package com.hackathon.chat.conversation;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConversationService {

    private final ConversationRepository repository;
    private final JdbcTemplate jdbc;

    public ConversationService(ConversationRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public Conversation create(String type) {
        return repository.save(new Conversation(type));
    }

    public void deleteConversation(UUID conversationId) {
        repository.deleteById(conversationId);
    }

    /**
     * Atomic: UPDATE ... RETURNING holds a row-level lock across the read-
     * increment-write pair for this conversation, so concurrent posters
     * are serialised by Postgres regardless of JVM threads.
     */
    public long assignNextSeq(UUID conversationId) {
        Long value = jdbc.queryForObject(
                "UPDATE conversation SET last_seq = last_seq + 1 "
                        + "WHERE id = ? RETURNING last_seq",
                Long.class, conversationId);
        if (value == null) {
            throw new NoSuchElementException("Conversation not found");
        }
        return value;
    }

    @Transactional(readOnly = true)
    public Conversation require(UUID id) {
        return repository.findById(id).orElseThrow(
                () -> new NoSuchElementException("Conversation not found"));
    }
}
