package com.hackathon.chat.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.common.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private UserRepository repository;
    private PasswordEncoder encoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserRepository.class);
        encoder = Mockito.mock(PasswordEncoder.class);
        service = new UserService(repository, encoder);
    }

    @Test
    void registerPersistsHashedPassword() {
        when(repository.existsByEmail(anyString())).thenReturn(false);
        when(repository.existsByUsernameLower(anyString())).thenReturn(false);
        when(encoder.encode("supersecret")).thenReturn("hashed");
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.register(new RegistrationRequest("Alice@Example.com", "Alice", "supersecret"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getUsernameLower()).isEqualTo("alice");
        assertThat(saved.getUsername()).isEqualTo("Alice");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegistrationRequest("alice@example.com", "alice", "supersecret")))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("field").isEqualTo("email");

        verify(repository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsernameRegardlessOfCasing() {
        when(repository.existsByEmail(anyString())).thenReturn(false);
        when(repository.existsByUsernameLower("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegistrationRequest("new@example.com", "ALICE", "supersecret")))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("field").isEqualTo("username");
    }

    @Test
    void changePasswordVerifiesCurrentThenReHashes() {
        User user = new User("alice@example.com", "alice", "oldHash");
        when(repository.findById(user.getId() == null ? user.getId() : user.getId())).thenReturn(java.util.Optional.of(user));
        when(encoder.matches("old", "oldHash")).thenReturn(true);
        when(encoder.encode("newpw")).thenReturn("newHash");
        when(repository.findById(any())).thenReturn(java.util.Optional.of(user));

        service.changePassword(user.getId(), "old", "newpw");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    void changePasswordRejectsWrongCurrent() {
        User user = new User("alice@example.com", "alice", "oldHash");
        when(repository.findById(any())).thenReturn(java.util.Optional.of(user));
        when(encoder.matches("bad", "oldHash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(user.getId(), "bad", "newpw"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getPasswordHash()).isEqualTo("oldHash");
    }
}
