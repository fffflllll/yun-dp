# MeetMate 当前 AI Agent / Harness 架构

> 状态：当前实现说明  
> 基线：Spring Boot 4.1 + Spring AI 2.0 Java 单体  
> 更新日期：2026-07-16

本文描述仓库中**已经实现**的 AI 能力、运行链路和可靠性边界。它以当前 Java 代码为准，不延续早期 Python / LangGraph 方案中的跨服务 Agent、Checkpoint、`interrupt / resume`、投票、否决轮次或重规划设计。

本文所说的 **AI Harness**，不是另一个 Agent 框架，而是包围模型调用的确定性 Java 执行边界：它负责提供可信输入、限定工具权限、推进运行状态、执行最终校验、持久化结果并输出安全的执行轨迹。LLM 只是 Harness 内部的一个非确定性组件。

---

## 1. 一句话架构

MeetMate 在现有 Java 单体内提供两项相互独立的 AI 能力：

1. **Preference Parser** 将成员自然语言解析为可编辑的结构化偏好草稿；成员确认后，结构化偏好才成为业务事实。
2. **Planning Agent** 在异步 `PlanRun` 中，通过请求级只读工具读取已确认偏好和 Java 候选集，生成 1 个首选与 2 个备选；Java Policy 通过后才能落库。

```text
成员自然语言
  -> Preference Parser
  -> 可编辑 Draft
  -> 成员本人确认
  -> Confirmed Preference（业务事实）

房主开始规划
  -> PlanRun / PlanAttempt 写入 MySQL
  -> DB Dispatcher 投递 RabbitMQ
  -> Java Planning Harness
       -> 候选预检
       -> Spring AI ChatClient + 请求级只读工具
       -> Java Policy 最终复核
  -> Proposal / PlanEvent / 状态写入 MySQL
  -> SSE 展示安全执行轨迹
  -> 房主选择一项方案并由 Java 再检查
```

核心原则是：

- MySQL 中的 Java 业务数据是唯一事实来源。
- 模型输出始终是待校验建议，不直接成为业务结果。
- 模型没有修改偏好、放宽约束、确认方案或改变房间状态的工具。
- RabbitMQ 和 SSE 都是传输通道，不承载权威业务事实。
- 用户看到固定阶段和脱敏摘要，不看到 Prompt、Token 流或模型隐藏思维链。

---

## 2. 当前范围与明确非目标

| 当前实现 | 当前明确不实现 |
|---|---|
| Spring AI 与业务代码运行在同一个 Java 应用 | 独立 Python Agent 服务 |
| `ChatClient` 结构化输出和请求级 Tool Calling | LangGraph Graph、Checkpoint、`interrupt / Command(resume)` |
| Java 编排的有限 Plan-and-Execute | 模型自由选择任意步骤的全自治 Agent |
| 一次 PlanRun 最多进行一次定向澄清 | 无限澄清、多轮自治对话 |
| 澄清后在同一 Run 下创建新 Attempt | 恢复原模型上下文或 JVM 调用栈 |
| 房主从三个建议中确认一项 | 成员投票、否决、平票、投票截止 |
| 澄清授权后的再次尝试 | 方案否决驱动的重规划、局部重规划 |
| 只读工具和 Java 最终校验 | 模型写库、执行 SQL、直接确认方案 |
| 持久 PlanEvent + SSE 补发 | 原始 Chain-of-Thought 或逐 Token 推理展示 |

旧的 Python / LangGraph 文档可作为历史设计材料阅读，但不能作为当前实现契约。当前路线的架构决策见 `docs/adr/0001-use-spring-ai-in-java-monolith.md`。

---

## 3. 组件与职责

| 组件 | 当前职责 | 不负责 |
|---|---|---|
| `MeetPreferenceAiService` | 自然语言偏好结构化；AI 关闭或异常时生成启发式草稿 | 确认偏好、直接推进规划 |
| `MeetPreferenceServiceImpl` | 校验成员权限；保存 Draft / Confirmed；全员确认后推进房间 | 让模型决定什么是业务事实 |
| `MeetPlanServiceImpl` | 创建 Run/Attempt、处理澄清答案、查询 Run、确认方案 | 执行模型 Tool Calling |
| `MeetPlanAttemptDispatcher` | 从数据库扫描待投递 Attempt，投递 RabbitMQ，恢复陈旧投递标记 | 保存完整业务上下文 |
| `MeetPlanAttemptListener` | 消费 `runId + attemptId` 并进入执行服务 | 解释或校验模型结果 |
| `MeetPlanExecutionService` | 推进 Run/Attempt/Room 状态，调用 Agent，保存澄清或方案 | 决定候选过滤规则的细节 |
| `MeetPlanningAgent` | 创建有限执行计划，绑定请求级工具，检查必要工具是否完成 | 直接写 Proposal、Room 或 Preference |
| `MeetPlanningAiService` | 构造 Planning Prompt、执行结构化模型调用、生成澄清文案 | 作为最终业务规则裁判 |
| `MeetPlanningTools` | 暴露当前 Room/Run/Attempt 的四个只读工具并记录工具轨迹 | 提供跨房间查询或业务写能力 |
| `MeetRestaurantCandidateService` | 读取已确认偏好、召回餐厅、执行硬约束过滤与群体排序 | 接受模型生成的候选事实 |
| `MeetPlanPolicyService` | 校验模型方案的数量、候选归属、唯一性和必要字段 | 信任模型的自然语言解释 |
| `MeetPlanEventService` | 持久化 PlanEvent、SSE 实时推送和断线补发 | 取代 Run/Attempt 状态表 |

Planning Agent 不是一个拥有长期记忆的单例对象。每个 Attempt 都创建新的 `MeetPlanningTools` 实例；已加载偏好、候选集、完成工具名和已查看候选 ID 只在本次 Java 调用内存在。

---

## 4. Preference Parser：草稿先于事实

### 4.1 解析入口

成员调用：

```http
POST /api/meet/rooms/{roomId}/preferences/parse
```

Java 先验证：

- 当前用户已登录且是该房间 `JOINED` 成员；
- 房间处于 `COLLECTING_PREFERENCES` 或 `MEMBERS_LOCKED`；
- 自然语言非空且不超过 1000 字符。

随后 `MeetPreferenceAiService` 使用 `ChatClient` 将自然语言解析为 `MeetPreferenceData`，主要字段包括：

- `budgetMax`；
- `preferredCuisines`；
- `avoidFoods`；
- `allergens`；
- `acceptsSpicy`；
- `maxDistanceMeters`；
- `preferredTime`；
- `hardConstraintKeys`；
- `notes`。

Prompt 要求只提取用户明确表达的信息，不猜测预算、过敏或时间；金额统一为人民币元，距离统一为米。返回值通过 Spring AI 映射为结构化 DTO，而不是把任意模型文本直接存成业务对象。

### 4.2 降级行为

当 `meetmate.ai.enabled=false`，或模型调用、结构化映射失败时，Parser 使用本地启发式规则提取有限字段，例如预算、距离、辣度和少量菜系，并返回 `aiParsed=false` 与提示信息。

降级结果仍只是可编辑草稿。AI 不可用不会迫使系统接受不可信数据，也不会阻断成员手工修正。

### 4.3 草稿与确认边界

解析后保存：

- 原文到 `raw_text`；
- 结构化结果到 `draft_json`；
- 状态置为 `DRAFT`；
- 清空旧的 `confirmed_json / confirmed_at`；
- 保存 `parser_version`。

成员检查并调用：

```http
PUT /api/meet/rooms/{roomId}/preferences/confirm
```

Java 才会把成员提交的结构化对象写入 `confirmed_json` 并将状态改为 `CONFIRMED`。Planning Agent 只读取 `CONFIRMED` 数据，不读取自然语言原文或 Draft。

当房主已经锁定成员且所有 `JOINED` 成员均确认偏好时，房间才进入 `READY_TO_PLAN`。确认最后一位成员偏好不会自动调用模型；开始规划仍是房主的显式操作。

---

## 5. PlanRun、PlanAttempt 与 PlanEvent

三个对象分离业务流程、实际执行和展示轨迹：

### 5.1 PlanRun

`PlanRun` 是用户可见的一次房间规划流程，持有：

- `roomId`；
- 当前状态；
- 当前 Attempt 编号；
- 已进行的澄清次数；
- 被房主选中的 Proposal；
- 流程级错误与完成时间。

当前状态集合：

```text
QUEUED -> RUNNING -> SUCCEEDED
                  -> FAILED
                  -> WAITING_INPUT -> QUEUED -> RUNNING -> SUCCEEDED / FAILED
                                    -> CANCELLED
```

`WAITING_INPUT -> QUEUED` 不是恢复旧模型会话，而是用户回答后创建新 Attempt 并重新执行规划。

### 5.2 PlanAttempt

`PlanAttempt` 是一次后台执行记录，持有：

- `runId + attemptNo` 唯一标识；
- `QUEUED / RUNNING / WAITING_INPUT / SUCCEEDED / FAILED`；
- `dispatchStatus / dispatchAttempts / nextDispatchAt`；
- 模型名与 Prompt 版本；
- 开始、结束时间和错误摘要。

首次规划创建 Attempt 1。成员同意一次定向约束放宽后，在同一个 Run 下创建 Attempt 2。当前 MVP 不因投票、否决或普通用户要求创建重规划 Attempt。

### 5.3 PlanEvent

`PlanEvent` 是持久化的安全执行轨迹，按 Run 保存单调 `sequence`，并可绑定具体 Attempt。事件内容由固定事件类型、用户可读摘要和可选的脱敏 JSON 组成，不保存模型原始思维链。

典型事件包括：

- `RUN_QUEUED / RUN_STARTED`；
- `AGENT_PLAN_CREATED`；
- `TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED`；
- `PREFERENCES_READ`；
- `CONSTRAINTS_CHECKED`；
- `RESTAURANTS_RECALLED / RESTAURANTS_FILTERED`；
- `PLANS_DRAFTED / PLAN_REVIEWED / PLANS_VALIDATED`；
- `WAITING_INPUT`；
- `RUN_COMPLETED / RUN_FAILED / PLAN_CONFIRMED`。

Run/Attempt 是业务状态，PlanEvent 是观察与审计记录。两者不能互相替代。

---

## 6. 启动、Dispatcher 与 RabbitMQ

### 6.1 创建规划流程

房主调用：

```http
POST /api/meet/rooms/{roomId}/plan-runs
```

`MeetPlanServiceImpl.start` 在数据库事务内完成：

1. 验证用户是房主；
2. 验证房间处于 `READY_TO_PLAN`；
3. 使用房间级 Redisson 分布式锁避免跨实例并发创建；
4. 查询该房间是否已有 `QUEUED / RUNNING / WAITING_INPUT` 的活跃 Run；
5. 插入 `PlanRun(QUEUED)`；
6. 插入 `PlanAttempt(QUEUED, dispatchStatus=PENDING)`；
7. 将房间改为 `PLANNING`；
8. 插入 `RUN_QUEUED` 事件；
9. 注册事务提交后的 Dispatcher 唤醒。

数据库事实先提交，MQ 投递后发生，因此消息中无需复制偏好、候选或 Prompt。

### 6.2 DB Dispatcher

Dispatcher 的当前行为：

- 收到本地唤醒后扫描最多 20 条 `QUEUED + PENDING + nextDispatchAt <= now` 的 Attempt；
- 先把 `dispatchStatus` 改为 `DISPATCHED` 并增加投递次数；
- 再向 RabbitMQ 发送 `MeetPlanAttemptMessage(runId, attemptId)`；
- 同步发送失败时恢复为 `PENDING`，并把下次投递时间推迟 5 秒；
- 定时扫描超过 30 秒仍为 `QUEUED + DISPATCHED` 的记录，将其重置为 `PENDING`。

应用启动时 Dispatcher 的唤醒标记默认为开启，因此会进行一次待投递扫描；常规运行中由事务提交后的回调请求扫描。

### 6.3 RabbitMQ 边界

规划队列使用持久 DirectExchange、持久 Queue 和固定 Routing Key。消息只携带：

```json
{
  "runId": 123,
  "attemptId": 456
}
```

消费者收到消息后重新读取 MySQL。RabbitMQ 负责“通知哪个 Attempt 可以执行”，不负责保存房间快照或恢复模型上下文。

监听器配置为自动 ACK，并启用容器本地重试，总消费尝试上限为 3 次。但是规划方法会把大多数运行期异常转换为 `FAILED` 状态后正常返回，因此这些业务/模型失败通常不会触发 MQ 重试；容器重试主要覆盖逃逸出监听方法的基础设施异常。

---

## 7. Planning Harness 执行链路

`MeetPlanExecutionService.execute(runId, attemptId)` 是后台状态机入口：

1. 读取 Run 与 Attempt；
2. 仅当 Attempt 仍为 `QUEUED` 且属于该 Run 时继续；
3. 将 Attempt 与 Run 改为 `RUNNING`，记录模型名和开始时间；
4. 调用 `MeetPlanningAgent.plan`；
5. 根据结果进入澄清、成功或失败路径。

### 7.1 Java 预检

`MeetPlanningAgent` 首先创建绑定当前 `Room / Run / Attempt` 的 `MeetPlanningTools`，然后在模型调用前执行一次 Java 候选预检。

注意：当前代码的澄清阈值是**可行候选少于 3 家**，不是只在 0 家时触发，因为最终输出必须恰好包含 3 个不同餐厅。

若候选不足 3 家，Harness 不调用规划模型生成方案，而是补记“读取偏好”和“查找候选”的工具轨迹，然后进入定向澄清或失败路径。

### 7.2 AI 开启时

候选至少 3 家时，`MeetPlanningAiService.generatePlans`：

- 使用当前配置的 OpenAI 兼容 ChatModel；
- 关闭并行工具调用；
- 通过 `.tools(tools)` 只为本次请求注入工具实例；
- 要求模型按顺序读取偏好、查找候选、查看详情并校验草稿；
- 要求最终结构化映射为 `AiMeetPlanSet`；
- 要求三个不同 `shopId`，首项为首选；
- 要求集合地点逐字使用详情工具返回的地址；
- 禁止编造餐厅、地址、评分、营业时间或成员偏好。

模型调用结束后，Harness 还会检查：

- 四个必要工具是否都至少成功完成一次；
- 最终三个方案引用的餐厅是否都经过详情工具查看；
- 最终 Plan Set 是否通过 Java Policy。

缺少必要工具、使用未查看餐厅或 Policy 不通过，都会让当前 Attempt 失败。

### 7.3 AI 关闭时

当 `meetmate.ai.enabled=false`，系统不会调用外部模型，而是：

- 从 Java 已排序候选中取前三名；
- 使用首个可用的成员时间偏好或默认提示；
- 生成固定说明；
- 由 Java 主动执行同一套工具轨迹与 Policy 校验。

这是本地开发和无模型环境的确定性降级路径，不等同于真实 AI 效果。

---

## 8. 请求级只读工具

四个工具由同一个 `MeetPlanningTools` 实例提供。实例在 Java 中预绑定当前 `roomId / runId / attemptId`，工具签名不允许模型传入其他房间 ID。

| 工具 | 输入 | 输出 | 安全约束 |
|---|---|---|---|
| `read_confirmed_preferences` | 无 | 当前房间已确认成员偏好 | 不返回 Draft；不修改偏好 |
| `find_feasible_restaurants` | 无 | 召回数、过滤后数量、过滤原因、最多 20 家摘要 | 候选完全由 Java 计算 |
| `inspect_restaurant_candidates` | 1 至 8 个 shopId | 合法候选详情与未知 ID | 只能查看本次候选集成员 |
| `validate_plan_draft` | `AiMeetPlanSet` 草稿 | `valid + errors` | 只校验，不保存方案 |

工具内部缓存本次调用已加载的偏好和候选，避免同一请求重复查询；同时记录完成工具名和已查看候选 ID，供 Harness 在模型返回后检查。

工具可以写入 PlanEvent，因为 PlanEvent 是安全执行轨迹；工具不能写入 Preference、Proposal、Room、Run 状态或已确认方案。

当前实现没有以下写工具：

- `confirm_plan`；
- `relax_constraint`；
- `update_preference`；
- `change_room_status`；
- 任意 SQL / HTTP / 文件执行工具。

---

## 9. Java 候选与 Policy 边界

### 9.1 候选召回和硬约束

`MeetRestaurantCandidateService` 当前从 `typeId=1` 的餐厅中召回数据，结合房间中心点、搜索半径、商铺基础数据和 `ShopMeetMeta` 进行过滤。

当前硬过滤包括：

- 超出房间搜索半径；
- 超过任一成员的硬预算；
- 超过任一成员的硬最大距离；
- 不满足硬辣度约束；
- 过敏原冲突；
- 存在硬过敏约束但餐厅缺少过敏原元数据。

通过硬约束后，Java 使用商铺评分、距离惩罚、预算满足和菜系偏好计算 `groupScore` 并降序排序。数值排名属于 Java，不由模型产生或覆盖。

### 9.2 最终方案 Policy

`MeetPlanPolicyService` 当前校验：

- 必须恰好三个方案；
- 每个方案必须有 `shopId`；
- 三个 `shopId` 不能重复；
- 餐厅必须属于本次 Java 候选集；
- `suggestedTime` 非空；
- `meetingPoint` 非空，且候选有地址时必须等于候选地址；
- `reasoning` 非空。

Policy 在三处形成防御纵深：模型可主动调用 `validate_plan_draft`，`MeetPlanningAgent` 在模型返回后复核，`MeetPlanExecutionService` 在落库前再次复核。

需要准确理解当前边界：Policy 尚未把时间解析为结构化区间，也未验证建议时间是否处于餐厅营业时间；偏好中的 `avoidFoods` 当前未参与候选过滤；`satisfiedPreferences / tradeoffs / reasoning` 目前只作为解释文本保存，没有逐条事实证明。因此文档和演示不能声称这些语义已被完整机器验证。

---

## 10. HITL 定向澄清

当前 Human-in-the-loop 是**持久化业务状态 + 新 Attempt**，不是 LangGraph Checkpoint 恢复。

### 10.1 触发条件

候选少于 3 家时，Java 根据过滤原因选择一个可安全放宽的目标：

- 硬预算：询问预算最严格的相关成员；
- 硬最大距离：询问距离最严格的相关成员；
- 不接受辣：询问相关成员。

过敏原不会作为可放宽目标。若没有可安全放宽的约束，Run 直接以 `NO_SAFE_RELAXATION` 失败。

目标成员、约束 Key 和固定选项由 Java 决定。AI 开启时，模型只把默认问题改写为简洁、尊重用户的中文；改写失败使用模板文案。模型不能改变目标用户、约束 Key 或答案选项。

### 10.2 持久化等待

Java 插入一条 `MeetClarification(PENDING)`，并推进：

```text
Attempt: RUNNING -> WAITING_INPUT
Run:     RUNNING -> WAITING_INPUT
Room:    PLANNING -> WAITING_INPUT
```

随后当前后台执行结束，不占用 HTTP 连接，不保留等待线程，也不保存模型会话。

### 10.3 回答与新 Attempt

只有 `targetUserId` 对应的成员可以回答：

```http
POST /api/meet/plan-runs/{runId}/clarifications/{clarificationId}/answer
```

当前只接受两个答案：

- `RELAX_TO_SOFT`：Java 从该成员 `confirmedJson.hardConstraintKeys` 中删除指定 Key，记录澄清已回答，在同一个 Run 下创建下一个 `QUEUED` Attempt，并重新投递完整规划；
- `CANCEL_PLAN`：Java 将 Clarification 标记已回答，将 Run 与 Room 改为 `CANCELLED`，不再执行模型。

Run 最多允许一次澄清。第二个 Attempt 仍不足 3 个候选时，以 `NO_FEASIBLE_PLAN` 失败，不继续递归询问。

这里的第二个 Attempt 是“成员明确授权约束放宽后的再次尝试”，不是投票否决后的产品级重规划，也不是恢复原模型调用。

---

## 11. Proposal 落库与房主确认

方案通过 Policy 后，Java 把三个 `AiMeetPlanOption` 转为 `MeetProposal`：

- rank 1 标记为 `recommended=true`；
- 保存 shopId、建议时间、集合地点、估算人均、理由；
- 保存满足偏好和取舍说明 JSON；
- Proposal 绑定当前 Run 与 Attempt。

随后：

```text
Attempt -> SUCCEEDED
Run     -> SUCCEEDED
Room    -> PLANS_READY
```

房主调用确认接口时，Java 检查：

- Run 已成功；
- 当前用户是房主；
- Proposal 属于该 Run；
- 商铺仍然存在；
- 按当前房间和已确认偏好重新计算候选时，该商铺仍可行。

通过后写入 `selectedProposalId / confirmedProposalId`，房间进入 `FINALIZED`。Run 保持 `SUCCEEDED`，并追加 `PLAN_CONFIRMED` 事件。

当前确认动作是房主单人决策。系统没有成员投票、否决票、多数票阈值、投票轮次或否决后重规划。

---

## 12. SSE 与可解释执行轨迹

成员可通过以下接口订阅：

```http
GET /api/meet/plan-runs/{runId}/events
Last-Event-ID: <sequence>
```

也可使用 `?after=<sequence>`。服务端取两者较大值，从 MySQL 查询并补发其后的 PlanEvent。

SSE 建立后先发送 `connected` 事件，再补发数据库历史事件；新事件若处于事务中，会在事务提交后推送，未处于事务中则在事件插入后立即推送给当前 Java 实例内的订阅者。SSE 断开不会取消 Run，客户端可重新查询：

```http
GET /api/meet/plan-runs/{runId}
```

该接口返回 Run、所有 Attempt、事件、最新 Clarification 和 Proposal，MySQL 状态仍是恢复页面的最终依据。

SSE Payload 只包含：

- eventId、runId、attemptId、sequence；
- 固定事件类型；
- 安全摘要；
- 可选的脱敏 JSON；当前 `payload` 字段以 JSON 字符串发送，客户端需要按需解析；
- 创建时间。

不推送 Prompt、API Key、完整模型请求、Token 流或隐藏思维链。

---

## 13. 权限边界

| 动作 | 当前权限 |
|---|---|
| 解析、确认本人偏好 | 房间内 `JOINED` 成员，且房间允许编辑偏好 |
| 开始规划 | 房主，且房间 `READY_TO_PLAN` |
| 查询 Run / 订阅 SSE | 房间内 `JOINED` 成员 |
| 回答澄清 | Clarification 指定的目标成员 |
| 取消澄清中的规划 | 目标成员选择 `CANCEL_PLAN` |
| 确认一个 Proposal | 房主 |

模型不会获得用户 Token，也不自行执行上述权限判断。权限由进入 Harness 前后的普通 Java 服务完成。

---

## 14. 配置与供应商边界

主要配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${MEETMATE_AI_API_KEY:not-configured}
      base-url: ${MEETMATE_AI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${MEETMATE_AI_MODEL:gpt-4.1-mini}
          temperature: 0.2

meetmate:
  ai:
    enabled: ${MEETMATE_AI_ENABLED:false}
    prompt-version: v2
    request-timeout: ${MEETMATE_AI_REQUEST_TIMEOUT:45s}
    max-completion-tokens: ${MEETMATE_AI_MAX_COMPLETION_TOKENS:1200}
    max-model-retries: ${MEETMATE_AI_MAX_MODEL_RETRIES:1}
    max-tool-calls: ${MEETMATE_AI_MAX_TOOL_CALLS:8}
  plan:
    recovery-interval: ${MEETMATE_PLAN_RECOVERY_INTERVAL:30s}
    dispatch-interval: ${MEETMATE_PLAN_DISPATCH_INTERVAL:1s}
    running-timeout-ms: ${MEETMATE_PLAN_RUNNING_TIMEOUT_MS:180000}
    max-execution-attempts: ${MEETMATE_PLAN_MAX_EXECUTION_ATTEMPTS:2}

security:
  shop:
    admin-user-ids: ${SHOP_ADMIN_USER_IDS:}
```

`base-url` 支持 OpenAI 兼容服务。业务代码不写死真实 Key；默认关闭 AI，便于未配置模型时启动和测试。

Attempt 会记录模型名和 Prompt 版本，但当前尚未持久化完整模型参数、Token 用量、候选快照或模型原始结构化响应。

---

## 15. 已实现的可靠性保证

当前实现已经具备以下基础保证：

- Run、Attempt、Event、Clarification 和 Proposal 均持久化到 MySQL；
- 创建 Run、首次 Attempt、房间状态和首个事件位于同一个事务；
- MQ 通知在创建事务提交后触发；
- RabbitMQ 消息只含标识，消费时以数据库为准；
- Dispatcher 每轮扫描数据库中的到期 `PENDING`，并用 CAS 抢占 `PENDING -> DISPATCHED`；
- 发送失败会把 Attempt 恢复为待投递，陈旧的 `QUEUED + DISPATCHED` 会被重置；
- Consumer 用多表条件更新原子认领 `QUEUED -> RUNNING`，重复消费不会再次调用模型；
- 模型调用前后使用短事务，Proposal、Attempt、Run、Room 和事件在最终提交中保持一致；
- `RUNNING` 超过执行期限会进入明确的超时失败状态，晚到结果不能覆盖终态；
- 瞬时执行异常最多按配置次数重新排队，重试仍由数据库状态驱动；
- 模型结果必须结构化映射并经过 Java 候选与 Policy 校验；
- SSE 可从数据库按 sequence 补发，断线不影响任务；
- 澄清等待不占用模型连接或 JVM 工作线程；
- AI 关闭或偏好解析失败时存在确定性降级路径。

这些保证足以支撑单实例 MVP 演示，但仍不能等价为生产级 exactly-once；多实例 Event/SSE 和更完整的结果快照仍是后续加固项。

---

## 16. 当前可靠性边界与改进顺序

下表区分当前事实与建议，避免把计划中的能力写成已实现能力。

| 优先级 | 当前边界 | 可能后果 | 建议加固 |
|---|---|---|---|
| P1 | 模型异常目前按有限次数重排队，超时则结束当前 Run 并把房间恢复为可重新开始 | 仍需要区分限流、网络、模型格式错误和业务不可行，避免无意义重试 | 为异常建立错误分类和指标，并在后续版本为重试创建独立 Attempt |
| P1 | 工具预算、调用超时和最终草稿指纹已在 Harness 层约束，但 Spring AI 内部循环仍由框架驱动 | 需要持续验证不同 ChatModel 对工具循环和超时的行为 | 用 Fake ChatModel 做契约测试，必要时进一步改成 Java 显式修复轮次 |
| P1 | 时间当前只允许作为软偏好，营业时间和时间窗口尚未结构化；`avoidFoods` 也只做输入保存 | 模型可给出建议时间，但 Java 暂时无法把它作为硬约束完整执行 | 建立时间窗口与营业时间执行器后，再开放 `PREFERRED_TIME` 硬约束 |
| P1 | Event sequence 的 `synchronized` 只保护单 JVM | 多实例并发追加同一 Run 可能撞唯一键 | 使用数据库原子序列、Run 行锁或带冲突重试的 sequence 分配 |
| P1 | SSE 订阅者保存在本机内存 | 多实例下，事件写在 B 节点不会实时推给连接在 A 节点的客户端 | 使用 Redis Pub/Sub、Rabbit fanout 或其他跨节点通知；数据库仍负责补发 |
| P1 | 订阅时先注册 emitter 再查询历史事件，新事件可能与补发并发到达 | 客户端可能收到乱序或重复 sequence | 服务端按水位线完成无缝切换，客户端始终按 `runId + sequence` 排序、去重 |
| P1 | Java Policy 未校验结构化营业时间和解释文本的事实引用 | 可能出现硬候选合法但建议时间不可执行、解释夸大的方案 | 结构化时间与营业时间；增加可机器验证的 preference evidence，不只保存自由文本 |
| P2 | 当前仍缺少输入快照、候选快照、Token、耗时细分和结果哈希 | 线上结果难以复现和评测 | 保存输入/候选快照哈希、模型参数、usage、阶段耗时和 Policy 报告 |
| P2 | 测试已覆盖 CAS 拒绝、Dispatcher 释放、工具顺序/预算、草稿指纹和 Policy 矩阵，但尚无真实 Rabbit/MySQL 故障注入 | 多实例和真实 Tool Calling 仍需环境级回归 | 增加 Testcontainers Rabbit/MySQL、Fake ChatModel 工具循环和 SSE 重连集成测试 |

推荐落地顺序：

1. 完成时间窗口、营业时间和忌口约束执行器；
2. 多实例 Event/SSE、快照、指标和评测；
3. 用 Testcontainers 验证真实 Rabbit/MySQL 故障恢复。

---

## 17. 当前状态机不变量

后续修改应保持以下不变量：

1. Draft 永远不能参与候选计算，只有 `confirmed_json` 可以。
2. 模型不能选择 Java 候选集之外的餐厅。
3. 模型不能通过工具写业务状态。
4. 每个成功 Run 必须有且只有三个不同餐厅 Proposal。
5. 房主确认时必须按当前数据重新检查所选餐厅仍可行。
6. 澄清只能由指定成员回答，且过敏原不能被 Agent 建议放宽。
7. 一个 Run 最多一次定向澄清；回答后是新 Attempt，不是恢复模型记忆。
8. RabbitMQ 与 SSE 断开不能改变 MySQL 中的业务事实。
9. 对外执行轨迹不得包含 Prompt、密钥或模型隐藏思维链。
10. 当前 MVP 不通过投票、否决或自动重规划改变方案。

---

## 18. 当前 API 速查

经前端 Nginx `/api` 前缀访问时：

```text
POST /api/meet/rooms/{roomId}/preferences/parse
PUT  /api/meet/rooms/{roomId}/preferences/confirm
GET  /api/meet/rooms/{roomId}/preferences/me

POST /api/meet/rooms/{roomId}/plan-runs
GET  /api/meet/plan-runs/{runId}
GET  /api/meet/plan-runs/{runId}/events
POST /api/meet/plan-runs/{runId}/clarifications/{clarificationId}/answer
POST /api/meet/plan-runs/{runId}/confirm
```

Spring Controller 内部映射不含 `/api`；该前缀由当前 Nginx 反向代理重写。

---

## 19. 代码索引

- 偏好入口：`src/main/java/com/hmdp/controller/MeetPreferenceController.java`
- 偏好事实边界：`src/main/java/com/hmdp/service/impl/MeetPreferenceServiceImpl.java`
- 偏好解析器：`src/main/java/com/hmdp/service/MeetPreferenceAiService.java`
- 规划入口：`src/main/java/com/hmdp/controller/MeetPlanController.java`
- Run/HITL/确认服务：`src/main/java/com/hmdp/service/impl/MeetPlanServiceImpl.java`
- Dispatcher：`src/main/java/com/hmdp/service/MeetPlanAttemptDispatcher.java`
- Rabbit 监听器：`src/main/java/com/hmdp/listener/MeetPlanAttemptListener.java`
- 执行状态机：`src/main/java/com/hmdp/service/MeetPlanExecutionService.java`
- Agent Harness：`src/main/java/com/hmdp/service/MeetPlanningAgent.java`
- Planning 模型调用：`src/main/java/com/hmdp/service/MeetPlanningAiService.java`
- 请求级工具：`src/main/java/com/hmdp/service/MeetPlanningTools.java`
- 候选过滤与排序：`src/main/java/com/hmdp/service/MeetRestaurantCandidateService.java`
- 最终 Policy：`src/main/java/com/hmdp/service/MeetPlanPolicyService.java`
- Event/SSE：`src/main/java/com/hmdp/service/MeetPlanEventService.java`
- 数据表：`src/main/resources/db/meetmate-migration.sql`
- AI 单元测试：`src/test/java/com/hmdp/MeetMateAiUnitTest.java`
