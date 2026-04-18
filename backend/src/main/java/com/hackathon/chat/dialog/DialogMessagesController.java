package com.hackathon.chat.dialog;

import com.hackathon.chat.message.MessageService;
import com.hackathon.chat.message.MessageView;
import com.hackathon.chat.message.SendMessageRequest;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
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

    public DialogMessagesController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<MessageView> post(@PathVariable UUID dialogId,
                                            @Valid @RequestBody SendMessageRequest request) {
        MessageView view = messageService.postToDialog(me().getId(), dialogId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public Map<String, Object> history(@PathVariable UUID dialogId,
                                       @RequestParam(required = false) Long beforeSeq,
                                       @RequestParam(required = false) Integer limit) {
        List<MessageView> items = messageService.historyForDialog(dialogId, me().getId(), beforeSeq, limit);
        return Map.of("items", items, "pageSize", items.size());
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
