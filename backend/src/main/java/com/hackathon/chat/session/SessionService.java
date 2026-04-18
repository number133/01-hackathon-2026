package com.hackathon.chat.session;

import com.hackathon.chat.auth.AuthService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<SessionView> listForUser(String username, String currentSessionId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        List<SessionView> views = new ArrayList<>(sessions.size());
        for (Session session : sessions.values()) {
            views.add(toView(session, currentSessionId));
        }
        views.sort(Comparator.comparing(SessionView::lastAccessedAt).reversed());
        return views;
    }

    public void revoke(String username, String sessionId) {
        Map<String, ? extends Session> mine = sessionRepository.findByPrincipalName(username);
        if (!mine.containsKey(sessionId)) {
            throw new AccessDeniedException("Session does not belong to the current user");
        }
        sessionRepository.deleteById(sessionId);
    }

    public void revokeAllExcept(String username, String keepSessionId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        for (Session session : sessions.values()) {
            if (!session.getId().equals(keepSessionId)) {
                sessionRepository.deleteById(session.getId());
            }
        }
    }

    private SessionView toView(Session session, String currentSessionId) {
        String ip = session.getAttribute(AuthService.ATTR_IP);
        String userAgent = session.getAttribute(AuthService.ATTR_USER_AGENT);
        Instant created = session.getCreationTime();
        Instant lastAccessed = session.getLastAccessedTime();
        return new SessionView(
                session.getId(),
                created,
                lastAccessed,
                ip == null ? "" : ip,
                userAgent == null ? "" : userAgent,
                session.getId().equals(currentSessionId));
    }
}
