package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.chat.auth.LoginRequest;
import com.hackathon.chat.auth.RegistrationRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void registerLoginLogoutRoundtrip() throws Exception {
        RegistrationRequest register = new RegistrationRequest("alice@example.com", "alice", "supersecret");

        MvcResult registerResult = mvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andReturn();

        Cookie session = registerResult.getResponse().getCookie("SESSION");
        assertThat(session).as("SESSION cookie should be set after register").isNotNull();

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        RegistrationRequest first = new RegistrationRequest("bob@example.com", "bob", "supersecret");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(first)))
                .andExpect(status().isOk());

        RegistrationRequest second = new RegistrationRequest("bob@example.com", "bob2", "anothersecret");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        RegistrationRequest register = new RegistrationRequest("carol@example.com", "carol", "supersecret");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(register)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest("carol@example.com", "wrong", false);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }
}
