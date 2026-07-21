# Keep Agent orchestration inside a Java AI Harness

MeetMate keeps planning orchestration, permissions, hard constraints, state transitions, persistence, retries and final validation in the Spring Boot application, while Spring AI supplies structured extraction and request-scoped read-only Tool Calling. This is a deliberate replacement for the earlier Python/LangGraph design: the current MVP needs a bounded planning workflow and human clarification, not a separate checkpointed graph service.

The consequence is that the model may propose and explain, but it cannot modify preferences, relax constraints, confirm a plan, execute SQL, or change room state. `PlanRun`, `PlanAttempt` and `PlanEvent` remain Java-owned business records.
