package com.hackathon.chat.auth;

import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import com.hackathon.chat.user.UserService;
import com.hackathon.chat.user.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthService authService;

    public AuthController(UserService userService,
                          UserRepository userRepository,
                          AuthService authService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegistrationRequest request,
                                             HttpServletRequest servletRequest,
                                             HttpServletResponse servletResponse) {
        User user = userService.register(request);
        authService.establishSession(user, false, servletRequest, servletResponse);
        return ResponseEntity.ok(UserView.of(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserView> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest servletRequest,
                                          HttpServletResponse servletResponse) {
        User user = authService.authenticate(request.email(), request.password());
        authService.establishSession(user, request.rememberMe(), servletRequest, servletResponse);
        return ResponseEntity.ok(UserView.of(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserView> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BadCredentialsException("not signed in");
        }
        User user = userRepository.findByUsernameLower(auth.getName().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("not signed in"));
        return ResponseEntity.ok(UserView.of(user));
    }
}
