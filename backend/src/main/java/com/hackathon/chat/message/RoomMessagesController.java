package com.hackathon.chat.message;

import com.hackathon.chat.common.RateLimiter;
import com.hackathon.chat.common.TooManyRequestsException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class RoomMessagesController {

    private final MessageService messageService;
    private final UserService userService;
    private final RateLimiter rateLimiter;

    public RoomMessagesController(MessageService messageService,
                                  UserService userService,
                                  RateLimiter rateLimiter) {
        this.messageService = messageService;
        this.userService = userService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<MessageView> post(@PathVariable UUID roomId,
                                            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = me().getId();
        // Capacity 20 / refill 1 per second → 60 msgs/min sustained, 20 burst.
        if (!rateLimiter.tryAcquire("messages.post", userId, 20, 1.0)) {
            throw new TooManyRequestsException("messages.post");
        }
        MessageView view = messageService.post(userId, roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public Map<String, Object> history(@PathVariable UUID roomId,
                                       @RequestParam(required = false) Long beforeSeq,
                                       @RequestParam(required = false) Integer limit) {
        HistoryPage page = messageService.history(roomId, me().getId(), beforeSeq, limit);
        return Map.of("items", page.items(), "hasMore", page.hasMore());
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
