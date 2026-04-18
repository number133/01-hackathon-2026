package com.hackathon.chat.auth;

import com.hackathon.chat.common.SessionProperties;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public static final String ATTR_IP = "chat.session.ip";
    public static final String ATTR_USER_AGENT = "chat.session.userAgent";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionProperties sessionProperties;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SessionProperties sessionProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionProperties = sessionProperties;
    }

    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }
        return user;
    }

    public void establishSession(User user,
                                 boolean rememberMe,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        long ttlSeconds = (rememberMe ? sessionProperties.longTtl() : sessionProperties.shortTtl()).toSeconds();
        session.setMaxInactiveInterval((int) ttlSeconds);

        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        session.setAttribute(AuthService.ATTR_IP, extractIp(request));
        session.setAttribute(AuthService.ATTR_USER_AGENT, emptyIfNull(request.getHeader("User-Agent")));
    }

    private static String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
