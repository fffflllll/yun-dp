# MeetMate — 术语表（GLOSSARY）

> grill-with-docs 会话沉淀的共享词汇，避免同词异义。

- **HITL（Human-in-the-Loop）**：人类在环。Agent 遇到偏好冲突或信息缺失时不瞎编，而是挂起、请人回答、再继续。本项目的核心 wow。
- **澄清（Clarification）**：Agent 判断信息不足/冲突时，请 Java 落一条 `tb_meet_clarification` 记录并把 AgentRun 切到 `WAITING_INPUT`，等用户作答。
- **挂起/恢复（interrupt / resume）**：LangGraph 的中断机制。Python 用 `interrupt` 挂起图，Java 收到用户答案后调 `resume`，Python 用 `Command(resume=...)` 从原节点继续。恢复幂等键 = `clarificationId`。
- **系统-of-record（System of Record）**：唯一业务事实来源 = Java。评分、状态、投票、最终方案都由 Java 独占；Python 无 DB、只做语义。
- **可替换语义层**：Python Agent 整体可换（原 A1 主张）。**已在 D3 降级**：不再是项目目标，不为它做证明。
- **纵切（Vertical Slice）**：一条打通所有层的最薄端到端路径。本项目 MVP 纵切见 CONTEXT D7。
- **wow**：简历/Demo 第一眼要让人记住的点。本项目 = 会主动问人的多人在环 Agent。
- **hmdp / yun-dp**：黑马点评教学项目，本项目的业务底座（GitHub: fffflllll/yun-dp）。
