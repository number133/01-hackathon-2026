package com.hackathon.chat.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(
        @NotBlank @Size(max = 40) String username,
        @Size(max = 500) String message) {
}
