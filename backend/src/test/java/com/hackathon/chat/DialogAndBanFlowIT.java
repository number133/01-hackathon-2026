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
class DialogAndBanFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void openDialogSendBanFreezeUnbanRefriend() throws Exception {
        Cookie alice = register("dlg-a@example.com", "dlga", "supersecret");
        Cookie bob = register("dlg-b@example.com", "dlgb", "supersecret");
        String aliceId = fetchMyId(alice);
        String bobId = fetchMyId(bob);

        // Become friends.
        String reqId = sendAndAccept(alice, bob, "dlgb");

        // Open dialog.
        MvcResult dlgResp = mvc.perform(post("/api/dialogs")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String dialogId = json.readTree(dlgResp.getResponse().getContentAsString()).get("id").asText();

        // Alice sends.
        MvcResult sent = mvc.perform(post("/api/dialogs/" + dialogId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"hi bob\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("hi bob"))
                .andReturn();
        String messageId = json.readTree(sent.getResponse().getContentAsString()).get("id").asText();

        // Bob can read history.
        mvc.perform(get("/api/dialogs/" + dialogId + "/messages").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].body").value("hi bob"));

        // Alice bans Bob.
        mvc.perform(post("/api/user-bans/" + bobId).cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());

        // Dialog flipped frozen in view.
        mvc.perform(get("/api/dialogs/" + dialogId).cookie(alice))
                .andExpect(jsonPath("$.frozen").value(true));

        // Bob cannot send.
        mvc.perform(post("/api/dialogs/" + dialogId + "/messages")
                        .cookie(bob).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"blocked?\"}"))
                .andExpect(status().isConflict());

        // Bob cannot edit his own past message (none from him here, so Alice tries edit).
        mvc.perform(patch("/api/messages/" + messageId)
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"edited\"}"))
                .andExpect(status().isConflict());

        // Unban: dialog stays frozen (no friendship).
        mvc.perform(delete("/api/user-bans/" + bobId).cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/dialogs/" + dialogId).cookie(alice))
                .andExpect(jsonPath("$.frozen").value(true));

        // Fresh friend request, accept, dialog unfreezes.
        sendAndAccept(alice, bob, "dlgb");
        mvc.perform(get("/api/dialogs/" + dialogId).cookie(alice))
                .andExpect(jsonPath("$.frozen").value(false));

        // Both can send again.
        mvc.perform(post("/api/dialogs/" + dialogId + "/messages")
                        .cookie(bob).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"back\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void nonParticipantCannotSeeOrSend() throws Exception {
        Cookie alice = register("np-a@example.com", "npa", "supersecret");
        Cookie bob = register("np-b@example.com", "npb", "supersecret");
        Cookie carol = register("np-c@example.com", "npc", "supersecret");

        sendAndAccept(alice, bob, "npb");
        String bobId = fetchMyId(bob);
        MvcResult dlg = mvc.perform(post("/api/dialogs")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String dialogId = json.readTree(dlg.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/dialogs/" + dialogId).cookie(carol))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dialogs/" + dialogId + "/messages")
                        .cookie(carol).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"sneaky\"}"))
                .andExpect(status().isForbidden());
    }

    private String sendAndAccept(Cookie sender, Cookie receiver, String receiverUsername) throws Exception {
        MvcResult create = mvc.perform(post("/api/friend-requests")
                        .cookie(sender).with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"" + receiverUsername + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String reqId = json.readTree(create.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/friend-requests/" + reqId + "/accept")
                        .cookie(receiver).with(csrf()))
                .andExpect(status().isNoContent());
        return reqId;
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
