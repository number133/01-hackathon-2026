package com.hackathon.chat.attachment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findAllByMessageIdIn(Collection<UUID> messageIds);

    List<Attachment> findAllByConversationId(UUID conversationId);

    @Query("SELECT a FROM Attachment a "
            + "WHERE a.messageId IS NULL AND a.createdAt < :cutoff")
    List<Attachment> findOrphansOlderThan(@Param("cutoff") Instant cutoff);
}
