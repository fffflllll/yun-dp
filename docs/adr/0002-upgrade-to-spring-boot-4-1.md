# Upgrade to Spring Boot 4.1 for Spring AI 2.0

MeetMate upgrades the Java application from Spring Boot 3.4.4 to 4.1.0 before enabling Spring AI 2.0. The upgrade is paired with MyBatis-Plus `mybatis-plus-spring-boot4-starter:3.5.17` and Redisson `4.6.1`, while Java 17 remains the runtime baseline; keeping the framework migration separate from Agent behavior makes compatibility failures diagnosable.
