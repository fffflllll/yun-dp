# 06 — 冲突澄清、Interrupt 与可靠 Resume

**What to build:** 核心 wow——当两名成员持有不可调和的 HARD 约束时，Agent 挂起、提出结构化澄清、并在人类回答后可靠恢复。本工单在"成功恢复"处停住（商铺搜索之前），保持单张工单的凝聚性。

**Blocked by:** 05 — AgentRun 启动与房间上下文 Tool。

**Status:** ready-for-agent

**关键业务规则（来自已冻结设计）：澄清仅在"冲突的 HARD 约束"时触发。**
- 不触发示例：A 吃火锅 SOFT，D 不吃火锅 HARD → 系统直接淘汰火锅候选，不澄清。
- 必须触发示例：A 必须吃火锅 HARD，D 完全不能接受火锅 HARD。

- [ ] `tb_meet_clarification` 建表（clarification_id、run_id、question_code、target_type=OWNER、answer_type、options、status）
- [ ] `request_clarification` Tool：Python 请 Java 持久化澄清 + 置 AgentRun=WAITING_INPUT
- [ ] 发出结构化问题，例如：
  ```json
  {
    "questionCode": "CONFLICTING_HARD_FOOD_CATEGORY",
    "targetType": "OWNER",
    "answerType": "SINGLE_CHOICE",
    "options": [
      {"value": "ASK_A_RELAX", "label": "询问 A 是否放宽"},
      {"value": "ASK_D_RELAX", "label": "询问 D 是否放宽"},
      {"value": "CANCEL_PLAN", "label": "暂不继续规划"}
    ]
  }
  ```
- [ ] LangGraph `interrupt` 挂起图；`clarificationId` 作为 resume 幂等键保存
- [ ] Java 澄清查询 + 回答接口；ResumeDispatcher 调 Python `POST /internal/agent/runs/{runId}/resume`
- [ ] Python `Command(resume=...)` 从中断点恢复，**仅消费 Java 已校验的答案**
- [ ] 幂等：重复 resume（同一 clarificationId）返回原状态，不重复执行节点
- [ ] 验收（Fake LLM）：双 HARD 冲突 → Run 挂起并落一条澄清 → 回答后 Graph 成功恢复，推进到 **search_shops 之前的节点**
- [ ] 澄清 + interrupt + resume 幂等测试通过
