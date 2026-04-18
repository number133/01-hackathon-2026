package com.hackathon.chat.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        boolean rememberMe) {
}
