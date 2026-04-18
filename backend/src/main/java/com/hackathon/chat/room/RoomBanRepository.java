package com.hackathon.chat.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomBanRepository extends JpaRepository<RoomBan, RoomBanId> {

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    Optional<RoomBan> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomBan> findAllByRoomId(UUID roomId);
}
