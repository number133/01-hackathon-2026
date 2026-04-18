package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;
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
class PrivateRoomInviteIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void privateRoomHiddenFromCatalogAndRequiresInvite() throws Exception {
        Cookie aliceSession = register("alice2@example.com", "alice2", "supersecret");
        Cookie bobSession = register("bob2@example.com", "bob2", "supersecret");

        String roomId = createRoom(aliceSession, "inv-secret", "private");

        // Bob does not see the private room in the catalog.
        mvc.perform(get("/api/rooms").cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='inv-secret')]").doesNotExist());

        // Bob cannot even GET by id.
        mvc.perform(get("/api/rooms/" + roomId).cookie(bobSession))
                .andExpect(status().isNotFound());

        // Alice invites Bob by username.
        mvc.perform(post("/api/rooms/" + roomId + "/invitations")
                        .cookie(aliceSession).with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"bob2\"}"))
                .andExpect(status().isCreated());

        // Bob now sees one pending invite.
        MvcResult list = mvc.perform(get("/api/invitations").cookie(bobSession))
                .andExpect(status().isOk()).andReturn();
        JsonNode arr = json.readTree(list.getResponse().getContentAsString());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(1);
        String invId = arr.get(0).get("id").asText();

        // Bob accepts.
        mvc.perform(post("/api/invitations/" + invId + "/accept").cookie(bobSession).with(csrf()))
                .andExpect(status().isNoContent());

        // Bob can now see the private room.
        mvc.perform(get("/api/rooms/" + roomId).cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("private"));
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

    private String createRoom(Cookie session, String name, String visibility) throws Exception {
        MvcResult r = mvc.perform(post("/api/rooms")
                        .cookie(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"description\":\"x\",\"visibility\":\"" + visibility + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }
}
