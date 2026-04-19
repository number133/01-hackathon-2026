package com.hackathon.chat.unread;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnreadController {

    private final UnreadService service;
    private final UserService userService;

    public UnreadController(UnreadService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping("/api/unread")
    public List<UnreadView> list() {
        return service.snapshot(me().getId());
    }

    @PostMapping("/api/conversations/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                         @Valid @RequestBody MarkReadRequest request) {
        service.markRead(me().getId(), id, request.seq());
        return ResponseEntity.noContent().build();
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
