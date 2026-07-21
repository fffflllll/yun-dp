# MeetMate 简历包装与面试拷打手册

> 适用方向：Java 后端、Java + AI 应用工程、实习/校招/1-3 年后端岗位  
> 代码基线：当前 `dp` 仓库，Spring Boot 4.1 + Spring AI 2.0 Java 单体版本  
> 更新日期：2026-07-16  
> 使用原则：简历负责突出价值，面试回答负责说明边界。可以包装设计取舍，不能把尚未实现的组件、指标和结果说成已经完成。

## 1. 这份手册怎么用

每个技术点都准备三层回答：

1. **简历层**：一句话说清场景、方案和收益，用来获得追问。
2. **原理层**：说明为什么这样做、保证范围是什么、失败时会怎样。
3. **生产化层**：当前学习版还缺什么，以及真实系统会如何补齐。

推荐面试表达模板：

> 当前学习版解决的是 X 场景下的 Y 问题，核心做法是 A。它能保证 B，但不能单独保证 C。生产化时我会补 D，并通过 E 指标或故障实验验证。

这套表达不会显得怂。相反，它证明你知道技术的适用边界，而不是只背“Redis + Lua + MQ”。

---

## 2. 可直接投递的简历版本

### 2.1 项目名称

**MeetMate｜内容驱动的本地体验与多人聚餐协作平台**

### 2.2 技术栈

**Java 17、Spring Boot 4.1、Spring AI 2.0、MyBatis-Plus、MySQL 8、Redis、Redisson、RabbitMQ、Vue、Nginx**

### 2.3 项目描述

面向本地体验内容发现、限量优惠预订与多人聚餐协作场景构建模块化单体系统。MySQL 保存用户、内容、订单及规划任务等业务事实，Redis 承担会话、热点数据、地理检索、社交 Feed 和秒杀资格预占，RabbitMQ 解耦异步落单与后台规划任务；模型输出必须经过 Java 业务规则校验后方可成为最终业务结果。

### 2.4 项目亮点

- **受控协商 Agent：** 基于 Spring AI 2.0 实现 Java 编排的有界 Plan-and-Execute 工作流，将偏好读取、候选召回、冲突识别、方案生成和结果校验拆分为有限阶段；模型仅调用房间上下文绑定的只读工具，候选先经 Java 硬约束过滤，最终方案再次由 Java 校验，并通过 RabbitMQ 异步执行、SSE 推送可解释过程事件。

- **异步秒杀链路：** 将下单请求拆分为 Redis 资格预检和 MySQL 异步落库两个阶段，使用 Lua 在 Redis 侧原子完成库存预扣与重复购买判断，通过 RabbitMQ 削峰保护数据库；消息层开启 correlated publisher confirm、returns 和 mandatory 路由检查，消费端使用 Redisson 按用户串行处理，并结合 SQL 条件扣库、本地重试、死信队列和 Lua 补偿处理异常订单。

- **热点缓存治理：** 封装通用 Redis `CacheClient`，针对不存在商铺使用短 TTL 空值缓存，热点数据采用逻辑过期、线程池异步重建和锁内双重检查，普通数据采用 Cache Aside，并在商铺更新后主动失效缓存，以降低缓存穿透和热点 Key 集中过期对数据库的冲击。

- **社交内容 Feed：** 使用 Redis Set 维护关注关系和共同关注，Redis ZSet 按点赞时间维护点赞用户顺序；博文发布时采用写扩散写入粉丝收件箱，并基于时间 score 与 offset 游标实现滚动分页，处理同一毫秒内多条内容造成的重复翻页问题。

### 2.5 一页简历的进取版措辞

如果简历空间很紧，可以使用下面四条。它们比“做了登录、商户和博客 CRUD”更容易获得有价值的追问，同时仍能在当前代码中找到对应实现。

- **Java AI Harness：** 在 Spring Boot 单体内构建包围大模型的确定性执行边界，通过请求级只读工具、调用预算、结构化输出、候选指纹和 Java Policy 二次校验限制模型权限；以 PlanRun/Attempt/Event 持久化异步执行状态，使模型失败、MQ 重投和 SSE 断线不改变业务事实归属。

- **限量权益预订链路：** 以 Redis Lua 作为高并发资格闸门，在单次脚本内完成库存预扣和重复购买判断；使用 RabbitMQ 解耦入口与订单事务，结合 Redisson 用户粒度互斥、MySQL 条件扣库、本地重试、死信队列和补偿脚本构建分层防线。

- **策略化缓存组件：** 将普通数据的 Cache Aside、空值缓存与热点数据的 stale-while-revalidate 抽象进通用 `CacheClient`，通过异步重建和锁内双重检查降低热点过期时的数据库放大，并将缓存更新竞态作为后续 generation fence 演进点。

- **社交关系读模型：** 使用 Set/ZSet 分别承载关注关系交集、点赞时序和粉丝 Feed，以写扩散换取读取稳定延迟，并通过 `maxTime + offset` 游标解决相同时间分值下的滚动分页重复问题。

这四条中的“分层防线”“策略化组件”“读模型”“确定性 Harness”都是对真实代码职责的抽象，不等于声称已经达到生产规模。

### 2.6 关于 460ms 优化到 135ms

只有具备压测证据时才追加下面这句话：

> 在【机器配置】、【并发数】并发、【总请求量】请求和【库存量】库存条件下，秒杀接口【平均/P95】响应时间由 460ms 降至 135ms，下降 70.7%。

必须记住：

- 平均响应时间不能在面试时说成 P95。
- 需要能说出压测工具、线程数、持续时间、库存量、成功率和错误率。
- 异步接口响应快不等于订单已经创建成功，接口返回的是“资格已受理”语义。
- 当前仓库没有压测脚本或报告，面试前应补一个可复现实验。

---

## 3. 90 秒项目介绍

> 这个项目最初来源于本地生活学习项目，我在此基础上把几个独立练习点串成了一个完整场景：用户先通过内容 Feed 发现商家和体验活动，再参与限量优惠预订，也可以创建多人聚餐房间，让系统收集成员偏好并生成可确认方案。  
> 后端是 Spring Boot 模块化单体，MySQL 是用户、内容、订单和规划状态的事实源；Redis 主要承担登录会话、GEO 检索、热点缓存、Feed 收件箱以及秒杀资格预占；RabbitMQ 用于把高峰请求和耗时规划任务异步化。  
> 我重点研究的不是简单调用这些中间件，而是三类问题：第一，Redis、MQ、MySQL 之间的一致性边界；第二，缓存击穿和更新并发下如何避免旧值回灌；第三，如何限制大模型只能处理语义，让 Java 掌握权限、硬约束和最终状态。当前学习版已经跑通核心链路，同时我也梳理了生产化所需的唯一约束、Outbox、对账、限流和故障注入方案。

### 面试官问“这不就是黑马点评吗？”

推荐回答：

> 基础商铺、秒杀和探店模块确实来自经典学习项目，我没有回避这个来源。我的增量主要有两部分：一是把各模块重新放进“内容发现到限量预订”的统一业务链，重点分析跨 Redis、MQ、MySQL 的失败恢复；二是增加多人聚餐协作流程，并把模型限制在只读工具和结构化输出范围内。面试时我更愿意讨论我改造的边界、发现的问题和验证方法，而不是把教程代码说成原创商业系统。

这个回答比强行否认来源更可信。

---

## 4. 总体架构与事实源

```text
Browser / Vue / Nginx
          |
          v
Spring MVC + Login/Refresh Interceptors
          |
          +-- Identity: MySQL User + Redis Session
          +-- Catalog: MySQL Shop + Redis Cache/GEO
          +-- Content: MySQL Blog/Follow + Redis Feed/Like view
          +-- Reservation: Redis Lua admission -> RabbitMQ -> MySQL Order
          +-- Planning: MySQL PlanRun/Event -> RabbitMQ Worker -> Spring AI
```

面试时一定要先说清楚事实源：

| 数据 | 当前主要事实源 | Redis/MQ 的定位 |
|---|---|---|
| 用户账号 | MySQL | Redis 保存可过期会话 |
| 商铺信息 | MySQL | Redis 是派生缓存和 GEO 索引 |
| 博客、关注关系 | MySQL | Redis 保存 Feed 和点赞顺序等读模型 |
| 秒杀资格 | 当前由 Redis 预占 | MQ 异步传递，MySQL 保存最终订单 |
| 规划任务与事件 | MySQL | MQ 执行任务，SSE 仅用于观察 |

标准回答：

> 我不会笼统地说 Redis 和 MySQL 双写一致，而是先区分事实源和派生数据。缓存、Feed 和 GEO 都可以从事实源重建；秒杀资格比较特殊，它在订单确认前属于短期预订状态，因此必须有订单号、状态和超时释放机制，不能只存一个用户 ID。

---

## 5. 秒杀模块拷打

### 5.1 代码地图

- 请求入口：`src/main/java/com/hmdp/controller/VoucherOrderController.java`
- 秒杀服务：`src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java`
- Redis 脚本：`src/main/resources/seckill.lua`
- 补偿脚本：`src/main/resources/rollback.lua`
- MQ 消费者：`src/main/java/com/hmdp/listener/SeckillVoucherListener.java`
- 队列配置：`src/main/java/com/hmdp/config/QueueConfig.java`
- 表结构：`src/main/resources/db/hmdp.sql`

### 5.2 当前调用链

```text
请求到达
  -> RedisIdWorker 生成 orderId
  -> Lua 检查库存和用户资格
  -> Redis 库存 -1，用户加入已购集合
  -> RabbitMQ 发布 VoucherOrder
  -> 消费者获取 user 维度 Redisson 锁
  -> MySQL 查询重复订单
  -> SQL stock = stock - 1 where stock > 0
  -> 插入订单
  -> 异常重试，最终进入死信队列并尝试 Redis 补偿
```

### 5.3 高频问题与口语回答

#### Q1：为什么用 Lua，而不是 Java 里先 GET 再 DECR？

> GET、判断和 DECR 是多条 Redis 命令，多个线程会在命令之间交叉执行，可能都看到库存大于零。Lua 在 Redis 事件循环中作为一次脚本执行，把库存判断、重复购买判断和预扣合在一个原子单元里，避免检查与修改之间的竞态。这里保证的是 Redis 侧原子性，不代表 Redis、MQ、MySQL 三者组成一个分布式事务。

#### Q2：Lua 能完全保证不超卖吗？

> 不能单靠 Lua 宣称系统永不超卖。Lua 能保证 Redis 预扣不为负，MySQL 还需要 `stock > 0` 的条件更新作为最终兜底。生产化还应给订单表增加 `UNIQUE(voucher_id, user_id)`，因为 Redis 数据丢失、旁路写入或消费竞态不能只靠应用代码防住。

#### Q3：为什么还要 MySQL 条件扣库存？Redis 不是已经扣了吗？

> Redis 是高并发入口的资格闸门，MySQL 才保存最终订单和持久库存。两边都扣是为了让数据库保持独立的不变量。SQL 使用 `UPDATE ... SET stock = stock - 1 WHERE stock > 0`，由受影响行数判断成功，避免数据库库存被扣成负数。

#### Q4：为什么使用 RabbitMQ？

> 秒杀入口只完成轻量资格预占，把相对慢的数据库事务转移给消费者，可以削峰并降低接口等待时间。代价是接口只能返回“已受理”，不能把 orderId 返回等同于订单已成功；还必须处理消息丢失、重复投递、积压和最终失败。

#### Q5：RabbitMQ 会不会丢消息？

> 当前配置已经打开 correlated publisher confirm、publisher returns 和 mandatory，但业务代码只是直接调用 `convertAndSend`，没有绑定 `CorrelationData` 回调、持久化 confirm 结果或处理 returned message。因此它能发现一部分 Broker 层问题，却不能关闭 Redis 预扣到消息发布之间的窗口。生产化可以让 Lua 同时写 Redis Stream 或待投递记录，由 Dispatcher 可重放地发布；也可以改为数据库事务 Outbox，但需要重新设计入口事实源。

#### Q6：发送方法抛异常时立即回滚 Redis 不就行了吗？

> 只对确定没有发送成功的错误安全。如果客户端超时但 Broker 实际已收到，立即补偿会多退库存，而消息稍后仍可能落库。所以补偿必须带 orderId 并根据预订状态做 CAS，不能只按 userId 删除资格。

#### Q7：RabbitMQ 为什么会重复消费？怎么处理？

> 消费者业务成功但 ACK 丢失、连接中断或 Broker 重新投递都会造成重复消息。应用必须幂等，不能把“MQ 一般只投一次”当保证。当前代码先按用户和券查询订单，生产化还要使用数据库唯一索引兜底，并把相同 orderId 的重复投递当成功处理。

#### Q8：为什么用了 Redisson 锁还需要唯一索引？

> 锁是并发协调手段，不是数据不变量。锁可能过期、Redis 可能故障、另一个服务可能绕过锁写库。唯一索引由数据库最终执行，才是“一人一单”的最后防线。锁主要减少冲突和重复查询，不能替代约束。

#### Q9：锁的粒度为什么是 userId？

> 当前目标是防同一用户的重复订单并发落库，所以按用户串行化，避免全局锁阻塞不同用户。更准确的 key 应包含 userId 和 voucherId，否则同一用户购买不同活动也会被无谓串行。锁竞争也不应直接当业务失败，需要区分稍后重试和重复下单。

#### Q10：死信队列是怎么工作的？

> 普通队列配置死信交换机和 routing key，消费多次失败后消息进入死信队列，死信消费者查询 MySQL 是否已有订单；已存在说明可能是 ACK 前故障，不补偿，确实不存在才释放 Redis 资格。当前死信消费者捕获补偿异常后没有继续抛出，会导致补偿失败的消息被 ACK，这一点生产化要改成 parking queue、人工重放或持久失败任务。

#### Q11：秒杀开始和结束时间在哪里校验？

> 当前异步实现没有把活动时间和券状态放进 Lua，旧的 Java 判断已被注释，因此直接调用接口可能提前或过期抢购。这属于当前代码缺口。生产化应在活动发布时把 begin/end/status 一起写入 Redis，Lua 使用服务端时间判断，并设置库存 key 的生命周期；MySQL 消费端也做二次校验。

#### Q12：Redis 重启后怎么办？

> 当前 Redis 是预订入口，库存和用户资格丢失会破坏一人一单。生产化需要持久化、启动重建和定时对账：根据 MySQL 活动库存与已确认订单重建 Redis，恢复期间暂停活动入口；已购关系仍由数据库唯一约束兜底。

#### Q13：Redis Cluster 下 Lua 有什么问题？

> Cluster 要求脚本访问的多个 key 位于同一个 hash slot。当前脚本根据前缀动态拼 key，不适合直接迁移。应从 Java 传入完整 `KEYS`，并用 `{voucherId}` hash tag，例如 `seckill:stock:{123}` 和 `seckill:order:{123}`，保证同槽执行。

#### Q14：接口为什么应该返回 202，而不是直接说抢购成功？

> 因为 Redis 通过只代表资格预占成功，数据库订单仍在异步创建。更准确的接口是返回 `202 Accepted + reservationId`，前端再查询 `RESERVED / CONFIRMED / RELEASED` 状态。当前项目直接返回 orderId，面试时要主动说明它的语义是受理号。

#### Q15：如何压测并证明设计有效？

> 我会固定库存和账号集，分别测试大量不同用户、同一用户重复请求和消费端停机三类场景。关注吞吐、P95/P99、Redis 脚本耗时、MQ 最大积压与清空速度，并在静默期后核对 Redis 预扣数、MySQL 库存和订单数。最终指标不只是响应快，还包括超卖数、重复订单数和故障恢复时间。

#### Q16：RedisIdWorker 为什么能生成分布式 ID？

> 当前实现把“当前秒相对项目起始时间的偏移”放到高位，把 Redis 按业务前缀和日期自增得到的序列放到低 32 位。不同日期使用不同计数器，不同业务前缀也隔离，因此多实例可以共享 Redis 生成不重复 ID。它依赖 Redis 可用、序列在一天内不超过 2^32，并没有处理时钟回拨；生产化会增加机器号/分片号、时钟保护和计数器生命周期。

#### Q17：为什么要注入 `self` 调用 `@Transactional` 方法？

> Spring 事务通过代理织入，同一个对象内部直接调用 `this.createVoucherOrder()` 会绕过代理，`@Transactional` 可能不生效。当前通过懒注入接口代理再调用，确保进入事务边界。更清晰的做法是把订单写入拆到独立的 Transactional Service，避免自注入让调用关系难以理解。

#### Q18：`RLock.tryLock()` 不传租约时间会怎样？

> Redisson 在成功加锁后默认使用 watchdog 续期，业务线程正常存活时不会因为固定短租约提前释放；`finally` 中 unlock 保证正常路径释放。当前 `tryLock()` 不等待，竞争失败会直接抛异常并进入消费重试，因此需要区分锁竞争、业务重复和数据库暂时不可用，不能全部当成永久失败。

#### Q19：自动 ACK、重试和死信的顺序是什么？

> 监听方法正常返回时容器 ACK；方法抛异常时先按配置做本地重试，当前最多三次；耗尽后由于 `default-requeue-rejected=false`，消息被拒绝并按照队列的死信交换机路由到 QD。死信监听器如果吞掉补偿异常又正常返回，Broker 会认为处理成功，所以补偿失败必须转入可重放的 parking queue 或持久失败表。

#### Q20：publisher confirm 和 consumer ACK 有什么区别？

> publisher confirm 说明消息是否被 Exchange 接受，publisher return 说明 mandatory 消息是否无法路由；它们都不代表消费者已经处理，更不代表 MySQL 事务已提交。consumer ACK 发生在消费方法成功返回之后，是消费侧的确认。三者必须分别记录和监控。

### 5.4 秒杀回答的边界

可以说：

- Lua 保证 Redis 侧资格判断和预扣原子执行。
- SQL 条件更新防止 MySQL 库存扣成负数。
- RabbitMQ 将入口和数据库事务解耦，实现削峰。
- Redisson 降低同一用户并发落库冲突。
- RabbitMQ 已开启 confirm/returns/mandatory 的 Broker 反馈基础能力。

当前不能说：

- Redis、RabbitMQ、MySQL 已经实现强一致分布式事务。
- 已经保证消息绝不丢失。
- 已经处理所有 NACK 和 returned message；当前业务还没有消费 confirm/return 结果。
- 已经实现数据库唯一约束和完整 Outbox。
- 已经通过十万 QPS、零超卖测试，除非补齐可复现报告。

---

## 6. 缓存模块拷打

### 6.1 代码地图

- 通用客户端：`src/main/java/com/hmdp/utils/CacheClient.java`
- 商铺服务：`src/main/java/com/hmdp/service/impl/ShopServiceImpl.java`
- 分类缓存：`src/main/java/com/hmdp/service/impl/ShopTypeServiceImpl.java`
- Redisson 配置：`src/main/java/com/hmdp/config/RedissonConfig.java`
- Caffeine 配置：`src/main/java/com/hmdp/config/CacheConfig.java`

### 6.2 当前策略

| 场景 | 当前方案 |
|---|---|
| 普通商铺 | Cache Aside + 空值缓存 |
| 热点商铺 | 逻辑过期 + 异步重建 + 双重检查 |
| 商铺更新 | 更新数据库后删除 Redis key |
| 商铺分类 | Redis List 缓存 |
| Caffeine | 已配置但业务未实际使用 |

### 6.3 高频问题与口语回答

#### Q1：Cache Aside 是什么？

> 读请求先查缓存，miss 再查数据库并回填；写请求先更新数据库，再让缓存失效。它简单且适合读多写少，但不是强一致方案，需要处理并发回填旧值、缓存故障和删除失败。

#### Q2：缓存穿透、击穿、雪崩分别是什么？

> 穿透是请求不存在的数据，每次都打数据库；击穿是单个热点 key 失效时大量请求同时回源；雪崩是大量 key 同时失效或 Redis 故障导致数据库流量激增。项目中空值缓存处理常见不存在 ID，逻辑过期与异步重建处理热点 key，但固定 TTL 和 Redis 故障降级还需要完善。

#### Q3：空值缓存有什么副作用？

> 会出现短时间的数据不存在假象，例如空值写入后数据库刚好新增该 ID。解决方式是空值使用较短 TTL，新增数据时主动删除对应空值，并监控 negative hit。它只适合防重复不存在 ID，随机 ID 攻击还需要参数校验、限流，必要时再使用布隆过滤器。

#### Q4：逻辑过期和普通 TTL 有什么区别？

> 普通 TTL 到期后 key 直接消失，请求必须同步回源；逻辑过期把 soft expire time 放在 value 里，过期后仍返回旧值，由一个线程异步刷新，用短期陈旧换取稳定延迟。生产化还要设置 hard TTL，限制最大陈旧时间并回收废弃 key。

#### Q5：为什么逻辑过期需要锁？

> 过期瞬间可能有很多请求，锁用于选出一个重建者，其余请求继续拿旧值。锁只保护重建过程，不能解决更新和重建之间的旧值覆盖，也不能用固定值无条件删除，否则租约过期后可能删掉另一个线程的新锁。

#### Q6：双重检查解决什么问题？

> 线程拿到锁时，可能前一个线程已经完成重建。如果不再检查，仍会重复访问数据库。拿锁后重新读取缓存并检查逻辑过期时间，可以减少无意义的回源。它优化并发，不是缓存一致性的完整方案。

#### Q7：当前缓存代码最大的确定性问题是什么？

> 热点 miss 时先按 `RedisData` 读取，降级回源后却向同一个 key 写裸 `Shop`；下一次请求又按 `RedisData` 解析，会出现格式冲突。修复方式是“一 key 一 schema”，统一 `CacheEnvelope`，热点与普通策略只改变过期参数，不能改变 value 格式；或者使用不同 key namespace。

#### Q8：为什么更新数据库后删除缓存仍可能读到旧值？

> 当前删除发生在数据库事务提交前。并发读可以在删除后、提交前读到旧数据库值并重新写入缓存，形成长期脏数据。应在事务 after-commit 后失效缓存，但异步重建任务仍可能拿着旧快照晚到写回，因此还需要 generation/version fence。

#### Q9：generation fence 怎么工作？

> 每个实体维护 generation。回源前记录当前 generation，准备回填时用 Lua 检查 generation 未变化才允许写入。更新事务提交后原子执行 `INCR generation + DEL payload`，这样任何更新前开始的慢查询或重建任务都不能在更新后覆盖新状态。

#### Q10：为什么 TTL 要加随机抖动？

> 批量预热时如果所有 key 使用相同 TTL，会在同一时间失效，形成流量尖峰。可以在基础 TTL 上增加一定比例随机值，例如 30 分钟加 0-5 分钟。当前代码使用固定 TTL，简历中不能说已经实现随机 TTL，除非补上代码和测试。

#### Q11：可以直接说项目用了 Caffeine L1 + Redis L2 吗？

> 目前不可以。仓库只创建了 Caffeine CacheManager，业务没有实际 get/put/evict，也没有 `@Cacheable`。真正做多级缓存后还要回答多实例失效：数据库更新后先失效 Redis，再通过 Pub/Sub 或版本校验让各实例失效 L1，并为丢广播准备短 TTL 兜底。

#### Q12：Redis 挂了应该直接查数据库吗？

> 不能无限制 fail-open，否则 Redis 故障会把全部流量瞬间压到数据库。非关键读可以在短超时后降级到数据库，但要通过 bulkhead、限流和短期本地 last-known-good 控制并发；秒杀资格等正确性关键链路应 fail-closed，避免绕过 Redis 预占。

#### Q13：Redisson 和 StringRedisTemplate 有什么区别？

> StringRedisTemplate 适合直接操作 Redis 数据结构和 Lua；Redisson 提供分布式对象、可重入锁和看门狗等更高级抽象。当前 Redisson 地址硬编码为 localhost，可能与 Spring Redis 配置连接不同实例，生产化应统一配置源、认证、database 和超时。

#### Q14：缓存序列化为什么要版本号？

> 如果直接缓存数据库实体，字段变更或旧节点写入可能导致新节点无法解析。缓存专用 DTO 加 `schemaVersion`、`state`、`generation` 和 epoch 时间，可以区分格式、兼容迁移并在损坏时主动驱逐，而不是让反序列化异常进入业务链路。

#### Q15：如何证明缓存确实有效？

> 不能只展示“有 Redis”。我会测 500 个并发请求同一个冷 key 时数据库实际查询次数，测试 100 个并发分类冷启动是否重复，使用锁和 latch 构造更新与重建交错，验证旧 generation 写入为零。线上指标至少包括 fresh/stale/negative hit、miss、DB amplification、重建耗时、队列深度、锁竞争和 stale age。

#### Q16：当前缓存重建锁为什么不够安全？

> `CacheClient` 给所有锁都写固定值 `1`，释放时直接 DEL。若重建超过 10 秒，旧锁过期后另一个实例拿到新锁，旧任务结束时可能把新锁删除。正确做法是写唯一 owner token，通过 Lua 比较 owner 后删除，或者统一使用 Redisson；获取和释放还应位于同一个执行线程。

#### Q17：线程池满了会发生什么？

> 当前重建池队列容量 100，使用 AbortPolicy。`submit` 被拒绝发生在异步任务的 `finally` 之外，请求会抛出 RejectedExecutionException，锁只能等 TTL 过期；任务内部异常抛给无人读取的 Future，也缺少日志。生产化应使用 Spring 管理的 Executor，监控队列和拒绝数，并在拒绝时立即安全释放锁、继续返回旧值或执行受控降级。

### 6.4 面试包装策略

当前简历可以说“Redis 热点缓存治理”，不要说“Caffeine L1 + Redis L2”。

完成以下内容后，才升级为多级缓存表述：

1. 统一 CacheEnvelope，修复同 key 双格式。
2. 接入真实 Caffeine 读写。
3. 增加 TTL 抖动和 hard TTL。
4. 实现多实例 L1 失效或版本校验。
5. 加入 after-commit 失效和 generation fence。
6. 补并发测试与命中率、回源次数报告。

---

## 7. 登录与安全模块拷打

### 7.1 代码地图

- 用户服务：`src/main/java/com/hmdp/service/impl/UserServiceImpl.java`
- 身份恢复：`src/main/java/com/hmdp/interceptor/RefreshTokenInterceptor.java`
- 登录校验：`src/main/java/com/hmdp/interceptor/LoginInterceptor.java`
- 路由配置：`src/main/java/com/hmdp/config/MvcConfig.java`
- 用户上下文：`src/main/java/com/hmdp/utils/UserHolder.java`
- 前端 token：`frontend/js/common.js`

### 7.2 当前流程

```text
手机号 -> 生成验证码 -> Redis 保存 2 分钟
验证码通过 -> 查询/创建用户 -> 生成 opaque token
token -> Redis Hash<UserDTO> + TTL
请求 -> RefreshTokenInterceptor 恢复身份和续期
     -> LoginInterceptor 判断受保护资源
     -> ThreadLocal 保存当前用户
     -> afterCompletion 清理
```

### 7.3 高频问题与口语回答

#### Q1：为什么用 Redis Session，而不是 JWT？

> 这个项目需要主动登出、会话续期和即时失效，opaque token + Redis 查询更简单。JWT 的优势是服务端少查状态，但撤销、权限变化和设备管理通常还要额外黑名单或版本号。选型不是 JWT 一定更高级，而是看是否需要服务端控制会话。

#### Q2：为什么 Redis 用 Hash 保存 UserDTO？

> Hash 可以只存登录态需要的轻量字段，避免把手机号、密码等完整 User 实体放进会话；也方便查看和局部更新。当前 UserDTO 只有 id、昵称和头像，降低敏感信息暴露面。

#### Q3：两个拦截器为什么要分开？

> 身份恢复拦截器对所有请求执行，有 token 就尝试恢复用户并刷新 TTL；登录拦截器只处理受保护接口，检查 ThreadLocal 是否存在用户。分开后公开接口也能识别可选登录用户，例如博客列表可以返回当前用户是否点赞。

#### Q4：ThreadLocal 为什么必须清理？

> Servlet 线程池会复用线程，如果请求结束不 remove，下一个请求可能读到上一位用户。当前在 `afterCompletion` 清理，但更稳妥的做法是在最外层 Filter 的 `try/finally` 中处理，并在请求开始先 remove，覆盖 preHandle 中途异常的路径。

#### Q5：当前验证码是真短信吗？

> 不是。当前学习版生成验证码后写 Redis，并在日志中输出，主要用于跑通流程。简历应写“手机号验证码登录”，不能说已接入短信平台。生产化需要短信 Provider、开发环境 fake provider，并且日志绝不能记录验证码。

#### Q6：验证码如何防暴力破解和短信轰炸？

> 发送端按手机号、IP、设备设置分钟和日级配额，加发送冷却；验证端限制错误次数并短时锁定。验证码要绑定 purpose 和 challengeId，成功后通过 `GETDEL` 或 Lua 原子消费，避免一个验证码重复换取多个 token。

#### Q7：滑动过期有什么风险？

> 只设置滑动 TTL 时，被盗 token 只要持续使用就可以一直续期。应同时设置 idle timeout 和 absolute expiry，例如空闲 30 分钟、绝对 7 天；刷新也不用每次请求都写 Redis，可以仅在剩余 TTL 小于阈值时续期。

#### Q8：如何支持退出全部设备？

> 除 `token -> session` 外维护 `userId -> sessionIds` 索引，记录设备、创建时间、最近访问和绝对到期时间。单端退出删除一个 session，全端退出遍历或提升 user sessionVersion，让旧会话全部失效。

#### Q9：token 放 sessionStorage 有什么风险？

> JavaScript 可读，发生 XSS 时 token 会被窃取。当前博客使用 `v-html`，风险会叠加。保持同源 Web 架构时可以改成 HttpOnly、Secure、SameSite Cookie；切换 Cookie 后要补 CSRF 防护。若仍用 Authorization header，则必须先清理 XSS、缩短 token 生命周期并避免日志泄漏。

#### Q10：当前接口权限有什么问题？

> `MvcConfig` 仍按整个 `/shop/**`、`/voucher/**`、`/upload/**` 前缀放行，路由层没有按 HTTP method 做精确白名单。商铺写接口目前额外有管理员 ID 校验，这是一次补强；但优惠券写接口和上传/删除接口仍不能只依赖这种手写控制器判断。生产化应默认拒绝，只公开查询接口，并增加 USER、MERCHANT、OPERATOR/ADMIN 的方法级授权和服务层校验。

#### Q11：密码登录为什么不能作为亮点？

> 前端存在密码登录页面，但后端统一登录逻辑忽略 password，现有 PasswordEncoder 又是单次 salted MD5。未完整实现设置密码、找回、改密和锁定流程前应删除入口；真实密码存储使用 Argon2id 或 BCrypt，并限制失败次数。

#### Q12：Cookie 会不会有 CSRF？Authorization header 呢？

> 浏览器会自动携带 Cookie，因此 Cookie 会话需要 SameSite 和 CSRF token；Authorization header 通常由前端代码主动添加，不容易被传统跨站表单自动带上，但更怕 XSS。当前同源 Nginx 代理不需要随意开放 `Access-Control-Allow-Origin: *`。

#### Q13：如何避免注册并发？

> 两个相同手机号请求可能都查不到用户并尝试插入。数据库手机号唯一索引是最终防线，应用捕获唯一冲突后重新查询用户，实现 insert-or-select，而不是把查询结果当并发保证。

#### Q14：用户资料为什么要拆 DTO？

> 公开主页只需要昵称、头像和简介；本人账户页面可以看到生日、积分等；后台才需要更多字段。直接返回 UserInfo 实体容易把敏感字段暴露给任意登录用户，应拆 PublicProfileDTO、SelfProfileDTO 和 AdminProfileDTO。

#### Q15：登录模块要测什么？

> 至少覆盖验证码过期、错误次数、一次性消费、并发注册、匿名访问矩阵、token 续期阈值、绝对过期、单端和全端登出、Redis 异常、ThreadLocal 异常清理、XSS 后 token 是否可读以及上传越权。

---

## 8. 博客与 Feed 模块拷打

### 8.1 代码地图

- 博客服务：`src/main/java/com/hmdp/service/impl/BlogServiceImpl.java`
- 关注服务：`src/main/java/com/hmdp/service/impl/FollowServiceImpl.java`
- 评论控制器：`src/main/java/com/hmdp/controller/BlogCommentsController.java`
- 博客页面：`frontend/blog-detail.html`
- 数据表：`src/main/resources/db/hmdp.sql`

### 8.2 高频问题与口语回答

#### Q1：为什么 Feed 用 ZSet？

> Feed 需要按时间倒序拉取，ZSet 的 member 存 blogId，score 存发布时间，可以按 score 范围查询并携带分数。相比 List，它更适合按时间游标读取，也能对重复 blogId 去重。

#### Q2：为什么不用普通 page/size 分页？

> Feed 持续有新内容插入，offset 深分页会越来越慢，还会因为前面插入新数据导致重复或漏读。项目用上一次最小时间作为 max score，并携带同一 score 已消费数量 offset，实现滚动分页。

#### Q3：为什么既要 maxTime 又要 offset？

> 多条内容可能在同一毫秒写入，只有 maxTime 时下一页使用包含边界会重复，排除边界又会漏数据。offset 表示边界时间已经消费了多少条，下次仍从同一 score 查询但跳过这些 member。

#### Q4：项目是推模式还是拉模式？

> 当前是 fanout-on-write：作者发布博客时查询所有粉丝，为每位粉丝的 ZSet 收件箱写入 blogId。优点是读快，缺点是大 V 发布写放大、同步发布延迟和部分失败。生产化可通过 Outbox 异步投递，并对大 V 使用推拉结合。

#### Q5：如何保证 Feed 不丢、不重？

> 当前数据库保存博客后同步写多个 Redis key，中途失败会部分投递，不能严格保证。生产化在保存博客的同一事务写 Outbox，消费者以 eventId 幂等，Feed member 使用 blogId 天然去重，并监控 outbox lag 和失败重试。

#### Q6：点赞为什么用 ZSet，不用 Set？

> 不只要判断是否点赞，还要按点赞时间展示前几位用户，所以用 userId 作为 member、时间作为 score。若只关心是否点赞，Set 会更简单。

#### Q7：当前点赞并发有什么问题？

> 当前先读 Redis 是否存在，再更新 MySQL count，最后修改 ZSet。两个并发点赞都可能读到不存在，数据库加两次而 ZSet 只有一个 member。生产化应把 `blog_like(blog_id,user_id)` 作为事实表并加唯一索引，Redis 作为派生读模型。

#### Q8：为什么 toggle 点赞接口不够好？

> 网络重试时 toggle 会把第二次请求变成取消点赞，不是幂等操作。更合理的是 `PUT /blogs/{id}/like` 表示期望已点赞，`DELETE` 表示期望未点赞，通过唯一约束和受影响行数实现幂等。

#### Q9：博客热榜只按 liked 排序有什么问题？

> 老内容会长期占榜，并发点赞时 offset 分页也可能漂移。可以使用带时间衰减的热度分数，例如点赞、评论、收藏按权重组合并随发布时间衰减，定期生成排行榜读模型；简历没有实现时不要说成推荐算法。

#### Q10：博客列表为什么有 N+1？

> 每条博客分别查询作者，再分别访问 Redis 判断当前用户是否点赞。一页 N 条会产生 N 次数据库查询和 N 次 Redis 操作。可以批量查询作者并用 Map 组装，点赞状态使用 pipeline 或批量命令。

#### Q11：关注关系为什么需要唯一索引？

> 当前表只有主键，并发关注可能插入重复行，Redis Set 会掩盖数据库重复。应增加 `UNIQUE(user_id, follow_user_id)`，禁止自关注，并为粉丝查询增加 `(follow_user_id, user_id)` 索引。

#### Q12：评论模块完成了吗？

> 数据表和实体存在，但控制器与服务没有业务实现，前端评论是静态展示。面试中不能说已经完成评论系统。要么从简历删除评论，要么补发布、回复、分页、删除权限、计数一致性和内容审核。

#### Q13：为什么博客有存储型 XSS 风险？

> 后端原样保存正文，前端用 `v-html` 渲染，攻击者可以提交带事件处理器的 HTML；token 又在 sessionStorage，形成会话盗取链。默认应按纯文本渲染，确需富文本则后端白名单清洗、前端使用成熟 sanitizer，并配置 CSP。

#### Q14：图片为什么不能直接信任扩展名？

> 当前上传只取原文件名后缀，未检查魔数、MIME、大小和像素。攻击者可以上传主动内容或耗尽磁盘。生产化应解码并重新编码图片、限制尺寸和配额，记录 owner，用资源 ID 删除并校验路径归属。

#### Q15：如何量化 Feed？

> 测发布接口 P95、每秒 fanout 条数、Outbox 到收件箱的端到端延迟、积压峰值、重复率和丢失率；分别测试普通作者和 1 万/10 万粉丝作者。读取侧关注 P95、空页率、重复内容率和 Redis 操作次数。

---

## 9. Agent 模块拷打

### 9.1 简历表述必须修正

当前项目已经从 LangGraph 改为 Spring AI 2.0 Java 单体，不能再写：

- Python/LangGraph 服务。
- LangGraph interrupt/checkpointer。
- 群组投票、否决轮次和投票驱动重规划。

当前真实能力是：

- Java 创建有限计划步骤。
- Spring AI 执行受控 Tool Calling。
- 工具只读并绑定当前 room/run/attempt。
- Java 负责候选硬约束过滤和最终方案复核。
- 候选不足时创建一次定向澄清，回答后重新发起规划。
- RabbitMQ 后台执行，SSE 推送持久化事件。

### 9.2 代码地图

- 编排：`src/main/java/com/hmdp/service/MeetPlanningAgent.java`
- 模型调用：`src/main/java/com/hmdp/service/MeetPlanningAiService.java`
- 工具：`src/main/java/com/hmdp/service/MeetPlanningTools.java`
- 规则：`src/main/java/com/hmdp/service/MeetPlanPolicyService.java`
- 执行：`src/main/java/com/hmdp/service/MeetPlanExecutionService.java`
- 状态与 SSE：`src/main/java/com/hmdp/service/impl/MeetPlanServiceImpl.java`
- 测试：`src/test/java/com/hmdp/MeetMateAiUnitTest.java`

### 9.3 高频问题与口语回答

#### Q1：为什么叫 Plan-and-Execute？

> Java 先定义有限步骤：读取已确认偏好、召回可行餐厅、检查候选详情、生成并校验方案，再逐步执行。它不是让模型自由规划无限任务，因此更准确地说是有界 Plan-and-Execute。

#### Q2：哪里体现 ReAct？

> 在方案生成阶段，模型根据工具 Observation 决定下一次调用读取偏好、召回候选、检查详情或提交校验。但工具集合、最大调用次数和最终目标都由 Java 限定，所以是受控 ReAct，而不是开放式自治 Agent。

#### Q3：为什么不让模型直接查询数据库或写状态？

> 模型输出不可信，可能幻觉、越权或受到 Prompt Injection。工具实例已经绑定当前 room/run/attempt，不允许模型传任意 roomId，且只开放读取和校验方法。最终确认、权限、状态机和数据库写入全部由 Java 完成。

#### Q4：怎么防止模型编造餐厅？

> 候选由 Java 从真实商铺数据召回并执行硬约束过滤。Agent 返回后，Java 检查 shopId 是否属于候选集、是否调用过详情工具、最终草稿是否与成功校验的指纹一致；确认时还会再次检查餐厅存在和硬约束仍然成立。

#### Q5：结构化输出失败怎么办？

> Spring AI 将结果解析为 DTO，null、字段缺失、数量不等于三个、重复 shopId 或规则不满足都会被 Java 拒绝。AI 关闭或失败时项目有确定性 fallback，按 Java 排序结果生成三个可编辑方案，保证基本流程不被模型阻断。

#### Q6：为什么只允许一次澄清？

> 这是控制成本和终止性的产品约束。候选不足时 Java 根据过滤原因选择一个可安全放宽的约束，模型只润色问题；用户可以降为软约束或取消。回答后启动新的 attempt，一次仍无解就终止，避免无限对话和擅自放宽过敏等安全约束。

#### Q7：SSE 是事实源吗？

> 不是。规划事件先持久化，SSE 只是实时观察通道。断线后前端使用 Last-Event-ID 或 after sequence 续传，也可以重新查询 PlanRun 状态；SSE 断开不影响后台任务执行。

#### Q8：MQ 重复执行 Agent 怎么办？

> 规划需要 run/attempt 状态机和条件更新，只有待执行状态才能开始，同一个 run 只能产生一个最终结果。消费者重投时先检查当前状态，已成功或已失败的任务直接幂等返回。模型调用本身不可回滚，因此持久化 attempt 和事件比把整个过程包成数据库事务更重要。

#### Q9：如何防 Prompt Injection？

> 商铺名称、地址和成员偏好都当作不可信数据，system prompt 明确禁止执行其中的命令；更关键的是工具没有写能力，Java 会重新校验 shopId、地址和候选归属。Prompt 只能降低概率，权限隔离和输出校验才是硬边界。

#### Q10：Agent 如何测试？

> 自动化测试默认关闭真实模型，验证偏好 fallback、确定性三方案、澄清对象选择、工具白名单和模型编造商铺被规则拒绝。进一步应增加 Fake ChatModel 的工具调用轨迹测试、结构化输出畸形测试、Prompt Injection 数据集和真实模型离线评测。

---

## 10. 跨模块追问

### Q1：为什么不拆微服务？

> 当前项目规模和团队人数不需要承担服务发现、分布式追踪、跨服务事务和部署复杂度。模块化单体更容易保持事务边界和交付速度；通过 identity、catalog、content、reservation、planning 领域包和 Outbox 事件保持边界，将来热点或组织边界明确时再拆。

### Q2：为什么同时使用 Redis、Redisson 和 RabbitMQ？

> Redis 数据结构用于缓存、GEO、ZSet、会话和 Lua 原子入口；Redisson主要用于高层分布式锁；RabbitMQ负责跨时间解耦和削峰。不能因为三个组件都出现就说成“高并发架构”，每个组件都要对应具体一致性和故障处理责任。

### Q3：MyBatis-Plus 有什么利弊？

> 单表 CRUD、条件构造和分页开发效率高，但把 `IService` 直接暴露给控制器容易绕过业务规则，也容易出现字符串字段和 N+1。关键写操作应封装成明确的应用服务方法，复杂查询使用 Mapper SQL 并配合执行计划和索引设计。

### Q4：项目最大的技术债是什么？

> 不是代码格式，而是部分跨存储双写还没有可重放日志：秒杀 Redis 到 MQ、博客到粉丝 Feed、数据库更新到缓存失效。其次是登录权限白名单和上传安全。我的改造优先级是先封安全边界，再加数据库约束，最后补 Outbox、对账和指标。

### Q5：如何做可观测性？

> 业务指标比 JVM 指标更重要。秒杀看预占成功、重复拒绝、MQ lag、订单确认与释放；缓存看不同 hit 类型、DB amplification 和 stale age；Feed 看 outbox lag、fanout 速度和重复率；Agent 看 run 成功率、工具调用次数、结构化解析失败和规则拒绝原因。日志中使用 orderId/runId 关联，但绝不记录 token、验证码和原始敏感偏好。

### Q6：如何设计测试金字塔？

> 纯业务规则用单元测试；Redis Lua、MySQL 约束、RabbitMQ 重投使用 Testcontainers 集成测试；关键流程用少量 API 测试；高并发和故障恢复使用独立压测/混沌脚本。测试必须包含断言，不能把灌缓存和批量生成 token 的脚本当自动化测试。

### Q7：为什么不用 Kafka？

> 当前主要是订单命令和后台任务，RabbitMQ 的路由、重试、死信和低运维门槛足够。Kafka 更适合高吞吐事件流、长时间保留和多消费者回放。是否切换取决于事件规模和回放需求，不是为了增加技术栈关键词。

### Q8：为什么不用 Elasticsearch？

> 当前商铺规模和搜索需求简单，MySQL 条件查询加 Redis GEO 足够。只有全文检索、复杂相关性排序、聚合和数据规模达到瓶颈时才引入 ES，并同时承担索引同步和一致性成本。

### Q9：MySQL 默认可重复读能解决一人一单吗？

> 不能把隔离级别当唯一性约束。两个事务都可能在各自快照中查到“没有订单”，随后分别插入。`UNIQUE(voucher_id,user_id)` 才能让数据库拒绝第二次插入；应用捕获 DuplicateKey 后按幂等成功或重复购买处理。可重复读主要解决一致性读和部分幻读语义，不替代业务唯一索引。

### Q10：项目应该补哪些索引？

> 秒杀订单补 `(voucher_id,user_id)` 唯一索引；关注补 `(user_id,follow_user_id)` 唯一索引和 `(follow_user_id,user_id)` 粉丝查询索引；博客按用户列表补 `(user_id,create_time,id)`；规划 Attempt 已有 `(dispatch_status,next_dispatch_at)` 支持 Dispatcher 扫描。索引设计要结合查询条件、排序和基数，用 EXPLAIN 验证而不是只背最左前缀。

---

## 11. 面试表达技巧

### 11.1 不要从技术名词开始

较差回答：

> 我用了 Redis、Lua、Redisson、RabbitMQ，解决高并发。

推荐回答：

> 秒杀高峰真正的问题是不能让所有请求直接争用 MySQL，同时要区分资格预占和最终订单。我先用 Lua 原子预占，再用 MQ 削峰，数据库通过条件更新和唯一约束兜底；跨存储失败则需要可重放投递和对账。

### 11.2 被指出缺陷时怎么答

不要硬顶。使用四步结构：

1. 承认保证范围。
2. 解释当前版本为什么这样实现。
3. 指出具体失败窗口。
4. 给出可落地改造和验证方式。

示例：

> 对，publisher confirm 不能解决 Redis 和 MQ 的原子性。当前学习版只能捕获明确发送异常并补偿，仍存在发送结果不确定窗口。生产化我会让 Lua 同时写待投递记录，由 Dispatcher 重放，并让补偿按 orderId CAS；再通过三个宕机点的故障实验验证最终收敛。

这类回答通常比假装“已经完全解决”更加分。

### 11.3 三类危险词

- **保证**：要明确是在 Redis、数据库还是整个系统范围内保证。
- **最终一致**：要说清谁负责重试、状态存在哪里、多久收敛、如何对账。
- **高并发**：必须提供并发量、吞吐、P95/P99、错误率和环境。

---

## 12. 面试前补强清单

### P0：不补容易现场翻车

- 收紧 `/voucher/**`、`/upload/**` 匿名写权限，并把商铺管理员校验移到统一方法级授权；路由白名单只放行明确的查询接口。
- 下线或修复上传路径穿越删除。
- 移除博客 `v-html` 或完成严格清洗，避免 token 被 XSS 窃取。
- 修复热点缓存同 key 双格式。
- 给订单表增加 `UNIQUE(voucher_id, user_id)`。
- 给关注表增加 `UNIQUE(user_id, follow_user_id)`。
- 停止日志输出验证码和 token。

### P1：决定简历能否写“可靠性”

- 秒杀资格记录保存 orderId 和状态，补偿按 orderId CAS。
- 将已开启的 publisher confirm/return/mandatory 接入 `CorrelationData` 回调和发送状态表，再增加可重放待投递记录与对账任务。
- 增加订单状态查询接口。
- 缓存增加统一 Envelope、TTL 抖动、hard TTL 和 generation fence。
- 博客发布增加 Outbox，Feed 消费幂等。
- OTP 增加限频、错误次数和原子消费。

### P2：形成面试证据

- 增加 Testcontainers 集成测试。
- 增加 k6/JMeter/Gatling 压测脚本并提交报告。
- 增加故障注入脚本：预扣后、发布后、DB 提交后 ACK 前杀进程。
- 增加缓存并发时序测试和数据库回源计数。
- 增加架构图、秒杀时序图、缓存更新时序图。
- README 给出一条命令启动完整环境和演示账号。

---

## 13. 指标记录模板

| 模块 | 场景 | 必填指标 | 实测值 |
|---|---|---|---|
| 秒杀 | 10 万请求争抢 1000 库存 | TPS、P95、P99、超卖、重复单、MQ lag | 待填写 |
| 秒杀 | 消费者停机 30 秒后恢复 | 最大积压、清空时间、最终差额 | 待填写 |
| 缓存 | 500 并发同 key 冷启动 | DB 回源次数、P95、锁竞争 | 待填写 |
| 缓存 | 更新与异步重建竞态 1 万次 | 旧 generation 写入成功数 | 待填写 |
| Feed | 1 万粉丝作者发布 | 发布 P95、fanout 时间、重复/丢失 | 待填写 |
| 登录 | OTP 暴力尝试和重复消费 | 429 比例、锁定次数、重复成功数 | 待填写 |
| Agent | 固定评测集 | 成功率、规则拒绝率、幻觉候选数、平均工具次数 | 待填写 |

不要在填写前把“待填写”替换成估算值。

---

## 14. 最终背诵提纲

面试前至少做到脱稿回答下面十句话：

1. Lua 只保证 Redis 脚本内部原子性，不保证 Redis、MQ、MySQL 原子提交。
2. Redisson 锁用于协调并发，数据库唯一索引才是不变量兜底。
3. MQ 异步接口返回的是受理状态，不是订单已经成功。
4. publisher confirm 不能独自解决跨存储双写。
5. 逻辑过期用可控陈旧换稳定延迟，必须有 hard TTL 和最大陈旧边界。
6. 更新后删除缓存仍可能旧值回灌，需要 after-commit 和 generation fence。
7. Feed 的 maxTime + offset 是为了解决相同 score 的重复与遗漏。
8. opaque session 与 JWT 没有绝对高下，选择取决于撤销和状态控制需求。
9. LLM 只负责语义，权限、硬约束和最终写入必须由确定性代码掌握。
10. 所有“高并发、最终一致、零超卖”都必须有指标、失败窗口和验证方法。
