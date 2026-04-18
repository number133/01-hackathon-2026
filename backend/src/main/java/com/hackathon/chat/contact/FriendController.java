package com.hackathon.chat.contact;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FriendController {

    private final FriendService friends;
    private final UserService userService;

    public FriendController(FriendService friends, UserService userService) {
        this.friends = friends;
        this.userService = userService;
    }

    @PostMapping("/api/friend-requests")
    public ResponseEntity<FriendRequestView> send(@Valid @RequestBody SendFriendRequestRequest req) {
        FriendRequestView view = friends.sendRequest(me().getId(), req.username(), req.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/api/friend-requests")
    public List<FriendRequestView> list(
            @RequestParam(value = "direction", required = false) String direction) {
        return friends.list(me().getId(), direction);
    }

    @PostMapping("/api/friend-requests/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID id) {
        friends.accept(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/friend-requests/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID id) {
        friends.decline(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/friend-requests/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        friends.revoke(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/friends")
    public List<FriendView> listFriends() {
        return friends.listFriends(me().getId());
    }

    @DeleteMapping("/api/friends/{userId}")
    public ResponseEntity<Void> unfriend(@PathVariable UUID userId) {
        friends.unfriend(me().getId(), userId);
        return ResponseEntity.noContent().build();
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
