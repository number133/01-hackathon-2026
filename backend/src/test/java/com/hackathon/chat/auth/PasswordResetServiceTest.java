package com.hackathon.chat.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.InvalidTokenException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder encoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        tokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
        encoder = Mockito.mock(PasswordEncoder.class);
        service = new PasswordResetService(userRepository, tokenRepository, encoder);
    }

    @Test
    void requestForKnownEmailStoresHashedTokenAndCapturesInBuffer() {
        User user = new User("alice@example.com", "alice", "hash");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.request("alice@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).hasSize(64);
        assertThat(service.recentTokens()).hasSize(1);
        assertThat(service.recentTokens().get(0).email()).isEqualTo("alice@example.com");
    }

    @Test
    void requestForUnknownEmailIsSilent() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        service.request("ghost@example.com");

        verify(tokenRepository, never()).save(any());
        assertThat(service.recentTokens()).isEmpty();
    }

    @Test
    void confirmRejectsUnknownToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm("unknown", "newpass"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void confirmRejectsExpiredToken() {
        String raw = "sometoken";
        String hash = PasswordResetService.sha256Hex(raw);
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), hash, Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirm(raw, "newpass"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void confirmRejectsReusedToken() {
        String raw = "sometoken";
        String hash = PasswordResetService.sha256Hex(raw);
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), hash, Instant.now().plusSeconds(600));
        token.markUsed(Instant.now().minusSeconds(10));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirm(raw, "newpass"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void confirmUpdatesPasswordAndMarksTokenUsed() {
        String raw = "sometoken";
        String hash = PasswordResetService.sha256Hex(raw);
        User user = new User("alice@example.com", "alice", "oldHash");
        PasswordResetToken token = new PasswordResetToken(user.getId(), hash, Instant.now().plusSeconds(600));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(userRepository.findById(token.getUserId())).thenReturn(Optional.of(user));
        when(encoder.encode("newpass")).thenReturn("newHash");

        service.confirm(raw, "newpass");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        assertThat(token.getUsedAt()).isNotNull();
    }
}
