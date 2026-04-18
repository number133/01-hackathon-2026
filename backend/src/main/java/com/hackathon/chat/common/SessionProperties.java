package com.hackathon.chat.common;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chat.session")
public record SessionProperties(
        @NotNull Duration shortTtl,
        @NotNull Duration longTtl) {
}
