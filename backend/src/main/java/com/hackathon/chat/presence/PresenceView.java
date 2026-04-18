package com.hackathon.chat.presence;

import java.util.UUID;

public record PresenceView(UUID userId, PresenceStatus status) {
}
