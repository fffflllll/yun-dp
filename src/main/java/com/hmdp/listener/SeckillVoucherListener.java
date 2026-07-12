package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class SeckillVoucherListener {

    @Resource
    SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(queues = "QA")
    public void receivedA(VoucherOrder voucherOrder) {
        log.info("MQ接收到下单消息: userId={}, orderId={}", voucherOrder.getUserId(), voucherOrder.getId());
        try {
            // 调用业务层下单
            voucherOrderService.handleVoucherOrder(voucherOrder);
            log.info("下单成功，订单ID: {}", voucherOrder.getId());
        } catch (Exception e) {
            log.error("下单处理异常，准备重试。订单ID: {}", voucherOrder.getId(), e);
            // 【关键】抛出异常，让Spring Retry机制捕获并重试
            // 如果不抛出异常，MQ会认为消费成功，就不会触发重试和死信了
            throw new RuntimeException("数据库下单失败");
        }
    }

    @RabbitListener(queues = "QD")
    public void receivedD(VoucherOrder voucherOrder) {
        log.error("【严重警告】订单进入死信队列，说明重试多次失败。开始回滚Redis。订单ID: {}", voucherOrder.getId());
        try {
            // 调用业务层的回滚方法
            voucherOrderService.handleFailure(voucherOrder);
            log.info("Redis回滚成功，用户可重新下单。");
        } catch (Exception e) {
            log.error("Redis回滚失败，需人工介入！", e);
            // 此时可以发邮件/钉钉报警给运维
        }
    }

}
