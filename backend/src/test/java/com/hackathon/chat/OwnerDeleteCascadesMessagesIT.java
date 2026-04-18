package com.hackathon.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.message.MessageRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OwnerDeleteCascadesMessagesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void messagesVanishWhenOwnerDeletesAccount() throws Exception {
        Cookie alice = register("cas-alice@ex.com", "casalice", "supersecret");
        String roomId = createRoom(alice, "cas-room");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/rooms/" + roomId + "/messages")
                            .cookie(alice).with(csrf())
                            .contentType("application/json")
                            .content("{\"text\":\"msg " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        MvcResult hist = mvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(alice))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andReturn();
        UUID conversationId = UUID.fromString(json.readTree(hist.getResponse().getContentAsString())
                .get("items").get(0).get("conversationId").asText());
        assertThat(messageRepository.countByConversationId(conversationId)).isEqualTo(5);

        mvc.perform(delete("/api/account").cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"password\":\"supersecret\"}"))
                .andExpect(status().isNoContent());

        assertThat(messageRepository.countByConversationId(conversationId)).isZero();
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
