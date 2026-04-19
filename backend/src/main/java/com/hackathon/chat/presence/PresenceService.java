package com.hackathon.chat.presence;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final int MAX_TABS_PER_USER = 50;

    private final PresenceProperties props;
    private final SimpMessagingTemplate broker;
    private final Clock clock;

    private final Map<UUID, Map<String, Long>> tabs = new ConcurrentHashMap<>();
    private final Map<UUID, PresenceStatus> lastPublished = new ConcurrentHashMap<>();

    public PresenceService(PresenceProperties props, SimpMessagingTemplate broker, Clock clock) {
        this.props = props;
        this.broker = broker;
        this.clock = clock;
    }

    public void recordPing(UUID userId, String tabId) {
        long now = clock.millis();
        Map<String, Long> perTab = tabs.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        if (perTab.size() >= MAX_TABS_PER_USER && !perTab.containsKey(tabId)) {
            evictOldestTab(perTab);
        }
        perTab.put(tabId, now);
        publishIfChanged(userId, now);
    }

    public PresenceStatus statusOf(UUID userId) {
        return statusOf(userId, clock.millis());
    }

    public List<PresenceView> bulkStatus(List<UUID> userIds) {
        long now = clock.millis();
        List<PresenceView> out = new ArrayList<>(userIds.size());
        for (UUID id : userIds) {
            out.add(new PresenceView(id, statusOf(id, now)));
        }
        return out;
    }

    public void dropTab(UUID userId, String tabId) {
        Map<String, Long> perTab = tabs.get(userId);
        if (perTab == null) return;
        if (perTab.remove(tabId) == null) return;
        if (perTab.isEmpty()) {
            tabs.remove(userId, perTab);
        }
        publishIfChanged(userId, clock.millis());
    }

    public void sweep() {
        long now = clock.millis();
        long offlineCutoff = now - props.afkThreshold().toMillis() - props.offlineGrace().toMillis();
        for (Map.Entry<UUID, Map<String, Long>> e : tabs.entrySet()) {
            Map<String, Long> perTab = e.getValue();
            perTab.entrySet().removeIf(t -> t.getValue() < offlineCutoff);
            if (perTab.isEmpty()) {
                tabs.remove(e.getKey(), perTab);
            }
            publishIfChanged(e.getKey(), now);
        }
    }

    private PresenceStatus statusOf(UUID userId, long now) {
        Map<String, Long> perTab = tabs.get(userId);
        if (perTab == null || perTab.isEmpty()) {
            return PresenceStatus.OFFLINE;
        }
        long latest = perTab.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (latest >= now - props.afkThreshold().toMillis()) {
            return PresenceStatus.ONLINE;
        }
        long offlineCutoff = now - props.afkThreshold().toMillis() - props.offlineGrace().toMillis();
        if (latest >= offlineCutoff) {
            return PresenceStatus.AFK;
        }
        return PresenceStatus.OFFLINE;
    }

    private void publishIfChanged(UUID userId, long now) {
        PresenceStatus current = statusOf(userId, now);
        PresenceStatus prior = lastPublished.get(userId);
        if (current == prior) {
            return;
        }
        if (current == PresenceStatus.OFFLINE && prior == null) {
            // Never advertised → don't emit a gratuitous "offline" frame.
            return;
        }
        lastPublished.put(userId, current);
        PresenceEvent payload = new PresenceEvent(userId, current, now);
        broker.convertAndSend("/topic/presence/" + userId, payload);
        log.debug("presence: user={} {} → {}", userId, prior, current);
    }

    private static void evictOldestTab(Map<String, Long> perTab) {
        String oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (Map.Entry<String, Long> e : perTab.entrySet()) {
            if (e.getValue() < oldestAt) {
                oldestAt = e.getValue();
                oldest = e.getKey();
            }
        }
        if (oldest != null) {
            perTab.remove(oldest);
        }
    }

    public record PresenceEvent(UUID userId, PresenceStatus status, long at) {
    }
}
