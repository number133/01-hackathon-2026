package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.chat.message.MessageView;
import com.hackathon.chat.ws.WsEventEnvelope;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WebSocketFanoutIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper json;

    @Test
    void subscriberReceivesCreatedMessage() throws Exception {
        SessionCookies alice = register("ws-alice@ex.com", "wsalice", "supersecret");
        SessionCookies bob = register("ws-bob@ex.com", "wsbob", "supersecret");

        String roomId = createRoom(alice, "ws-room");
        post("/api/rooms/" + roomId + "/join", null, bob);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.afterPropertiesSet();
        stompClient.setTaskScheduler(scheduler);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "SESSION=" + bob.sessionValue);

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        CompletableFuture<String> received = new CompletableFuture<>();
        session.subscribe("/topic/rooms/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete(new String((byte[]) payload));
            }
        });
        Thread.sleep(300);

        ResponseEntity<String> postResp = post(
                "/api/rooms/" + roomId + "/messages",
                Map.of("text", "fanout-test"), alice);
        assertThat(postResp.getStatusCode().is2xxSuccessful())
                .as("POST /messages body was %s", postResp.getBody())
                .isTrue();

        String payload = received.get(5, TimeUnit.SECONDS);
        JsonNode envelope = json.readTree(payload);
        assertThat(envelope.get("event").asText()).isEqualTo(WsEventEnvelope.EVENT_CREATED);
        assertThat(envelope.get("seq").asLong()).isEqualTo(1L);
        assertThat(envelope.get("message").get("body").asText()).isEqualTo("fanout-test");
        assertThat(envelope.get("message").get("authorUsername").asText()).isEqualTo("wsalice");

        session.disconnect();
    }

    @Test
    void nonMemberSubscribeIsRejected() throws Exception {
        SessionCookies alice = register("ws-alice2@ex.com", "wsalice2", "supersecret");
        SessionCookies outsider = register("ws-out@ex.com", "wsout", "supersecret");
        String roomId = createRoom(alice, "ws-members-only");

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.afterPropertiesSet();
        stompClient.setTaskScheduler(scheduler);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "SESSION=" + outsider.sessionValue);

        CompletableFuture<Throwable> error = new CompletableFuture<>();
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession s,
                                                org.springframework.messaging.simp.stomp.StompCommand c,
                                                StompHeaders headers, byte[] payload, Throwable exception) {
                        error.complete(exception);
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable exception) {
                        error.complete(exception);
                    }
                }
        ).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/rooms/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // Should not arrive.
            }
        });

        Throwable thrown = error.get(3, TimeUnit.SECONDS);
        assertThat(thrown).isNotNull();
    }

    private SessionCookies register(String email, String username, String password) throws Exception {
        URI url = URI.create("http://localhost:" + port + "/");
        ResponseEntity<String> bootstrap = rest.exchange(url, HttpMethod.GET, null, String.class);
        String xsrf = extractCookie(bootstrap.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf);
        headers.add("X-XSRF-TOKEN", xsrf);
        Map<String, Object> body = Map.of("email", email, "username", username, "password", password);
        ResponseEntity<String> response = rest.exchange(
                URI.create("http://localhost:" + port + "/api/auth/register"),
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String session = extractCookie(response.getHeaders().get(HttpHeaders.SET_COOKIE), "SESSION");
        return new SessionCookies(session, xsrf);
    }

    private String createRoom(SessionCookies session, String name) throws Exception {
        ResponseEntity<String> r = post("/api/rooms", Map.of(
                "name", name, "description", "x", "visibility", "public"), session);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        return json.readTree(r.getBody()).get("id").asText();
    }

    private ResponseEntity<String> post(String path, Object body, SessionCookies session) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        headers.add(HttpHeaders.COOKIE, "SESSION=" + session.sessionValue
                + "; XSRF-TOKEN=" + session.xsrfValue);
        headers.add("X-XSRF-TOKEN", session.xsrfValue);
        return rest.exchange(URI.create("http://localhost:" + port + path),
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String extractCookie(List<String> setCookies, String name) {
        if (setCookies == null) return null;
        for (String raw : setCookies) {
            String[] parts = raw.split(";");
            if (parts.length == 0) continue;
            String[] kv = parts[0].split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(name)) {
                return kv[1].trim();
            }
        }
        return null;
    }

    private record SessionCookies(String sessionValue, String xsrfValue) {
    }
}
