package com.hackathon.chat.invitation;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InvitationController {

    private final InvitationService service;
    private final UserService userService;

    public InvitationController(InvitationService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @PostMapping("/rooms/{roomId}/invitations")
    public ResponseEntity<InvitationView> invite(@PathVariable UUID roomId,
                                                 @Valid @RequestBody CreateInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.invite(roomId, me().getId(), request));
    }

    @GetMapping("/rooms/{roomId}/invitations")
    public List<InvitationView> listForRoom(@PathVariable UUID roomId) {
        return service.listForRoom(roomId, me().getId());
    }

    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        service.revoke(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invitations")
    public List<InvitationView> listMine() {
        return service.listForInvitee(me().getId());
    }

    @PostMapping("/invitations/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID id) {
        service.accept(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID id) {
        service.decline(id, me().getId());
        return ResponseEntity.noContent().build();
    }

    private User me() {
        return userService.requireByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
