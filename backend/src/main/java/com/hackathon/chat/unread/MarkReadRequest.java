package com.hackathon.chat.unread;

import jakarta.validation.constraints.PositiveOrZero;

public record MarkReadRequest(@PositiveOrZero long seq) {
}
