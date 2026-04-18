package com.hackathon.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OwnerCascadeIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void deletingAccountRemovesOwnedRoomsOnly() throws Exception {
        Cookie alice = register("cascade-a@example.com", "cascadea", "supersecret");
        Cookie bob = register("cascade-b@example.com", "cascadeb", "supersecret");

        String aliceOwned = createRoom(alice, "cascade-owned", "public");
        String bobOwned = createRoom(bob, "cascade-bob-room", "public");

        // Alice joins bob's room.
        mvc.perform(post("/api/rooms/" + bobOwned + "/join").cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());

        // Alice deletes her account.
        mvc.perform(delete("/api/account")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"password\":\"supersecret\"}"))
                .andExpect(status().isNoContent());

        // Bob's room still exists.
        mvc.perform(get("/api/rooms/" + bobOwned).cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("cascade-bob-room"));

        // Alice's room is gone.
        mvc.perform(get("/api/rooms/" + aliceOwned).cookie(bob))
                .andExpect(status().isNotFound());
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
