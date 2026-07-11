# MeetMate MVP — Tracer-Bullet 工单索引

> 来源：`/to-tickets`，由 `grill-with-docs` 共识（见 `doc/CONTEXT.md` / `doc/GLOSSARY.md`）驱动。
> 原则：每条工单是穿透所有层的**最薄纵切**，可独立验证；阻塞边见各文件。
> 状态：全部 `ready-for-agent`。未配真实 tracker，本地落盘于 `.scratch/meetmate-mvp/issues/`。

## 依赖链

```
01 ─┬─→ 02 ─┬─→ 04 ─→ 05 ─→ 06 ─→ 07 ─┐
    │        │                            ├─→ 08 (最终验收依赖 07)
    └─→ 03 ─┘   (04 同时依赖 03)          │
                                           │
08 在 02 后即可启动（分阶段接入），但全链路验收卡在 07
```

## 工单清单（依赖顺序）

| # | 标题 | Blocked by |
|---|------|-----------|
| 01 | 基线修复与 MeetMate 工程骨架 | None |
| 02 | 房间、成员与最小商铺元数据 | 01 |
| 03 | Python Agent 骨架与偏好解析 Graph | 01 |
| 04 | Java 偏好采集纵切 | 02, 03 |
| 05 | AgentRun 启动与房间上下文 Tool | 04 |
| 06 | 冲突澄清、Interrupt 与可靠 Resume | 05 |
| 07 | 商铺候选、Proposal 生成与 Java 落库 | 06 |
| 08 | Vue3 极简演示页 + 全链路回归 | 02（最终依赖 07） |

## 贯穿约束（来自已冻结架构）

- Java 是业务事实来源；Python 不直连数据库。
- 候选必须由 Java 提供；Python 只返回 `candidate_id` + 解释。
- AgentRun 状态、Clarification 持久化、Interrupt/Resume 幂等 —— 不可砍。
- 澄清**仅**在双 HARD 冲突时触发（A 火锅 SOFT + D 不吃 HARD → 直接淘汰，不澄清）。
- 每张 Agent 工单验收分两档：Fake LLM 自动通过 + 真实 LLM 人工 smoke。
- 表名对齐文档：`tb_meet_room` / `tb_meet_member` / `tb_shop_meet_meta`。
- 前端用 `web/`（Vue3），不新增 Vue2 正式页面。

## 可砍清单（v2+）

复杂评分 / Ranking 分数展示 / 投票 / 重规划 / 多轮 Round / SSE 多实例 / Redis Stream / OpenTelemetry / 完整 Vue 产品页 / 优惠券 / Qdrant / 用户长期画像。
