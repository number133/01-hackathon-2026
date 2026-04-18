package com.hackathon.chat.ws;

import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class UserEventPublisher {

    private final SimpMessagingTemplate template;

    public UserEventPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publish(UUID userId, String event, Object payload) {
        String destination = "/topic/users/" + userId;
        Map<String, Object> envelope = Map.of("event", event, "payload", payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            template.convertAndSend(destination, envelope);
                        }
                    });
        } else {
            template.convertAndSend(destination, envelope);
        }
    }
}
