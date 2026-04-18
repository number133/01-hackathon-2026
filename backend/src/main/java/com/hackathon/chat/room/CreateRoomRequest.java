package com.hackathon.chat.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(min = 1, max = 80) String name,
        @Size(max = 2000) String description,
        @NotBlank @Pattern(regexp = "public|private") String visibility) {
}
