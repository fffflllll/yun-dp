# 02 — 房间、成员与最小商铺元数据

**What to build:** MeetMate 状态的最小件——一个房间、它的成员、以及聚餐搜索所需的商铺元数据——使得能创建并查看一个含两名冲突成员（A、D）的房间。表名严格对齐已冻结架构文档。

**Blocked by:** 01 — 基线修复与工程骨架。

**Spec Status:** READY
**Execution Status:** BLOCKED_BY_01

- [ ] `tb_meet_room` 建表（owner、status、created_at 等）—— 名称对齐文档（**非** `tb_meet_room_member`）
- [ ] `tb_meet_member` 建表（room_id、user_id、role 等）—— 对齐文档命名
- [ ] `com.hmdp.meet` 下 controller/service/mapper：创建房间、房间详情、添加成员
- [ ] 生产环境添加成员**不允许**任意添加其他用户；Demo 用 dev profile 测试接口或预置测试用户
- [ ] `tb_shop_meet_meta` 建表，仅放**聚餐专属**元数据（基础坐标/价格**不在此表**）：
  - `shop_id`、`tags_json`（火锅/非火锅等标签）、`spicy_level`、`business_hours_json`、`allergen_tags_json`、`source`、`confidence`
  - 注：坐标 `x/y` 与 `avg_price` **属于 `tb_shop` 基础字段**，不在此表
- [ ] 种子数据同时写入 `tb_shop`（含 x/y/avg_price/type_id）+ `tb_shop_meet_meta`
- [ ] 至少准备 **5～8 家**商铺，其中 **3 家以上**通过澄清后的硬约束（保证 07 能稳定产出 2～3 个方案）
- [ ] curl 可创建"周六聚餐"房间并加入成员 A、D，列表可见
- [ ] Controller + Service + Mapper 集成测试通过
