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
class KickEqualsBanIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void kickRecordsBanUnbanAllowsRejoin() throws Exception {
        Cookie admin = register("kick-a@example.com", "kicka", "supersecret");
        Cookie victim = register("kick-v@example.com", "kickv", "supersecret");

        String roomId = createRoom(admin, "kick-room", "public");
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(victim).with(csrf()))
                .andExpect(status().isNoContent());
        String victimId = fetchMyId(victim);

        // Kick.
        mvc.perform(delete("/api/rooms/" + roomId + "/members/" + victimId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());

        // Ban list shows the victim.
        mvc.perform(get("/api/rooms/" + roomId + "/bans").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("kickv"));

        // Victim re-join → 403.
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(victim).with(csrf()))
                .andExpect(status().isForbidden());

        // Unban.
        mvc.perform(delete("/api/rooms/" + roomId + "/bans/" + victimId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());

        // Victim re-join now OK.
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(victim).with(csrf()))
                .andExpect(status().isNoContent());
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

    private String fetchMyId(Cookie session) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }
}
