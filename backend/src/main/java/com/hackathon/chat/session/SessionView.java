package com.hackathon.chat.session;

import java.time.Instant;

public record SessionView(
        String sessionId,
        Instant createdAt,
        Instant lastAccessedAt,
        String ip,
        String userAgent,
        boolean current) {
}
