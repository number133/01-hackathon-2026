package com.hackathon.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.chat.auth.RegistrationRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UnreadFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void bumpOnPostAndClearOnMarkRead() throws Exception {
        Cookie alice = register("unr-a@example.com", "unra", "supersecret");
        Cookie bob = register("unr-b@example.com", "unrb", "supersecret");

        String roomId = createRoom(alice, "unr-room");
        String convId = conversationOf(roomId, alice);
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(bob).with(csrf()))
                .andExpect(status().isNoContent());

        // Baseline: no unread for either.
        expectUnread(alice, convId, 0L);
        expectUnread(bob, convId, 0L);

        // Alice posts.
        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"hi\"}"))
                .andExpect(status().isCreated());

        // Alice's own marker advanced (no self-bump).
        expectUnread(alice, convId, 0L);
        // Bob sees 1.
        expectUnread(bob, convId, 1L);

        // Alice posts again.
        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"hi again\"}"))
                .andExpect(status().isCreated());

        expectUnread(bob, convId, 2L);

        // Bob marks read at seq=2.
        mvc.perform(post("/api/conversations/" + convId + "/read")
                        .cookie(bob).with(csrf())
                        .contentType("application/json")
                        .content("{\"seq\":2}"))
                .andExpect(status().isNoContent());

        expectUnread(bob, convId, 0L);

        // Mark-read is monotonic: lower seq ignored.
        mvc.perform(post("/api/conversations/" + convId + "/read")
                        .cookie(bob).with(csrf())
                        .contentType("application/json")
                        .content("{\"seq\":0}"))
                .andExpect(status().isNoContent());
        expectUnread(bob, convId, 0L);

        // Mark-read clamps to last_seq (posting huge value doesn't break).
        mvc.perform(post("/api/conversations/" + convId + "/read")
                        .cookie(bob).with(csrf())
                        .contentType("application/json")
                        .content("{\"seq\":999999}"))
                .andExpect(status().isNoContent());
        expectUnread(bob, convId, 0L);

        // Non-participant is 403.
        Cookie carol = register("unr-c@example.com", "unrc", "supersecret");
        mvc.perform(post("/api/conversations/" + convId + "/read")
                        .cookie(carol).with(csrf())
                        .contentType("application/json")
                        .content("{\"seq\":1}"))
                .andExpect(status().isForbidden());
    }

    private void expectUnread(Cookie session, String convId, long expected) throws Exception {
        MvcResult r = mvc.perform(get("/api/unread").cookie(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        long actual = -1;
        for (JsonNode item : body) {
            if (item.get("conversationId").asText().equals(convId)) {
                actual = item.get("count").asLong();
            }
        }
        if (actual != expected) {
            throw new AssertionError("expected unread=" + expected + " for " + convId
                    + " but saw " + actual + " in " + body);
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
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String conversationOf(String roomId, Cookie session) throws Exception {
        MvcResult r = mvc.perform(get("/api/rooms/" + roomId).cookie(session))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("conversationId").asText();
    }
}
