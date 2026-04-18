package com.hackathon.chat.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.hackathon.chat.message.MessageView;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class MessageBroadcasterTest {

    @Test
    void publishSendsEnvelopeToTopicForRoom() {
        SimpMessagingTemplate template = Mockito.mock(SimpMessagingTemplate.class);
        MessageBroadcaster broadcaster = new MessageBroadcaster(template);

        UUID roomId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        MessageView view = new MessageView(
                UUID.randomUUID(), conversationId, roomId, 42L,
                UUID.randomUUID(), "alice", "hi", null,
                java.util.List.of(),
                Instant.now(), null, null);

        broadcaster.publish(WsEventEnvelope.EVENT_CREATED, roomId, view);

        verify(template).convertAndSend(eq("/topic/rooms/" + roomId), any(WsEventEnvelope.class));
    }
}
