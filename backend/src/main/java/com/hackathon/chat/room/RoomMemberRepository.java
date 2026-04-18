package com.hackathon.chat.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomMember> findAllByRoomId(UUID roomId);

    List<RoomMember> findAllByUserId(UUID userId);

    long countByRoomId(UUID roomId);

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    @Modifying
    @Query("DELETE FROM RoomMember m WHERE m.roomId = :roomId AND m.userId = :userId")
    void deleteByRoomIdAndUserId(@Param("roomId") UUID roomId, @Param("userId") UUID userId);
}
