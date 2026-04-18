package com.hackathon.chat.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(min = 3, max = 40)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "letters, digits, dot, underscore, dash only")
        String username,
        @NotBlank @Size(min = 8, max = 200) String password) {
}
