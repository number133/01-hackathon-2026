package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.chat.auth.RegistrationRequest;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
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
class AttachmentFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    static Path UPLOAD_ROOT;

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        registry.add("chat.attachment.storage-root", () -> UPLOAD_ROOT.toString());
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void uploadLinkDownloadRoundTrip() throws Exception {
        Cookie alice = register("att-a@example.com", "atta", "supersecret");
        Cookie bob = register("att-b@example.com", "attb", "supersecret");

        String roomId = createRoom(alice, "att-room");
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(bob).with(csrf()))
                .andExpect(status().isNoContent());
        String convId = conversationOf(roomId, alice);

        byte[] bytes = "hello bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", bytes);

        MvcResult upload = mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("conversationId", convId)
                        .param("comment", "first note")
                        .cookie(alice).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String attId = json.readTree(upload.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/attachments/" + attId + "/metadata").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("hello.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(bytes.length));

        // Alice sends a message referencing the attachment.
        String body = "{\"text\":\"look\",\"attachmentIds\":[\"" + attId + "\"]}";
        MvcResult send = mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].id").value(attId))
                .andReturn();

        // Bob downloads the bytes.
        MvcResult dl = mvc.perform(get("/api/attachments/" + attId).cookie(bob))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(dl.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(dl.getResponse().getHeader("Content-Disposition"))
                .contains("hello.txt");
    }

    @Test
    void attachmentOnlyMessageAccepted() throws Exception {
        Cookie alice = register("att-ao-a@example.com", "attaoa", "supersecret");
        String roomId = createRoom(alice, "att-ao-room");
        String convId = conversationOf(roomId, alice);

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.bin", "application/octet-stream", "x".getBytes());
        MvcResult up = mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("conversationId", convId)
                        .cookie(alice).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        String attId = json.readTree(up.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"attachmentIds\":[\"" + attId + "\"]}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void banRevokesAttachmentAccess() throws Exception {
        Cookie alice = register("att-ban-a@example.com", "attba", "supersecret");
        Cookie bob = register("att-ban-b@example.com", "attbb", "supersecret");

        String roomId = createRoom(alice, "att-ban-room");
        mvc.perform(post("/api/rooms/" + roomId + "/join").cookie(bob).with(csrf()))
                .andExpect(status().isNoContent());
        String convId = conversationOf(roomId, alice);

        MockMultipartFile file = new MockMultipartFile(
                "file", "b.bin", "application/octet-stream", "b".getBytes());
        MvcResult up = mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("conversationId", convId)
                        .cookie(alice).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        String attId = json.readTree(up.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"attachmentIds\":[\"" + attId + "\"]}"))
                .andExpect(status().isCreated());

        // Bob can read.
        mvc.perform(get("/api/attachments/" + attId).cookie(bob))
                .andExpect(status().isOk());

        // Alice bans Bob.
        String bobId = fetchMyId(bob);
        mvc.perform(post("/api/rooms/" + roomId + "/bans")
                        .cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/attachments/" + attId).cookie(bob))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountDeleteRemovesAttachmentDirectoriesOfOwnedRooms() throws Exception {
        Cookie alice = register("att-ad-a@example.com", "attada", "supersecret");
        String roomId = createRoom(alice, "att-ad-room");
        String convId = conversationOf(roomId, alice);

        MockMultipartFile file = new MockMultipartFile(
                "file", "d.bin", "application/octet-stream", "d".getBytes());
        mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("conversationId", convId)
                        .cookie(alice).with(csrf()))
                .andExpect(status().isCreated());

        Path convDir = UPLOAD_ROOT.resolve(convId);
        assertThat(Files.exists(convDir)).isTrue();

        mvc.perform(delete("/api/account").cookie(alice).with(csrf())
                        .contentType("application/json")
                        .content("{\"password\":\"supersecret\"}"))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(convDir)).isFalse();
    }

    @Test
    void roomDeleteRemovesAttachmentDirectory() throws Exception {
        Cookie alice = register("att-rd-a@example.com", "attrda", "supersecret");
        String roomId = createRoom(alice, "att-rd-room");
        String convId = conversationOf(roomId, alice);

        MockMultipartFile file = new MockMultipartFile(
                "file", "c.bin", "application/octet-stream", "c".getBytes());
        mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("conversationId", convId)
                        .cookie(alice).with(csrf()))
                .andExpect(status().isCreated());

        Path convDir = UPLOAD_ROOT.resolve(convId);
        assertThat(Files.exists(convDir)).isTrue();

        mvc.perform(delete("/api/rooms/" + roomId)
                        .cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(convDir)).isFalse();
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
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String conversationOf(String roomId, Cookie session) throws Exception {
        MvcResult r = mvc.perform(get("/api/rooms/" + roomId).cookie(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = json.readTree(r.getResponse().getContentAsString());
        JsonNode conv = node.get("conversationId");
        if (conv != null && !conv.isNull()) return conv.asText();
        // fallback: post a message and pull conversationId from the response
        MvcResult post = mvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .cookie(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"seed\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(post.getResponse().getContentAsString()).get("conversationId").asText();
    }

    private String fetchMyId(Cookie session) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }
}
