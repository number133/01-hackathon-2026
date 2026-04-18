package com.hackathon.chat.contact;

import java.time.Instant;
import java.util.UUID;

public record UserBanView(UUID userId, String username, Instant createdAt) {
}
