# 02 — 房间、成员与最小商铺元数据

**What to build:** MeetMate 状态的最小件——一个房间、它的成员、以及聚餐搜索所需的商铺元数据——使得能创建并查看一个含两名冲突成员（A、D）的房间。表名严格对齐已冻结架构文档。

**Blocked by:** 01 — 基线修复与工程骨架。

**Status:** ready-for-agent

- [ ] `tb_meet_room` 建表（owner、status、created_at 等）—— 名称对齐文档（**非** `tb_meet_room_member`）
- [ ] `tb_meet_member` 建表（room_id、user_id、role 等）—— 对齐文档命名
- [ ] `com.hmdp.meet` 下 controller/service/mapper：创建房间、房间详情、添加成员
- [ ] 生产环境添加成员**不允许**任意添加其他用户；Demo 用 dev profile 测试接口或预置测试用户
- [ ] `tb_shop_meet_meta` 建表并播种最小聚餐商铺，遵守文档 UNKNOWN 策略（火锅/非火锅标签、辣度、坐标 x/y、人均价格、营业时间、数据来源）
- [ ] curl 可创建"周六聚餐"房间并加入成员 A、D，列表可见
- [ ] Controller + Service + Mapper 集成测试通过
