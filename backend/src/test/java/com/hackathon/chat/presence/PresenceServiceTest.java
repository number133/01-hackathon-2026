package com.hackathon.chat.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class PresenceServiceTest {

    private SimpMessagingTemplate broker;
    private MutableClock clock;
    private PresenceProperties props;
    private PresenceService service;

    @BeforeEach
    void setUp() {
        broker = Mockito.mock(SimpMessagingTemplate.class);
        clock = new MutableClock(Instant.parse("2026-04-18T12:00:00Z"));
        props = new PresenceProperties(
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));
        service = new PresenceService(props, broker, clock);
    }

    @Test
    void pingFromNewTabEmitsOnline() {
        UUID user = UUID.randomUUID();
        service.recordPing(user, "tab-1");

        verify(broker, times(1)).convertAndSend(eq(topic(user)), argThat(isEvent(user, PresenceStatus.ONLINE)));
        assertThat(service.statusOf(user)).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void pingFromSameTabWhileOnlineDoesNotReemit() {
        UUID user = UUID.randomUUID();
        service.recordPing(user, "tab-1");
        reset(broker);

        clock.advance(Duration.ofSeconds(1));
        service.recordPing(user, "tab-1");

        verify(broker, never()).convertAndSend(eq(topic(user)), any(Object.class));
    }

    @Test
    void sweepAfterAfkThresholdEmitsAfk() {
        UUID user = UUID.randomUUID();
        service.recordPing(user, "tab-1");
        reset(broker);

        clock.advance(Duration.ofSeconds(61));
        service.sweep();

        verify(broker, times(1)).convertAndSend(eq(topic(user)), argThat(isEvent(user, PresenceStatus.AFK)));
        assertThat(service.statusOf(user)).isEqualTo(PresenceStatus.AFK);
    }

    @Test
    void pingAfterAfkEmitsOnline() {
        UUID user = UUID.randomUUID();
        service.recordPing(user, "tab-1");
        clock.advance(Duration.ofSeconds(61));
        service.sweep();
        reset(broker);

        service.recordPing(user, "tab-1");

        verify(broker, times(1)).convertAndSend(eq(topic(user)), argThat(isEvent(user, PresenceStatus.ONLINE)));
    }

    @Test
    void sweepAfterOfflineGraceEmitsOffline() {
        UUID user = UUID.randomUUID();
        service.recordPing(user, "tab-1");
        clock.advance(Duration.ofSeconds(61));
        service.sweep();
        reset(broker);

        clock.advance(Duration.ofSeconds(31));
        service.sweep();

        verify(broker, times(1)).convertAndSend(eq(topic(user)), argThat(isEvent(user, PresenceStatus.OFFLINE)));
        assertThat(service.statusOf(user)).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void secondTabKeepsOnlineWhenFirstTabSweepExpires() {
        UUID user = UUID.randomUUID();
        service.recordPing(user, "tab-1");
        clock.advance(Duration.ofSeconds(30));
        service.recordPing(user, "tab-2");
        reset(broker);

        // tab-1 silent past afk; tab-2 still fresh.
        clock.advance(Duration.ofSeconds(35));
        service.sweep();

        verify(broker, never()).convertAndSend(eq(topic(user)), any(Object.class));
        assertThat(service.statusOf(user)).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void bulkStatusReturnsOfflineForUnknownUser() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        service.recordPing(known, "tab-1");

        List<PresenceView> views = service.bulkStatus(List.of(known, unknown));

        assertThat(views).containsExactly(
                new PresenceView(known, PresenceStatus.ONLINE),
                new PresenceView(unknown, PresenceStatus.OFFLINE));
    }

    @Test
    void shorterAfkThresholdIsRespected() {
        PresenceProperties fast = new PresenceProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1));
        PresenceService fastService = new PresenceService(fast, broker, clock);

        UUID user = UUID.randomUUID();
        fastService.recordPing(user, "tab-1");
        reset(broker);

        clock.advance(Duration.ofSeconds(11));
        fastService.sweep();

        verify(broker, times(1)).convertAndSend(eq(topic(user)), argThat(isEvent(user, PresenceStatus.AFK)));
    }

    private static String topic(UUID userId) {
        return "/topic/presence/" + userId;
    }

    private static org.mockito.ArgumentMatcher<Object> isEvent(UUID userId, PresenceStatus expected) {
        return payload -> payload instanceof PresenceService.PresenceEvent e
                && e.userId().equals(userId)
                && e.status() == expected;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public long millis() {
            return now.toEpochMilli();
        }
    }
}
