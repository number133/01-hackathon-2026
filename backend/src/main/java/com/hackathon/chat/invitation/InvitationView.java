package com.hackathon.chat.invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitationView(
        UUID id,
        UUID roomId,
        String roomName,
        UUID inviterId,
        String inviterUsername,
        UUID inviteeId,
        String inviteeUsername,
        String message,
        String status,
        Instant createdAt,
        Instant resolvedAt) {
}
