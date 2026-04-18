package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Seeds 100,000 messages (spec: 3-year-old room history) and walks the
 * history endpoint from newest to oldest, asserting that per-page latency
 * stays flat. Disabled by default — opt in via RUN_LARGE_HISTORY_IT=true.
 * Re-enable condition: run locally or in CI when you want to verify the
 * idx_message_conversation_seq_desc index is still doing its job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_LARGE_HISTORY_IT", matches = "true")
class LargeHistoryScrollIT {

    private static final int TOTAL = 100_000;
    private static final int PAGE = 50;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void walksHistoryBackwardsWithFlatLatency() throws Exception {
        Cookie alice = register("big-alice@ex.com", "bigalice", "supersecret");
        String roomId = createRoom(alice, "big-room");
        UUID conversationId = roomRepository.findById(UUID.fromString(roomId))
                .orElseThrow()
                .getConversationId();
        UUID userId = userRepository.findByUsernameLower("bigalice").orElseThrow().getId();

        bulkInsert(conversationId, userId, TOTAL);

        int seen = 0;
        Long cursor = null;
        long maxPageMillis = 0;
        while (true) {
            long start = System.currentTimeMillis();
            String url = "/api/rooms/" + roomId + "/messages?limit=" + PAGE
                    + (cursor == null ? "" : "&beforeSeq=" + cursor);
            MvcResult r = mvc.perform(get(url).cookie(alice)).andExpect(status().isOk()).andReturn();
            long elapsed = System.currentTimeMillis() - start;
            maxPageMillis = Math.max(maxPageMillis, elapsed);
            JsonNode items = json.readTree(r.getResponse().getContentAsString()).get("items");
            if (items.isEmpty()) break;
            seen += items.size();
            cursor = items.get(items.size() - 1).get("seq").asLong();
        }
        assertThat(seen).isEqualTo(TOTAL);
        assertThat(maxPageMillis).as("max page latency").isLessThan(500);
    }

    private void bulkInsert(UUID conversationId, UUID authorId, int count) throws Exception {
        // Rely on message.created_at DEFAULT now() so we don't have to bind a
        // java.time.Instant (PSQL's setObject refuses it without an explicit
        // SQL type).
        String sql = "INSERT INTO message (id, conversation_id, seq, author_id, body) "
                + "VALUES (gen_random_uuid(), ?, ?, ?, ?)";
        try (var conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (int i = 1; i <= count; i++) {
                ps.setObject(1, conversationId);
                ps.setLong(2, i);
                ps.setObject(3, authorId);
                ps.setString(4, "seed " + i);
                ps.addBatch();
                if (i % 1000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        // Bump the conversation counter to match the seeded messages so
        // subsequent user-posted messages don't collide on seq.
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE conversation SET last_seq = ? WHERE id = ?")) {
            ps.setLong(1, count);
            ps.setObject(2, conversationId);
            ps.executeUpdate();
        }
    }

    private Cookie register(String email, String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new RegistrationRequest(email, username, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
    }

    private String createRoom(Cookie session, String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/rooms")
                        .cookie(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"description\":\"x\",\"visibility\":\"public\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }
}
