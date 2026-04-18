package com.hackathon.chat.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.SessionProperties;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        SessionProperties props = new SessionProperties(Duration.ofHours(24), Duration.ofDays(30));
        service = new AuthService(userRepository, passwordEncoder, props);
    }

    @Test
    void authenticateReturnsUserOnMatch() {
        User user = new User("alice@example.com", "alice", "hash");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        User result = service.authenticate("alice@example.com", "pw");

        assertThat(result).isSameAs(user);
    }

    @Test
    void authenticateRejectsUnknownEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ghost@example.com", "pw"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        User user = new User("alice@example.com", "alice", "hash");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("alice@example.com", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
