# MeetMate — 项目意图与上下文（CONTEXT）

> 本文档由 `grill-with-docs` 会话逐步沉淀，记录"我们到底为什么做、做成什么样"的共识。
> 与 `ADR-001~004`（技术冻结决策）互为补充：ADR 记"怎么切"，本文记"为什么切 / 交付什么"。
> 状态：共识已达成，工单已发布至 `.scratch/meetmate-mvp/issues/`（见 `INDEX.md`），待 `/implement` 沿依赖执行。

---

## 决策树（grilling 记录）

### D1 — 根节点：MeetMate 的北极星（2026-07-12）
- **结论**：A — 学习 / 作品级 Demo。
- **含义**：第一优先级不是"真能上线的餐厅产品"，而是**证明"业务系统守门、AI 只做语义"这套分层能落地、可测、可替换**。
- **v1 成功标准（初拟）**：端到端跑通 + 关键并发 / 澄清链路有自动化测试；而非真实用户订位量。
- 候选：B=真能用小产品 / C=单纯练手 LangGraph / D=其他。已排除 B、C。

### D2 — 承重概念："可替换"指什么（2026-07-12，已被 D3 覆盖/降级）
- 初答 A1：整个 Python Agent 服务可替换，Java 契约是稳定边界。
- **已被 D3 推翻**：用户明确"可替换没用，就是想给简历加亮点"。故"可替换"不再是项目目标，无需为它做证明（如第二个 stub 实现）。

### D3 — 真正的根节点：简历亮点（2026-07-12，推翻 D1/D2）
- **结论**：用户原话"我就想给简历加点亮点"。"学习/作品级 Demo"只是外壳，**真实动机 = 一个能写进简历、显得厉害的 AI Agent 作品**。
- **后果**：
  - 砍掉"做第二个实现去证明可替换"（B1 否决）。
  - 但**契约 / 状态机 / 幂等 / 评测 / 可观测性这套重机器保留**——它不是为了证明可替换，而是简历上的"工程成熟度"硬证据。
  - 衡量标准从"架构哲学是否被证明"变成"招聘方看了觉不觉得厉害、能不能快速看懂价值"。
- **开放子问题（真正要 grill 的）**：
  - 简历面向什么角色？（后端 / AI 工程师 / 全栈）——决定强调点。
  - 必须真能跑 / 有 live demo，还是"架构漂亮 + 部分实现"就够？
  - 最该让人记住的"wow"是哪个：LangGraph 多节点编排？群体冲突 + HITL 澄清？系统-of-record 与 AI 分层？
  - MVP 最薄但仍有亮点的纵切在哪？

---

### D4 — 简历面向角色（2026-07-12）
- **结论**：E2 — AI / Agent 工程师向。
- **含义**：简历主场 = LangGraph 多节点编排 + HITL 澄清挂起/恢复 + 语义冲突解释 + 评测体系。Java 系统-of-record 作为"我懂怎么把不可信 AI 安全嵌进严肃业务"的佐证。
- 排除：E1(后端主场) / E3(全栈) / E4(海投，已归入 E2 打法)。

---

### D5 — 是否真跑通 / 演示形态（2026-07-12）
- **结论**：F2 — 必须接**真实 LLM** 端到端跑，演示即真实效果。
- **风险已告知**：抽风 / 烧钱 / 超时 / 录屏翻车风险高；用户已知并选择接受。
- 含义：MVP 硬指标 = 真实 LLM 下端到端跑通一次；自动化测试仍可用 Fake LLM（见 Python 文档 §26.2），但"演示产物"用真模型。

---

### D6 — 最该突出的"wow"元素（2026-07-12）
- **结论**：G2 — AI 知道"什么时候该问人"：群体偏好冲突 + 信息缺失时，Agent 主动挂起、请 Java 落 clarification、等人回答后 `resume` 接着跑。带人类在环的状态机。
- **定位**：LangGraph 多节点状态机（G1）是其底层机制，藏后面当技术支撑，不单独当门面；分层哲学（G3）作佐证。
- 简历落点：摘要第一句 / Demo 第一帧 = "一个会主动问人的多人在环 Agent"。

---

### D7 — MVP 最薄纵切（2026-07-12）
- **结论**：H1 — 1 房间 + 2 冲突成员(A 火锅/D 不吃) + 1 次真 LLM 规划 + 1 次澄清挂起/恢复 + 出方案。
- **砍掉（推 v2）**：投票、重规划、多轮、完整评测集；仅留 Fake-LLM 单测保底。
- **前端**：不建设完整产品前端，但建立**最小 Vue3 Demo Shell**（`web/`，单页演示）；不做通用组件库 / 完整样式系统 / 正式页面迁移。精力压在 Agent 主链。（覆盖早前"不新造 Vue 工程"措辞）
- **规模预期**：几百行核心即可 demo 出 G2 全貌。

---

### D8 — 项目来源（2026-07-12）
- **结论**：I1 — 继续基于 hmdp / yun-dp 增量改造，在现有 `dp` 仓库 Java 上挂 Agent 链路，复用现成房间/用户/商铺表。
- **叙事**："在真实业务系统上安全接入 AI 层"，Java 系统-of-record 成加分项。hmdp 是公认教学底座无妨，加分项在原创的 AI 增量层。
- 排除：I2(greenfield，丢"嵌入现有系统"故事) / I3(独立干净模块，前期重构成本)。

---

### D9 — 技术栈锁定程度（2026-07-12）
- **结论**：J1 — LangGraph 锁死，其余弹性。
- **理由**：G2 的挂起/恢复人类在环状态机正是 LangGraph 招牌能力（`interrupt` + `Command(resume=...)` + checkpointer），免费拿到最难部分；且是简历高频关键词。LLM 供应商 / Java 内部实现 / 前端保持弹性，能跑即可。
- 排除：J2(全锁，堆砌且脆弱) / J3(全弹，写不出框架名，E2 减分)。

---

## 已达成共识（D1~D9 决策链固化 · 2026-07-12）

**一句话意图**：在 hmdp/yun-dp 这个真实业务系统上，增量做一个**会主动问人的多人在环 AI Agent**（基于 LangGraph 的挂起/恢复状态机），作为面向 **AI/Agent 工程师岗**的简历亮点作品；MVP 用真实 LLM 端到端跑通"两人偏好冲突 → Agent 挂起澄清 → 人答后 resume → 出方案"这一条最薄纵切。

| # | 决策 | 结论 |
|---|---|---|
| D1 | 北极星 | 学习/作品级 Demo（外壳） |
| D3 | 真动机 | **简历亮点**（推翻 D1/D2；不做"证可替换") |
| D4 | 面向角色 | AI/Agent 工程师向（Java 作佐证） |
| D5 | 演示形态 | 真实 LLM 端到端跑通（风险自担） |
| D6 | wow | 会主动问人的多人在环 Agent（HITL 澄清挂起/恢复） |
| D7 | MVP 纵切 | 1房间+2冲突成员+1次真LLM规划+1次澄清挂起恢复+出方案；砍投票/重规划/多轮/评测集；前端=最小Vue3 Demo Shell |
| D8 | 来源 | 基于 hmdp/yun-dp 增量改造 |
| D9 | 技术栈 | LangGraph 锁死，其余弹性 |

**MVP 硬验收**：真实 LLM 下，一次跑通完整链路（含一次真实的 clarification 挂起 + resume 恢复），并留有 Fake-LLM 单测保底。
**明确不做（v2+）**：投票、重规划、多轮、完整评测集、第二实现证可替换、完整 Vue3 产品前端（仅保留最小 Demo Shell）。

---

## 工单复审修订（2026-07-12 晚）

用户复审 `.scratch/meetmate-mvp/issues/` 后确认整体可用（≈90%），并落定 5 个必须修正 + 7 个优化，均已写入工单，此处记锚点：

**必须修正（已落地）：**
1. **06 澄清语义闭环**：原 `ASK_A_RELAX/ASK_D_RELAX` 房主选"去问 A"并不等于 A 同意放宽。改为 MVP 让 **A=房主**，澄清直接问 A：`RELAX_OWNER_HARD_FOOD_CATEGORY` → `RELAX_TO_SOFT` / `CANCEL_PLAN`，一轮即解除双 HARD 冲突。
2. **05 可靠启动改为 DB Dispatcher**：原"写库后发 MQ"有事务缺口。改为 `tb_meet_agent_run` 增 `dispatch_status/dispatch_attempts/next_dispatch_at/dispatch_error`，AgentRunDispatcher 轮询 PENDING→调 Python→ACKED，退避重试；MVP 不引 Outbox+MQ，与 06 ResumeDispatcher 同构。
3. **06 Resume 表字段补齐**：增 `answer_json/answered_by/answered_at/version/resume_status(NOT_READY/PENDING/DISPATCHING/ACKED/FAILED)/resume_attempts/next_retry_at/resume_error`；幂等键 = `clarificationId + answerVersion`。
4. **CONTEXT ↔ 08 前端冲突**：CONTEXT 原写"不新造 Vue 工程"，与 08 的 Vue3 `web/` 矛盾。拍板保留最小 Vue3 Demo Shell，更新 CONTEXT D7 / 共识表 / 明确不做清单。
5. **02 商铺坐标错位**：`x/y/avg_price` 属 `tb_shop` 基础字段，移出 `tb_shop_meet_meta`；meta 仅放 `tags_json/spicy_level/business_hours_json/allergen_tags_json/source/confidence`；种子同时写两表，至少 5~8 家、3+ 家过澄清后硬约束。

**优化（已落地）：** 07 与 06 可并行（07 Blocked by 05，E2E 验收卡 06）；INDEX 措辞改为"增量可验证工单"而非"每张都是最薄纵切"；状态拆 `Spec READY` + `Execution BLOCKED/READY`；04 偏好表增 `confirmed_json/status/parser_version/version`；03 增 Java→Python 服务签名校验（X-Service-*，规范在 01）；05 的 interrupt 测试移至 06；08 "最终方案"改为"候选方案/推荐方案列表"。
