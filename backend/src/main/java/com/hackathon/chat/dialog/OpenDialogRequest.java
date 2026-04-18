package com.hackathon.chat.dialog;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenDialogRequest(@NotNull UUID userId) {
}
