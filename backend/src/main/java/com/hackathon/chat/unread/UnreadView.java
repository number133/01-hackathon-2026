package com.hackathon.chat.unread;

import java.util.UUID;

public record UnreadView(UUID conversationId, long count) {
}
