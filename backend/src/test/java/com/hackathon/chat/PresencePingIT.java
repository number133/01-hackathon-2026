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
import com.hackathon.chat.presence.PresenceService;
import com.hackathon.chat.presence.PresenceStatus;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
class PresencePingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PresenceService presenceService;

    @Test
    void pingRegistersUserAsOnline() throws Exception {
        Cookie alice = register("pres-a@example.com", "presa", "supersecret");
        UUID aliceId = UUID.fromString(fetchMyId(alice));

        mvc.perform(post("/api/presence/ping")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"tabId\":\"tab-1\"}"))
                .andExpect(status().isNoContent());

        assertThat(presenceService.statusOf(aliceId)).isEqualTo(PresenceStatus.ONLINE);

        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/presence?userIds=" + aliceId + "," + unknown).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(aliceId.toString()))
                .andExpect(jsonPath("$[0].status").value("online"))
                .andExpect(jsonPath("$[1].userId").value(unknown.toString()))
                .andExpect(jsonPath("$[1].status").value("offline"));
    }

    @Test
    void pingWithBlankTabIdRejected() throws Exception {
        Cookie alice = register("pres-b@example.com", "presb", "supersecret");
        mvc.perform(post("/api/presence/ping")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"tabId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configReturnsPingIntervalMs() throws Exception {
        Cookie alice = register("pres-c@example.com", "presc", "supersecret");
        MvcResult r = mvc.perform(get("/api/presence/config").cookie(alice))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("pingIntervalMs").asLong()).isGreaterThan(0);
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

    private String fetchMyId(Cookie session) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }
}
