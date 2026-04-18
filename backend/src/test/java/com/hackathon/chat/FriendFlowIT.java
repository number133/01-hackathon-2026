package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class FriendFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void requestAcceptUnfriend() throws Exception {
        Cookie alice = register("a-fa@example.com", "afa", "supersecret");
        Cookie bob = register("a-fb@example.com", "bfa", "supersecret");

        MvcResult create = mvc.perform(post("/api/friend-requests")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"bfa\",\"message\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String reqId = json.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/friend-requests?direction=incoming").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reqId))
                .andExpect(jsonPath("$[0].status").value("pending"));

        mvc.perform(post("/api/friend-requests/" + reqId + "/accept")
                        .cookie(bob).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/friends").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bfa"));
        mvc.perform(get("/api/friends").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("afa"));

        String bobId = fetchMyId(bob);
        mvc.perform(delete("/api/friends/" + bobId).cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/friends").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void mutualPendingRejected() throws Exception {
        Cookie alice = register("mp-a@example.com", "mpa", "supersecret");
        Cookie bob = register("mp-b@example.com", "mpb", "supersecret");

        mvc.perform(post("/api/friend-requests")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"mpb\"}"))
                .andExpect(status().isCreated());

        MvcResult reject = mvc.perform(post("/api/friend-requests")
                        .cookie(bob).with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"mpa\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        JsonNode body = json.readTree(reject.getResponse().getContentAsString());
        assertThat(body.get("message").asText()).contains("Incoming request");
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
