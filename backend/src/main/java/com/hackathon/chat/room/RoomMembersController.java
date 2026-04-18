package com.hackathon.chat.room;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomMembersController {

    private final RoomMembershipService membership;
    private final UserService userService;

    public RoomMembersController(RoomMembershipService membership, UserService userService) {
        this.membership = membership;
        this.userService = userService;
    }

    @PostMapping("/join")
    public ResponseEntity<Void> join(@PathVariable UUID roomId) {
        membership.join(roomId, me().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members/me")
    public ResponseEntity<Void> leave(@PathVariable UUID roomId) {
        membership.leave(roomId, me().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/members")
    public List<RoomMemberView> listMembers(@PathVariable UUID roomId) {
        return membership.listMembers(roomId, me().getId());
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> remove(@PathVariable UUID roomId, @PathVariable UUID userId) {
        membership.remove(roomId, userId, me().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bans")
    public ResponseEntity<RoomBanView> ban(@PathVariable UUID roomId,
                                           @Valid @RequestBody BanUserRequest request) {
        RoomBan ban = membership.ban(roomId, request.userId(), me().getId(), request.reason());
        List<RoomBanView> refreshed = membership.listBans(roomId, me().getId());
        RoomBanView view = refreshed.stream()
                .filter(v -> v.userId().equals(ban.getUserId()))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/bans")
    public List<RoomBanView> listBans(@PathVariable UUID roomId) {
        return membership.listBans(roomId, me().getId());
    }

    @DeleteMapping("/bans/{userId}")
    public ResponseEntity<Void> unban(@PathVariable UUID roomId, @PathVariable UUID userId) {
        membership.unban(roomId, userId, me().getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/admins/{userId}")
    public ResponseEntity<Void> promote(@PathVariable UUID roomId, @PathVariable UUID userId) {
        membership.promote(roomId, userId, me().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{userId}")
    public ResponseEntity<Void> demote(@PathVariable UUID roomId, @PathVariable UUID userId) {
        membership.demote(roomId, userId, me().getId());
        return ResponseEntity.noContent().build();
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
