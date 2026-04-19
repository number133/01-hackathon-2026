package com.hackathon.chat.presence;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Correlates STOMP sessions with presence tabs: a CONNECT frame that carries
 * a <code>tabId</code> header registers the mapping; DISCONNECT drops the
 * matching tab from {@link PresenceService} so the last-tab-closed transition
 * doesn't have to wait for the sweep grace window.
 */
@Component
public class StompPresenceListener {

    private static final Logger log = LoggerFactory.getLogger(StompPresenceListener.class);

    private final PresenceService presence;
    private final UserService userService;

    private final Map<String, Binding> bySession = new ConcurrentHashMap<>();

    public StompPresenceListener(PresenceService presence, UserService userService) {
        this.presence = presence;
        this.userService = userService;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String tabId = accessor.getFirstNativeHeader("tabId");
        if (sessionId == null || tabId == null || accessor.getUser() == null) {
            return;
        }
        try {
            User user = userService.requireByUsername(accessor.getUser().getName());
            bySession.put(sessionId, new Binding(user.getId(), tabId));
        } catch (RuntimeException ex) {
            log.debug("stomp connect: could not resolve user {}", accessor.getUser().getName(), ex);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) return;
        Binding b = bySession.remove(sessionId);
        if (b == null) return;
        presence.dropTab(b.userId(), b.tabId());
    }

    private record Binding(UUID userId, String tabId) {
    }
}
