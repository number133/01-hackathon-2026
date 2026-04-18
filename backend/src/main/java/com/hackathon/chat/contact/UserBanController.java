package com.hackathon.chat.contact;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-bans")
public class UserBanController {

    private final UserBanService bans;
    private final UserService userService;

    public UserBanController(UserBanService bans, UserService userService) {
        this.bans = bans;
        this.userService = userService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Void> ban(@PathVariable UUID userId) {
        bans.ban(me().getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unban(@PathVariable UUID userId) {
        bans.unban(me().getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<UserBanView> list() {
        return bans.list(me().getId());
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
