package com.hackathon.chat.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendFriendRequestRequest(
        @NotBlank @Size(max = 40) String username,
        @Size(max = 500) String message) {
}
