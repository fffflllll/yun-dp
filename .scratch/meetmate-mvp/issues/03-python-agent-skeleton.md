# 03 — Python Agent 骨架与偏好解析 Graph

**What to build:** 一个可运行的 FastAPI + LangGraph agent-service，能用真实 LLM 解析自然语言聚餐偏好，并以 Fake LLM 作为 dev/CI 默认。这是第一条纵切的 Agent 半边。

**Blocked by:** 01 — 基线修复与工程骨架。

**Status:** ready-for-agent

**验收分两档（真实 LLM 不得作为自动验收唯一依据）：**

必须通过（Fake LLM）：
- [ ] FastAPI 启动；`/health` ready/live 返回 200
- [ ] LangGraph + Pydantic 接好；LlmProvider 抽象，env 选择供应商
- [ ] **Fake LLM 是 dev/CI 默认**；真实 LLM 经 `LLM_PROVIDER` env 切换（仅手工 smoke）
- [ ] Preference Parse Graph 实现：抽取时间/预算/菜系/忌口/过敏/HARD·SOFT，schema 校验，失败修复（最多 2 次）
- [ ] `POST /internal/agent/preferences/parse` 实现并返回 `ParsedPreference`
- [ ] 独立可 demo：curl 解析端点即出结构化偏好（Fake LLM，确定性）
- [ ] Graph + Schema + Fake-LLM 单测通过（CI 不烧 LLM）

人工 Smoke（真实 LLM）：
- [ ] 解析 3–5 条中文偏好，人工确认结果基本合理
