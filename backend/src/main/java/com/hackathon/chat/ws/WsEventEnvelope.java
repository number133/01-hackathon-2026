package com.hackathon.chat.ws;

import com.hackathon.chat.message.MessageView;
import java.util.UUID;

public record WsEventEnvelope(
        String event,
        UUID conversationId,
        UUID roomId,
        long seq,
        MessageView message) {

    public static final String EVENT_CREATED = "message.created";
    public static final String EVENT_EDITED = "message.edited";
    public static final String EVENT_DELETED = "message.deleted";

    public static WsEventEnvelope of(String event, MessageView view) {
        return new WsEventEnvelope(event, view.conversationId(), view.roomId(),
                view.seq(), view);
    }
}
