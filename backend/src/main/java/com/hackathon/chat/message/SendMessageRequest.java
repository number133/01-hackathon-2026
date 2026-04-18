package com.hackathon.chat.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendMessageRequest(
        @NotBlank @Size(max = 3072) String text,
        UUID replyToId) {
}
