package com.hackathon.chat.attachment;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chat.attachment")
public record AttachmentProperties(
        @NotNull DataSize maxSize,
        @NotNull DataSize maxImageSize,
        @NotNull Duration orphanTtl,
        @NotNull Duration sweepInterval,
        @NotNull String storageRoot,
        List<String> blockedMimePrefixes,
        int maxPerMessage) {
}
