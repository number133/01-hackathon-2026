package com.hackathon.chat.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, UUID> {

    Optional<RoomInvitation> findByRoomIdAndInviteeUserIdAndStatus(
            UUID roomId, UUID inviteeUserId, String status);

    List<RoomInvitation> findAllByInviteeUserIdAndStatus(UUID inviteeUserId, String status);

    List<RoomInvitation> findAllByRoomIdAndStatus(UUID roomId, String status);
}
