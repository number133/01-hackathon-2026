package com.hackathon.chat.unread;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnreadRepository extends JpaRepository<UnreadMarker, UnreadMarkerId> {

    List<UnreadMarker> findAllByUserId(UUID userId);

    @Query("SELECT m FROM UnreadMarker m WHERE m.userId = :userId AND m.conversationId IN :ids")
    List<UnreadMarker> findAllForUserIn(@Param("userId") UUID userId,
                                        @Param("ids") List<UUID> conversationIds);

    @Query("SELECT m FROM UnreadMarker m "
            + "WHERE m.conversationId = :conversationId AND m.userId IN :userIds")
    List<UnreadMarker> findAllForUserInConversation(
            @Param("conversationId") UUID conversationId,
            @Param("userIds") List<UUID> userIds);
}
