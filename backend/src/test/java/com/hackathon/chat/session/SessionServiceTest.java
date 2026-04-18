package com.hackathon.chat.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.auth.AuthService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

class SessionServiceTest {

    private FindByIndexNameSessionRepository<Session> repository;
    private SessionService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repository = Mockito.mock(FindByIndexNameSessionRepository.class);
        service = new SessionService(repository);
    }

    @Test
    void listForUserReturnsViewsSortedByLastAccessDescending() {
        MapSession s1 = new MapSession("aaa");
        s1.setLastAccessedTime(Instant.parse("2026-04-18T10:00:00Z"));
        s1.setAttribute(AuthService.ATTR_IP, "1.1.1.1");
        s1.setAttribute(AuthService.ATTR_USER_AGENT, "Firefox");
        MapSession s2 = new MapSession("bbb");
        s2.setLastAccessedTime(Instant.parse("2026-04-18T11:00:00Z"));
        s2.setAttribute(AuthService.ATTR_IP, "2.2.2.2");
        s2.setAttribute(AuthService.ATTR_USER_AGENT, "Chrome");
        Map<String, Session> sessions = new LinkedHashMap<>();
        sessions.put(s1.getId(), s1);
        sessions.put(s2.getId(), s2);
        when(repository.findByPrincipalName("alice")).thenReturn(sessions);

        var views = service.listForUser("alice", "bbb");

        assertThat(views).hasSize(2);
        assertThat(views.get(0).sessionId()).isEqualTo("bbb");
        assertThat(views.get(0).current()).isTrue();
        assertThat(views.get(1).sessionId()).isEqualTo("aaa");
        assertThat(views.get(1).current()).isFalse();
        assertThat(views.get(0).ip()).isEqualTo("2.2.2.2");
    }

    @Test
    void revokeRefusesSessionOwnedByAnotherUser() {
        when(repository.findByPrincipalName("alice")).thenReturn(Map.of());

        assertThatThrownBy(() -> service.revoke("alice", "not-my-session"))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void revokeAllExceptKeepsOnlyGivenSessionId() {
        MapSession s1 = new MapSession("aaa");
        MapSession s2 = new MapSession("bbb");
        MapSession s3 = new MapSession("ccc");
        Map<String, Session> sessions = Map.of(
                s1.getId(), s1,
                s2.getId(), s2,
                s3.getId(), s3);
        when(repository.findByPrincipalName("alice")).thenReturn(sessions);

        service.revokeAllExcept("alice", "bbb");

        verify(repository).deleteById("aaa");
        verify(repository).deleteById("ccc");
        verify(repository, never()).deleteById("bbb");
    }
}
