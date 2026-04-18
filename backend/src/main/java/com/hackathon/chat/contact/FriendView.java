package com.hackathon.chat.contact;

import java.time.Instant;
import java.util.UUID;

public record FriendView(UUID userId, String username, Instant establishedAt) {
}
