package com.hackathon.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class MessagingFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void postEditDeleteHistory() throws Exception {
        Cookie alice = register("m-alice@ex.com", "malice", "supersecret");
        Cookie bob = register("m-bob@ex.com", "mbob", "supersecret");

        String roomId = createRoom(alice, "m-general");
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(bob).with(csrf()))
                .andExpect(status().isNoContent());

        // Post three messages as Alice
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/rooms/" + roomId + "/messages")
                            .cookie(alice).with(csrf())
                            .contentType("application/json")
                            .content("{\"text\":\"hello " + i + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seq").value(i))
                    .andExpect(jsonPath("$.body").value("hello " + i));
        }

        // Bob fetches history — newest first.
        mvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seq").value(3))
                .andExpect(jsonPath("$.items[2].seq").value(1));

        // Alice edits message seq=2
        MvcResult hist = mvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(alice))
                .andReturn();
        String targetId = json.readTree(hist.getResponse().getContentAsString())
                .get("items").get(1).get("id").asText();

        mvc.perform(patch("/api/messages/" + targetId).cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"hello edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("hello edited"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());

        // Alice deletes message seq=3
        String topId = json.readTree(hist.getResponse().getContentAsString())
                .get("items").get(0).get("id").asText();
        mvc.perform(delete("/api/messages/" + topId).cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seq").value(3))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].deletedAt").isNotEmpty());
    }

    @Test
    void nonMemberCannotReadHistory() throws Exception {
        Cookie alice = register("m-alice2@ex.com", "malice2", "supersecret");
        Cookie bob = register("m-bob2@ex.com", "mbob2", "supersecret");
        String roomId = createRoom(alice, "m-alice-only");

        mvc.perform(get("/api/rooms/" + roomId + "/messages").cookie(bob))
                .andExpect(status().isForbidden());
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        Cookie alice = register("m-alice3@ex.com", "malice3", "supersecret");
        String roomId = createRoom(alice, "m-big");

        String oversize = "a".repeat(4000);
        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"" + oversize + "\"}"))
                .andExpect(status().isBadRequest());
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
