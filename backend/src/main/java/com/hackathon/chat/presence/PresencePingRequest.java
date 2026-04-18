package com.hackathon.chat.presence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PresencePingRequest(
        @NotBlank @Size(max = 64) String tabId) {
}
