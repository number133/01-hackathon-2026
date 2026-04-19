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
    void listMineReturnsJoinedRoomsIncludingPrivate() throws Exception {
        Cookie aliceSession = registerAndGetSession("mine-a@example.com", "minea", "supersecret");
        Cookie bobSession = registerAndGetSession("mine-b@example.com", "mineb", "supersecret");
        Cookie eveSession = registerAndGetSession("mine-e@example.com", "minee", "supersecret");

        // Alice owns one public room and one private room.
        String publicId = createRoom(aliceSession, "mine-public", "x", "public");
        String privateId = createRoom(aliceSession, "mine-private", "x", "private");

        // Bob joins only the public room.
        mvc.perform(post("/api/rooms/" + publicId + "/join").cookie(bobSession).with(csrf()))
                .andExpect(status().isNoContent());

        // /mine for Alice contains both — sorted by name (case-insensitive).
        MvcResult aliceMine = mvc.perform(get("/api/rooms/mine").cookie(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode aliceRows = json.readTree(aliceMine.getResponse().getContentAsString());
        assertThat(aliceRows.get(0).get("name").asText()).isEqualTo("mine-private");
        assertThat(aliceRows.get(1).get("name").asText()).isEqualTo("mine-public");
        // Both rows carry the viewer's role.
        assertThat(aliceRows.get(0).get("myRole").asText()).isEqualTo("owner");
        assertThat(aliceRows.get(1).get("myRole").asText()).isEqualTo("owner");

        // /mine for Bob has only the public room.
        mvc.perform(get("/api/rooms/mine").cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("mine-public"))
                .andExpect(jsonPath("$[0].myRole").value("member"));

        // Private room is NOT leaked to Bob — he is not a member.
        mvc.perform(get("/api/rooms/mine").cookie(bobSession))
                .andExpect(jsonPath("$[?(@.name=='mine-private')]").doesNotExist());

        // /mine for Eve (no memberships) is empty.
        mvc.perform(get("/api/rooms/mine").cookie(eveSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // /mine requires authentication.
        mvc.perform(get("/api/rooms/mine"))
                .andExpect(status().isUnauthorized());
    }

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

    private String createRoom(Cookie session, String name, String description, String visibility) throws Exception {
        MvcResult created = mvc.perform(post("/api/rooms")
                        .cookie(session).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(new CreateRoomBody(name, description, visibility))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(created.getResponse().getContentAsString()).get("id").asText();
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
