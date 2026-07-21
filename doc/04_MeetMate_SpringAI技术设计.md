# MeetMate Spring AI 2.0 技术设计

## 目标

MeetMate MVP 在现有 Java 单体中实现多人餐厅聚会规划。Java 是业务事实来源，Spring AI 只负责偏好语义提取、冲突问题措辞和候选方案组织。

## 运行链路

```text
成员自然语言偏好
  -> 房间成员可通过持久化消息协作
  -> Spring AI 结构化提取
  -> 成员本人确认
  -> 房主锁定成员名单
  -> 全员确认偏好
  -> PlanRun / PlanAttempt 投递 RabbitMQ
  -> Java 创建受控 Agent 执行计划
  -> Spring AI 通过 Tool Calling 读取偏好、召回候选并校验草稿
  -> Agent 生成 1 首选 + 2 备选
  -> Java 校验候选 ID、硬约束和字段完整性
  -> PlanEvent 持久化并通过 SSE 推送
  -> 房主确认一项方案
```

## 配置

```yaml
spring:
  ai:
    openai:
      api-key: ${MEETMATE_AI_API_KEY:}
      base-url: ${MEETMATE_AI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${MEETMATE_AI_MODEL:gpt-4.1-mini}
          temperature: 0.2

meetmate:
  ai:
    enabled: ${MEETMATE_AI_ENABLED:false}
    prompt-version: v2
```

`base-url` 可指向 OpenAI、DeepSeek 或其他 OpenAI 兼容服务。真实 API Key 只通过部署环境注入，测试使用 Fake ChatModel 或关闭真实调用。

## Agent 架构

本项目采用 **Java 编排的 Plan-and-Execute + 受控 ReAct Tool Calling**，不采用完全自治的无限 ReAct 循环。

```text
MeetPlanExecutionService（业务状态机）
  -> MeetPlanningAgent（受控执行计划）
       -> Java 静默预检：候选是否足够
       -> ChatClient + ToolCallingAdvisor
            -> read_confirmed_preferences
            -> find_feasible_restaurants
            -> inspect_restaurant_candidates
            -> validate_plan_draft
       -> MeetPlanPolicyService 最终复核
  -> 持久化 Proposal / PlanEvent / Run 状态
```

选择混合模式的原因：

- 房间状态、权限、硬约束和持久化是确定性业务逻辑，不能交给模型决定。
- 模型适合在已确认事实内比较候选、解释群体取舍并组织三个方案。
- 工具绑定当前 `roomId/runId/attemptId`，模型不能传入任意房间 ID 越权读取。
- 工具按单次请求通过 `.tools(...)` 注入，不注册为所有模型请求共享的默认工具。
- AI 模式下必须成功完成四个必要工具调用，且最终选择的三个餐厅都必须经过详情工具查看；缺少调用时该 Attempt 明确失败。
- 关闭 AI 时，同一套工具和校验策略执行确定性降级方案，便于本地开发和测试。

## 工具权限

| 工具 | 能力 | 是否写业务数据 |
|---|---|---|
| `read_confirmed_preferences` | 读取当前房间已确认偏好 | 否 |
| `find_feasible_restaurants` | Java 硬约束过滤与排序 | 否 |
| `inspect_restaurant_candidates` | 查看最多 8 家候选详情 | 否 |
| `validate_plan_draft` | 校验三个方案是否引用合法候选 | 否 |

不会提供 `confirm_plan`、`relax_constraint`、`update_preference` 等写工具。此类操作必须由有权限的用户发起，并由普通 Java 业务服务处理。

## 可解释轨迹

SSE 增加 `AGENT_PLAN_CREATED`、`TOOL_STARTED`、`TOOL_COMPLETED`、`TOOL_FAILED` 和 `PLAN_REVIEWED`。前端展示的是工具名称、数量和校验摘要，不展示 Prompt、Token 或模型原始思维链。

## 可靠性边界

- RabbitMQ 只投递 `runId + attemptId`，不承载业务事实。
- MySQL 保存 `PlanRun`、`PlanAttempt`、`PlanEvent`、澄清和方案。
- MySQL 保存 `MeetMessage`；消息只允许已加入成员读取和发送，前端用消息 ID 增量轮询。
- SSE 事件带单调 `sequence`，断线用 `Last-Event-ID` 补发。
- 模型输出必须解析为结构化 DTO；餐厅必须属于 Java 候选集。
- 模型只能请求工具，Java 才是工具的实际执行者和权限边界。
- 不展示模型 token、Prompt 或原始思维链，只展示固定阶段和脱敏决策摘要。
- 没有可行候选时最多发起一次定向澄清，回答后创建新的 Attempt。

## 当前接口

- `GET /api/meet/rooms/{roomId}/messages?after={messageId}`
- `POST /api/meet/rooms/{roomId}/messages`
- `POST /api/meet/rooms/{roomId}/preferences/parse`
- `PUT /api/meet/rooms/{roomId}/preferences/confirm`
- `POST /api/meet/rooms/{roomId}/lock-members`
- `POST /api/meet/rooms/{roomId}/plan-runs`
- `GET /api/meet/plan-runs/{runId}`
- `GET /api/meet/plan-runs/{runId}/events`
- `POST /api/meet/plan-runs/{runId}/clarifications/{clarificationId}/answer`
- `POST /api/meet/plan-runs/{runId}/confirm`
