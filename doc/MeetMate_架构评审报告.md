# MeetMate 改造计划架构评审

> 评审对象：`doc/01~03_MeetMate_*.md`（前端 / Java 后端 / Python Agent）v1.0
> 评审角色：Software Architect
> 结论：**方向正确，质量高于同类教学/副业项目；存在 3 个 P0 级设计缺口，需在动手前补完**
> **状态更新（2026-07-11）**：R1/R2/R3 与 P1 投票规则、FINALIZED 局部重规划、shop_meet_meta 治理、Checkpoint 隔离均已由用户正式拍板，并回写进三份设计稿 **v1.1**（§32~§35 R3/R1/R2/ADR-001~004）。本报告中的 R1~R12 视为已闭环，R10 状态冗余问题随 WAITING_* 合并为单一 WAITING_INPUT 解决。

---

## 一、总体结论

这份计划最大的亮点，是**边界切得对**：把"确定性业务"和"非确定性语义"物理分离。

- Java（Spring Boot 单体）是**唯一业务事实来源**，拥有状态机、事务、硬约束终审、审计；
- Python（FastAPI + LangGraph）是**无数据库、仅内网、可被随时替换**的语义/编排服务；
- 前端只认 Java，绝不直接调 Python。

这套分层避免了"AI 服务反向成为业务主系统"这一最常见的架构错误，也为后续把 Python 换成别的模型/框架留了退路。**这是对的，不要动摇。**

但三份文档在"组件职责"层面非常扎实，在"跨服务编排的端到端时序"层面偏弱——而这恰恰是整套系统最难、最容易返工的地方。下面按严重度列出缺口。

---

## 二、做得好的地方（保持）

1. **领域化分包**：`com.hmdp.meet.{room,member,preference,planning,...}` + 独立 `agentgateway`，不再堆 `controller/service/impl`。
2. **状态机集中**：Room 与 AgentRun 各有独立状态机，禁止 Controller/Mapper 直接改状态。
3. **幂等设计完整**：创建房间（Idempotency-Key）、创建 Run（roomId+activeRunType+Redisson）、保存方案/投票（唯一键 + Upsert）。
4. **防御纵深**：Python 提议方案 → Java 二次校验（shopId 存在、在候选集、不违反硬约束、时间合法、不重复）。
5. **安全基线成熟**：Python 内网、服务签名 + 防重放、无 DB、Prompt Injection 视为数据、禁止执行模型生成 SQL/代码、日志脱敏。
6. **测试/评测体系**：单元 / 集成（Testcontainers）/ 契约 / 并发 / 偏好解析评测集，强度远超一般项目。
7. **克制的技术选型**：首期明确排除微服务、Nuxt、GraphQL、WebSocket、mTLS，先做 REST+SSE，正确。
8. **基线修复清单**（ThreadLocal、MQ 强制异常、商铺写权限、统一缓存锁）——这些是动新功能前的必要条件，已识别。

---

## 三、风险登记（按严重度）

| ID | 严重度 | 区域 | 问题 | 建议 |
|----|--------|------|------|------|
| R1 | **P0** | 编排时序 | Java 调 Python 是"异步长任务 + 可中断 + 需用户补信息"的组合，但三份文档只定义了组件，未定义**端到端时序契约**：Python 进入 `WAITING_*` 后，如何把"待澄清问题"落到前端、前端作答后如何 `resume` 回 Python。 | 定义 `WAITING_INPUT` 进度事件的载荷（问题类型/选项/截止时间），Java 落"pending clarification"并暴露 `GET/POST /clarifications`；用户作答后 Java 调 `Python resume`。见第四节 ADR-2。 |
| R2 | **P0** | 评分归属 | Java（§16）是群体分/公平性/成员分的**权威**计算方；但 Python 的 `ProposalSuggestion.member_matches[].score` 也允许 LLM 填数字。两份分数可能不一致，前端不知信谁。 | **Java 独占所有数值评分**；Python 只回传 `shopId + 定位 + 解释 + 成员匹配文本`，数值在 Java 落库时由 §16 计算覆盖。见 ADR-1。 |
| R3 | **P0** | 时间驱动状态 | `preferenceDeadline` / `votingDeadline` 之后的 `EXPIRED` 转换没有调度器。无 scheduler / 延时消息，房间永远不会过期。 | 增加 Spring `@Scheduled` 或 RabbitMQ 延时消息，定期扫描超时房间/投票并转 `EXPIRED`；同时驱动前端倒计时。 |
| R4 | P1 | 状态机 | 前端 §7.9 支持 FINALIZED 上"发起局部替换"，但 Java §8 状态机 **没有 `FINALIZED → REPLANNING` 边**。 | 补一条 `FINALIZED --> REPLANNING` 转换（带版本号递增）。 |
| R5 | P1 | 投票规则 | `voting_rule` 字段存在，但"通过条件""排名+否决如何合成胜者""单个否决是否整轮失败"均未定义——这是核心业务规则。 | 明确 `voting_rule` 枚举（如 MAJORITY / UNANIMOUS / VETO_BLOCKS）与聚合算法，写进 ADR。 |
| R6 | P1 | Checkpoint 存储 | Python"不连 MySQL"成立，但 LangGraph Checkpointer 需存储（env 写 `postgres_or_redis`）。是否复用 Java 的 Redis？"无 DB"边界需澄清。 | 复用独立 Redis DB（或 Java Redis 的专用 namespace），明确与"无 MySQL"不冲突；禁止 Python 读写业务表。 |
| R7 | P1 | 数据ootstrap | `tb_shop_meet_meta`（辣度/地铁距离/过敏标签/营业时间）是硬约束的燃料，但**数据来源未定义**（人工 seed？外部 API？）。缺它硬过滤形同虚设。 | 明确数据来源与初始化脚本；MVP 可先接受"无 meta 则跳过该项硬约束"的降级。 |
| R8 | P2 | Nginx | SSE 经 Nginx 需 `proxy_buffering off` + 较长 `proxy_read_timeout`，文档未提。 | 部署文档补 Nginx SSE 配置。 |
| R9 | P2 | 部署 | 新前端目录为 `web/`（旧 `frontend/` → `frontend-legacy/`），`docker-compose.yml` 仍引用 `frontend`，需同步改名。 | 更新 compose 的 build 上下文与 nginx `/api` 反代目标（`java-app:8081`）。 |
| R10 | P2 | 状态机冗余 | AgentRun 的 `WAITING_VOTE_RESULT` 语义不清——投票是 Java 主导的用户阶段，Python 不应"等投票"。疑似死状态。 | 若无"投票后由 Python 生成终稿"的明确用途，删除该状态。 |
| R11 | P2 | 可观测性 | 仅有结构化日志 + requestId，无分布式追踪（OTel）。跨 Java/Python 排障靠日志拼接。 | 后期引入 OpenTelemetry，首期可接受。 |
| R12 | P2 | 契约测试 | 双向 OpenAPI 契约已提，但未明确"Java 与 Python 各自发布 schema，对方据此做契约测试"的对等动作。 | 约定双方向各发布 OpenAPI/JSON Schema，CI 双向校验。 |

---

## 四、建议补的两份 ADR

### ADR-1：评分归属 —— Java 独占数值评分

- **Context**：Python 的 `ProposalSuggestion` 含 `member_matches[].score`，Java §16 也是权威评分方，两份分数可能冲突。
- **Decision**：所有数值（groupScore / fairnessScore / 各成员 score / 明细分）**一律由 Java 在落库时计算**。Python 的 proposal 载荷只含 `shopId / suggestedTime / positioning / explanation / member match 文本`，不得携带可影响展示的数值分；即使携带，Java 落库时以 §16 结果覆盖。
- **Consequences**：✅ 前端永远信 Java；✅ LLM 无法"美化"分数误导用户；❌ Python 方案解释需基于 Java 已回传的候选分撰写（依赖 `shops/rank` 工具先返回分）。

### ADR-2：编排模型 —— Java 异步触发、Python 回调驱动、用户交互全经 Java

- **Context**：规划是长任务且可中断（缺信息 / 需放宽确认）。若把"与用户对话"直接交给 Python，会绕开 Java 的权限与状态机。
- **Decision**：
  1. Java `startPlanning` **fire-and-forget**（拿到 `accepted` 即返回，不阻塞）；房间先转 `PLANNING`。
  2. Python 仅通过 **Tool API 回调**（progress / proposals）与 **progress 事件（含 `WAITING_INPUT`）** 与 Java 通信。
  3. Python 进入 `WAITING_*` 时，发 `WAITING_INPUT` 事件（载荷含 questionType / options / deadline）；Java 落 `pending_clarification` 并暴露给前端。
  4. 前端作答 → `POST /clarifications` → Java 调 `Python resume(runId, answer)`。
  5. 取消：Java 转 `CANCELLED` 并调 `Python cancel`，Python 收到后停止并回调。
- **Consequences**：✅ 权限/状态始终由 Java 守门；✅ 用户对话不绕过业务状态机；❌ 需额外定义 clarification 数据契约（R1 的落地）。

---

## 五、动手前应先拍板的开放问题

1. 澄清（clarification）交互的数据契约长什么样？（R1/ADR-2）
2. `voting_rule` 的取值与通过/否决聚合算法？（R5）
3. 硬约束所需的 `tb_shop_meet_meta` 从哪来、MVP 缺数据时如何降级？（R7）
4. Checkpoint 是否复用 Java 的 Redis，namespace 如何隔离？（R6）
5. 过期/倒计时由谁驱动（scheduler vs 延时 MQ）？（R3）
6. Python 是否真的需要 `WAITING_VOTE_RESULT`？（R10）

---

## 六、评审结论

计划**合理且可落地**，不需要推翻重来。优先在编码前补完 R1/R2/R3 三处 P0（编排时序、评分归属、时间调度），并在设计文档中显式回答第五节 6 个问题。其余 P1/P2 可在实现对应模块时按需补全。
