# 08 — Vue3 极简演示页 + 全链路回归

**What to build:** 一个极简 Vue3（TypeScript + Vite）演示前端，live 走通整个 MVP，外加全链路测试/CI 装配。02 后即可开工，但最终验收依赖 07。

**Blocked by:** 02 — 房间/成员/商铺元数据（可提前开工）；最终验收依赖 07 — 商铺候选/Proposal。

**Status:** ready-for-agent

**分阶段接入：**
- [ ] `web/` 脚手架（Vue3 + TS + Vite）；`MeetDemoPage.vue` + `api/meet.ts` + `router/index.ts`（复用现有登录/token；**不要**再建一个 Vue2 页面）
- [ ] 02 后：创建房间 + 加 A/D
- [ ] 04 后：提交偏好，看结构化解析
- [ ] 06 后：展示澄清问题，回答它
- [ ] 07 后：展示最终方案
- [ ] Fake-LLM 全链路 E2E 通过（CI 不烧 LLM）
- [ ] Java/Python 契约漂移检查通过
- [ ] CI 编排：`mvn test` + `pytest`(Fake LLM) + E2E
- [ ] 真实 LLM 人工演示脚本成文（`LLM_PROVIDER=real`；解析 3–5 条偏好；人工确认合理）
