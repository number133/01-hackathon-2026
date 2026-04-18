package com.hackathon.chat.ws;

import com.hackathon.chat.message.MessageView;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MessageBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcaster.class);

    private final SimpMessagingTemplate template;

    public MessageBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    /**
     * Broadcasts after the current transaction commits so a rollback never
     * leaks a phantom message to subscribers. Falls back to immediate send
     * when called outside a transaction (e.g. tests).
     */
    public void publish(String event, UUID roomId, MessageView view) {
        WsEventEnvelope envelope = WsEventEnvelope.of(event, view);
        String destination = "/topic/rooms/" + roomId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("registering afterCommit broadcast for {} seq={}", destination, view.seq());
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.debug("afterCommit: sending to {} seq={}", destination, view.seq());
                            template.convertAndSend(destination, envelope);
                        }
                    });
        } else {
            log.debug("immediate broadcast to {} seq={}", destination, view.seq());
            template.convertAndSend(destination, envelope);
        }
    }
}
