package com.hackathon.chat.message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByConversationIdAndSeq(UUID conversationId, long seq);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.seq < :beforeSeq
            ORDER BY m.seq DESC
            """)
    List<Message> findHistory(@Param("conversationId") UUID conversationId,
                              @Param("beforeSeq") long beforeSeq,
                              Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
            ORDER BY m.seq DESC
            """)
    List<Message> findLatest(@Param("conversationId") UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);
}
