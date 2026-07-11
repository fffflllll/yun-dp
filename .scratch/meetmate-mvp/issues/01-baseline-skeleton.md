# 01 — 基线修复与 MeetMate 工程骨架

**What to build:** 把现有 hmdp 代码收拾成可重复、可增量构建 MeetMate 的干净状态，且不改变任何业务行为。修复已知 P0 问题，建立 meet 包与迁移纪律，对齐 Java/Python 约定，并打通最小 Docker 网络，让未来的 Python Agent 与 Vue3 web 能接入。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `mvn test` 在现有 hmdp 功能上通过
- [ ] 现有 hmdp 功能仍可启动（Java + MySQL + Redis + RabbitMQ via docker compose）
- [ ] P0 基线修复：ThreadLocal 清理；删除 MQ 消费者测试异常；修正商铺写接口权限；固定 Java 容器内部端口
- [ ] 建立 `com.hmdp.meet` 空包
- [ ] 建立数据库迁移目录；迁移可重复执行（幂等）
- [ ] 创建 `agent-service/` 与 `web/` 目录占位
- [ ] Docker Compose 网络允许 agent-service / web 访问 Java（host-gateway / extra_hosts）
- [ ] 统一约定落地：Java↔Python JSON 命名风格、ID 生成（复用 RedisIdWorker）、时间格式（UTC+8 / ISO）、统一错误响应信封
- [ ] `agent-service/.env.example` 存在（LLM_PROVIDER、key、JAVA_TOOL_BASE_URL 等）
