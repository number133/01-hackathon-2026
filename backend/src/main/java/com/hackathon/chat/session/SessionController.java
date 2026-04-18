package com.hackathon.chat.session;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionView> list(HttpServletRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String currentId = request.getSession(false) == null ? "" : request.getSession(false).getId();
        return sessionService.listForUser(username, currentId);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(@PathVariable String sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        sessionService.revoke(username, sessionId);
        return ResponseEntity.noContent().build();
    }
}
