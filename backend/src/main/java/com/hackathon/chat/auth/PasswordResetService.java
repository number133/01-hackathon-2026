package com.hackathon.chat.auth;

import com.hackathon.chat.common.InvalidTokenException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int BUFFER_SIZE = 20;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final Deque<RecentToken> recentTokens = new ArrayDeque<>();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void request(String email) {
        Optional<User> match = userRepository.findByEmail(email.trim());
        if (match.isEmpty()) {
            // Silent-succeed to avoid user-enumeration.
            return;
        }
        User user = match.get();
        String rawToken = generateToken();
        String hash = sha256Hex(rawToken);
        Instant expires = Instant.now().plus(TOKEN_TTL);
        tokenRepository.save(new PasswordResetToken(user.getId(), hash, expires));

        log.info("Password reset link for {}: /reset-password?token={}", user.getEmail(), rawToken);

        synchronized (recentTokens) {
            recentTokens.addFirst(new RecentToken(user.getEmail(), rawToken, expires));
            while (recentTokens.size() > BUFFER_SIZE) {
                recentTokens.removeLast();
            }
        }
    }

    public void confirm(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Token is not valid"));
        if (token.getUsedAt() != null) {
            throw new InvalidTokenException("Token already used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Token expired");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Token user missing"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(Instant.now());
    }

    public List<RecentToken> recentTokens() {
        synchronized (recentTokens) {
            return List.copyOf(recentTokens);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record RecentToken(String email, String token, Instant expiresAt) {
    }
}
