# MeetMate 改造计划架构评审

> 评审对象：`doc/01~03_MeetMate_*.md`（前端 / Java 后端 / Python Agent）v1.0
> 评审角色：Software Architect
> 结论（v1.0 首评）：**方向正确，质量高于同类教学/副业项目；存在 3 个 P0 级设计缺口，需在动手前补完**（已于 v1.1 拍板、v1.2 落地）
> **状态更新（2026-07-11 二次评审）**：三份设计稿已升 **v1.2**，补齐轮次实体、澄清多回答模型、澄清内部接口、可靠 resume 投递、candidateId 绑定、状态机冲突修正、Checkpoint 配置统一、shop_meet_meta 治理与种子数据。R1~R3 与 P1 投票/局部重规划/Checkpoint 已 **RESOLVED**；R7/R12 **PARTIAL**（种子数据待落地、CI 双向契约校验待落实）；R8/R9 已在 v1.2 文档补充（**RESOLVED**）；R11 仍为 **OPEN**（无 OTel）。下方风险表新增「状态」列，本评审不再宣称"R1~R12 全部闭环"。

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

| ID | 严重度 | 区域 | 问题 | 建议（v1.2 落地） | 状态 |
|----|--------|------|------|------|------|
| R1 | **P0** | 编排时序 | 跨服务澄清时序契约缺失 | `WAITING_INPUT` 结构化问题 + clarification API + Java 持有 HITL + Python 挂起（§33/ADR-002） | RESOLVED |
| R2 | **P0** | 评分归属 | 双份分数冲突 | Java 独占评分，Python 输出禁 score，Ranking Snapshot 防漂移（§34/ADR-001） | RESOLVED |
| R3 | **P0** | 时间驱动 | 无调度器房间不过期 | Guard + `@Scheduled` 扫描 + Agent 超时按 runType 补偿（§32/ADR-003） | RESOLVED |
| R4 | P1 | 状态机 | FINALIZED 缺重规划边 | `FINALIZED → REPLAN_PENDING → REPLANNING`，补恢复边（§8） | RESOLVED |
| R5 | P1 | 投票规则 | 通过/否决算法未定义 | 多数制单选择，floor(N/2)+1（ADR-004） | RESOLVED |
| R6 | P1 | Checkpoint | 存储与无 DB 边界 | Redis 独立 client/ACL/prefix/TTL + 固定配置（§17/§28） | RESOLVED |
| R7 | P1 | 数据 bootstrap | meta 来源与 UNKNOWN 未定义 | 来源枚举 + UNKNOWN 按字段处理 + 种子脚本位置（§10.8.1） | PARTIAL（种子数据待实际落地） |
| R8 | P2 | Nginx | SSE 缓冲未关 | 部署文档补 `proxy_buffering off` + 长 timeout（前端 §20） | RESOLVED |
| R9 | P2 | 部署 | compose 仍引 frontend | 明确 build context 指向 `web/`，nginx 反代 `java-app:8081`（Java §29） | RESOLVED |
| R10 | P2 | 状态机冗余 | WAITING_VOTE_RESULT 死状态 | 合并为单一 `WAITING_INPUT`（v1.1） | RESOLVED |
| R11 | P2 | 可观测性 | 无 OTel | 仍仅结构化日志 + requestId，OTel 留后期 | OPEN（接受风险） |
| R12 | P2 | 契约测试 | CI 双向校验未落实 | 双向 OpenAPI/JSON Schema 已定义，CI 校验待落实 | PARTIAL |

---

> **v1.2 状态**：以下两份建议在 v1.1 已升级为 Java 文档中的 **ADR-001（评分归属）** 与 **ADR-002（HITL 编排）**，并随 v1.2 补充了澄清内部接口（§18.3）、可靠 resume 投递（§33.6）、candidateId 绑定等落地细节，状态 **RESOLVED**。

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

## 五、动手前应先拍板的开放问题（v1.2 已全部闭环）

1. 澄清交互数据契约（R1/ADR-2）—— ✅ 已定义 `WAITING_INPUT` + `tb_meet_clarification` + clarification API + `request_clarification` 内部接口 + ResumeDispatcher。
2. `voting_rule` 取值与聚合算法（R5）—— ✅ 冻结为 `SUPPORT`/`REJECT_ALL`/`ABSTAIN` 多数制（ADR-004）。
3. `tb_shop_meet_meta` 来源与降级（R7）—— ✅ 来源枚举 + UNKNOWN 按字段处理 + 种子脚本位置已定义（种子数据待落地）。
4. Checkpoint 是否复用 Java Redis、namespace（R6）—— ✅ 复用 Redis 独立 client/ACL/prefix/TTL，配置固定为 redis（§17/§28）。
5. 过期/倒计时驱动方（R3）—— ✅ Guard + `@Scheduled` 扫描双保险，暂不用延时 MQ。
6. Python 是否需要 `WAITING_VOTE_RESULT`（R10）—— ✅ 删除，合并为单一 `WAITING_INPUT`。

---

## 六、评审结论

计划**合理且可落地**，不需要推翻重来。v1.1 已拍板 R1/R2/R3 与 P1 投票/局部重规划/Checkpoint/评分归属，v1.2 已补齐轮次实体、澄清多回答模型与可靠 resume 投递、candidateId 绑定、状态机冲突与 Checkpoint 配置。**当前可进入 P0 基线（基线修复 + 建表）开发**；但 R7（种子数据实际落地）、R11（OTel）、R12（CI 双向契约校验）仍为 PARTIAL/OPEN，需在对应模块实现时补全——不属于编码前阻塞项。
