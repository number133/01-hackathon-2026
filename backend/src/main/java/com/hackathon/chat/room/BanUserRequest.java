package com.hackathon.chat.room;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BanUserRequest(
        @NotNull UUID userId,
        @Size(max = 200) String reason) {
}
