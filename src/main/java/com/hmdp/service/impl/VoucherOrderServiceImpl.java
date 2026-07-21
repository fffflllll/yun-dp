package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import com.rabbitmq.client.Return;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private CacheManager cacheManager;

    @Lazy
    @Resource
    private IVoucherOrderService self;

    /**
     * 脚本初始化
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    private static final String REQUEST_DEDUPE_CACHE = "seckill:request:dq";

    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 1. Redisson 锁 (防止同一个用户在MQ里有两条消息并发处理)
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            throw new RuntimeException("不允许重复下单");
        }
        try {
            self.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rollbackRedisVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();

        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = "seckill:order:" + voucherId;

        // 执行 Lua 脚本
        Long result = stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Arrays.asList(stockKey, orderKey),
                userId.toString()
        );

        if (result != null && result == 1) {
            log.info("Redis回滚成功 (库存+1, 移除用户). orderId: {}", voucherOrder.getId());
        } else {
            log.warn("Redis回滚跳过 (用户不在Set中，可能是重复回滚). orderId: {}", voucherOrder.getId());
        }
    }

    @Override
    public void handleFailure(VoucherOrder voucherOrder) {
        Long orderId = voucherOrder.getId();
        // 1. 【最终防线】查询 MySQL：这个订单到底成功了没？
        // 有可能 MQ 认为失败了（比如超时），但其实 DB 事务已经提交了。
        VoucherOrder dbOrder = getById(orderId);
        if (dbOrder != null) {
            //情况一：MySQL 里有数据
            log.warn("虽然进入死信队列，但MySQL已存在订单，判定为业务成功。无需回滚Redis！OrderId: {}", orderId);
            return;
        }
        //情况二：MySQL 里没数据
        // 说明重试了 N 次都失败了，或者数据库真的挂了。
        log.error("MySQL未生成订单，判定为最终失败。正在回滚Redis库存... OrderId: {}", orderId);

        // 2. 执行 Redis 回滚 (使用你已有的 Lua 脚本方法)
        this.rollbackRedisVoucherOrder(voucherOrder);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(RedisConstants.SECKILL_STOCK_KEY),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = 0;
        if (result != null) {
            r = result.intValue();
        }

        if(r!=0){
            //2.1.不为0，代表没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        // RabbitMQ
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        try {
            rabbitTemplate.convertAndSend("X","XA",order);
        } catch (Exception e) {
            log.error("发送 RabbitMQ 消息失败，订单ID: {}", orderId, e);
            this.rollbackRedisVoucherOrder(order);
            throw new RuntimeException("发送消息失败");
        }
        // 3. 返回订单号给前端（实际下单异步处理）
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀已经结束！");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService Proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return Proxy.createVoucherOrder(new VoucherOrder().setVoucherId(voucherId).setUserId(userId));
////        }
//
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(10);
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            IVoucherOrderService Proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return Proxy.createVoucherOrder(new VoucherOrder().setVoucherId(voucherId).setUserId(userId));
//        } finally {
//            lock.unlLock();
//        }
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long currentMsgId = voucherOrder.getId();
        // 1. 查数据库里有没有这个用户的单子
        VoucherOrder existingOrder = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .one();

        if (existingOrder != null) {
            // 情况 A：幂等性校验（不仅用户一样，连订单ID都一样）
            // 说明是 MQ 还没收到 ACK 导致的重复投递
            if (existingOrder.getId().equals(currentMsgId)) {
                log.info("重复消息，幂等处理，直接忽略。OrderId: {}", currentMsgId);
                return; // 【绝对不能抛异常，也不能回滚】
            }

            //情况 B：一人一单拦截（用户一样，但订单ID不一样）
            // 说明是 Redis 数据丢失导致的非法请求，或者是并发攻击
            log.warn("用户重复下单拦截，回滚库存。OrderId: {}", currentMsgId);
            // 手动回滚 Redis (因为这是不应该发生的扣减)
            this.rollbackRedisVoucherOrder(voucherOrder);
            return; // 【不要抛异常】，因为重试也没用，直接结束即可
        }

        //扣减库存
        boolean success = seckillVoucherService
                .update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("用户 {} 购买优惠券 {} 失败，库存不足", userId, voucherOrder.getVoucherId());
            return;
        }

        save(voucherOrder);
    }
}