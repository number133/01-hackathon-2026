package com.hackathon.chat.user;

import com.hackathon.chat.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserService userService;
    private final SessionService sessionService;

    public AccountController(UserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest) {
        User user = currentUser();
        userService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
        sessionService.revokeAllExcept(user.getUsername(), servletRequest.getSession(false).getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest servletRequest) {
        User user = currentUser();
        userService.deleteAccount(user.getId(), request.password());
        if (servletRequest.getSession(false) != null) {
            servletRequest.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userService.requireByUsername(username);
    }
}
