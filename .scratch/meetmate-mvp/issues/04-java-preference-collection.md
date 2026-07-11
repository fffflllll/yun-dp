# 04 — Java 偏好采集纵切

**What to build:** 第一条完整端到端纵切：成员的自然语言偏好走 文本 → Java → Python → Java → 结构化结果，并且可被确认。证明 Java↔Python 契约打通。本工单是独立 tracer bullet，**不要并入规划工单**。

**Blocked by:** 02 — 房间/成员/商铺元数据；03 — Python Agent 骨架与解析图。

**Status:** ready-for-agent

- [ ] `tb_meet_preference` 建表（room_id、user_id、raw_text、parsed_json、confirmed）
- [ ] Java 接口接收 NL 偏好，持久化原文，调用 Python `/internal/agent/preferences/parse`
- [ ] 解析结果作为草稿持久化；接口返回结构化 `ParsedPreference`
- [ ] 最小用户确认流程（确认/覆盖解析结果）
- [ ] Java/Python 偏好解析契约测试通过（字段/枚举/签名）
- [ ] 演示纵切：提交"A：周六19点后、预算100、吃火锅" → 可见结构化解析

**首条纵切闭环：** 用户文本 → Java → Python → Java → 查询结果
