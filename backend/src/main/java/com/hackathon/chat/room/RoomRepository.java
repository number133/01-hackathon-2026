package com.hackathon.chat.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    long countByOwnerId(UUID ownerId);

    List<Room> findAllByOwnerId(UUID ownerId);

    Optional<Room> findByIdAndVisibility(UUID id, String visibility);

    Optional<Room> findByConversationId(UUID conversationId);

    @Query("""
            SELECT r FROM Room r
            WHERE r.visibility = 'public'
              AND (LOWER(r.name) LIKE :pattern OR LOWER(r.description) LIKE :pattern)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Room> searchPublic(@Param("pattern") String pattern, Pageable pageable);
}
