package com.hmdp.config;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "meetmate.ai")
public class MeetAiProperties {
    private boolean enabled;
    private String promptVersion = "v2";

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(45);

    @Min(256)
    @Max(8192)
    private int maxCompletionTokens = 1200;

    @Min(0)
    @Max(3)
    private int maxModelRetries = 1;

    @Min(4)
    @Max(20)
    private int maxToolCalls = 8;
}
