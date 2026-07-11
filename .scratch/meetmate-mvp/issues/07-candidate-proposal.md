# 07 — 商铺候选、Proposal 生成与 Java 落库

**What to build:** wow 的收尾——从恢复后的 Run 出发，Java 提供权威候选，Python 选出 2–3 个并给出解释（绝不编造事实），Java 校验并持久化方案。

**Blocked by:** 06 — 冲突澄清、Interrupt 与可靠 Resume。

**Status:** ready-for-agent

**架构边界（即便砍掉评分也必须保留）：Java 独占候选。Python 只返回 candidate_id + 解释；Java 从自己的快照解析 shopId + suggestedTime 并校验 candidateId。**

- [ ] `search_shops` Tool：Python 请求；Java 返回受控候选列表（中心/半径/价格/类型取自 `tb_shop` + `tb_shop_meet_meta`）
- [ ] Java 生成 `candidate_id`；最小 Candidate Snapshot 持久化（run_id、candidate_id、shop_id、suggested_time、facts_json）
- [ ] Python 从候选中选 2–3 个，仅返回 candidate_id + 成员匹配解释（不含 shopId / 分数 / 编造事实）
- [ ] Java 校验 candidate_id（对照快照），持久化 `tb_meet_proposal`（解析 shop）
- [ ] 方案查询接口返回含 shop + 解释的方案列表
- [ ] 验收（Fake LLM）：恢复后的 Run → 2–3 个方案持久化且可查
- [ ] resume 幂等、候选校验、Proposal 落库测试通过
- [ ] **明确不在范围**：复杂评分、投票、重规划、多轮
