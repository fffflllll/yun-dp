# 05 — AgentRun 启动与房间上下文 Tool

**What to build:** 启动一个规划 AgentRun、并让 Python 图从 Java 读取房间上下文的机制。把 Run 推到"冲突检查"节点为止、且进度可查——但暂不处理澄清与方案。

**Blocked by:** 04 — Java 偏好采集纵切。

**Status:** ready-for-agent

- [ ] `tb_meet_agent_run` 建表（run_id、room_id、round_no、run_type=PLAN、status、snapshot）
- [ ] 同一房间至多一个活跃 PLAN Run（启动幂等）
- [ ] 可靠的 AgentRun 启动投递（ResumeDispatcher 风格；复用 `SeckillVoucherListener` 的 RabbitMQ QueueConfig 模式）
- [ ] Java "room context" 内部 Tool 实现（`GET /internal/agent-tools/rooms/{roomId}/context`）：成员、已确认偏好、搜索区域、日期选项、已否决商铺
- [ ] Python Planning Graph 启动，调用 room-context Tool，归一化偏好
- [ ] Python → Java 进度回调持久化且可查（RUNNING 阶段）
- [ ] 验收（Fake LLM）：创建 PLAN Run → Python 读到 A/D 偏好 → Run 进度可查；到达冲突检查节点
- [ ] AgentRun 状态机 + Tool API + interrupt 就绪测试通过
