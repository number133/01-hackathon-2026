package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class RoomFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void createJoinPromoteBanDelete() throws Exception {
        Cookie aliceSession = registerAndGetSession("alice1@example.com", "alice1", "supersecret");
        Cookie bobSession = registerAndGetSession("bob1@example.com", "bob1", "supersecret");

        // Alice creates a public room.
        MvcResult created = mvc.perform(post("/api/rooms")
                        .cookie(aliceSession).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(new CreateRoomBody("flow-general", "chat", "public"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String roomId = body.get("id").asText();
        String bobId = fetchMyId(bobSession);

        // Bob sees the room in the catalog.
        mvc.perform(get("/api/rooms").cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='flow-general')]").exists());

        // Bob joins.
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(bobSession).with(csrf()))
                .andExpect(status().isNoContent());

        // Alice promotes Bob.
        mvc.perform(put("/api/rooms/" + roomId + "/admins/" + bobId).cookie(aliceSession).with(csrf()))
                .andExpect(status().isNoContent());

        // Bob (now admin) bans a third user.
        Cookie eveSession = registerAndGetSession("eve1@example.com", "eve1", "supersecret");
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(eveSession).with(csrf()))
                .andExpect(status().isNoContent());
        String eveId = fetchMyId(eveSession);
        mvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(bobSession).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(new BanBody(eveId, "testing"))))
                .andExpect(status().isCreated());

        // Eve can't rejoin.
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(eveSession).with(csrf()))
                .andExpect(status().isForbidden());

        // Alice deletes the room.
        mvc.perform(delete("/api/rooms/" + roomId).cookie(aliceSession).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rooms/" + roomId).cookie(bobSession))
                .andExpect(status().isNotFound());
    }

    private Cookie registerAndGetSession(String email, String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new RegistrationRequest(email, username, password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie s = r.getResponse().getCookie("SESSION");
        assertThat(s).isNotNull();
        return s;
    }

    private String fetchMyId(Cookie session) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private record CreateRoomBody(String name, String description, String visibility) {}
    private record BanBody(String userId, String reason) {}
}
