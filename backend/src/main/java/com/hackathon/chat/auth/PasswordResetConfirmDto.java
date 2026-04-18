package com.hackathon.chat.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 200) String newPassword) {
}
