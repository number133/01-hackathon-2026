package com.hackathon.chat.dialog;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dialogs")
public class DialogController {

    private final DialogService dialogs;
    private final UserService userService;

    public DialogController(DialogService dialogs, UserService userService) {
        this.dialogs = dialogs;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<DialogView> openOrGet(@Valid @RequestBody OpenDialogRequest req) {
        DialogView view = dialogs.getOrCreate(me().getId(), req.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public List<DialogView> list() {
        return dialogs.list(me().getId());
    }

    @GetMapping("/{id}")
    public DialogView one(@PathVariable UUID id) {
        return dialogs.view(id, me().getId());
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
