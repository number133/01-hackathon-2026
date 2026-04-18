package com.hackathon.chat.dialog;

import java.time.Instant;
import java.util.UUID;

public record DialogView(
        UUID id,
        UUID counterpartId,
        String counterpartUsername,
        boolean frozen,
        Instant lastMessageAt) {
}
