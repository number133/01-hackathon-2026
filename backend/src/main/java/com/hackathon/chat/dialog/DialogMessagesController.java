package com.hackathon.chat.dialog;

import com.hackathon.chat.common.RateLimiter;
import com.hackathon.chat.common.TooManyRequestsException;
import com.hackathon.chat.message.HistoryPage;
import com.hackathon.chat.message.MessageService;
import com.hackathon.chat.message.MessageView;
import com.hackathon.chat.message.SendMessageRequest;
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
@RequestMapping("/api/dialogs/{dialogId}/messages")
public class DialogMessagesController {

    private final MessageService messageService;
    private final UserService userService;
    private final RateLimiter rateLimiter;

    public DialogMessagesController(MessageService messageService,
                                    UserService userService,
                                    RateLimiter rateLimiter) {
        this.messageService = messageService;
        this.userService = userService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<MessageView> post(@PathVariable UUID dialogId,
                                            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = me().getId();
        if (!rateLimiter.tryAcquire("messages.post", userId, 20, 1.0)) {
            throw new TooManyRequestsException("messages.post");
        }
        MessageView view = messageService.postToDialog(userId, dialogId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public Map<String, Object> history(@PathVariable UUID dialogId,
                                       @RequestParam(required = false) Long beforeSeq,
                                       @RequestParam(required = false) Integer limit) {
        HistoryPage page = messageService.historyForDialog(dialogId, me().getId(), beforeSeq, limit);
        return Map.of("items", page.items(), "hasMore", page.hasMore());
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
