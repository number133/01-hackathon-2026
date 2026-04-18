package com.hackathon.chat.presence;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chat.presence")
public record PresenceProperties(
        @NotNull Duration pingInterval,
        @NotNull Duration afkThreshold,
        @NotNull Duration offlineGrace,
        @NotNull Duration sweepInterval) {
}
