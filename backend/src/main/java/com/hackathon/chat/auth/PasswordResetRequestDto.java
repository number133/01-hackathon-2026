package com.hackathon.chat.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestDto(@NotBlank @Email String email) {
}
