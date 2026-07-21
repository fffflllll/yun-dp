# Use Spring AI in the Java monolith

MeetMate MVP will use Spring AI 2.0 inside the existing Spring Boot application instead of a separate Python and LangGraph service. The current workflow needs a bounded planning invocation after members confirm preferences, so keeping the business state, model orchestration, validation, and persistence in one Java deployment reduces delivery risk while still demonstrating a production-shaped AI integration; durable LangGraph interrupt/resume semantics are deferred unless a later requirement proves they are necessary.
