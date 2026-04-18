package com.hackathon.chat.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.common.DuplicateResourceException;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private UserRepository userRepo;
    private PasswordEncoder encoder;
    private RoomRepository roomRepo;
    private RoomService roomService;
    private UserService service;

    @BeforeEach
    void setUp() throws Exception {
        userRepo = Mockito.mock(UserRepository.class);
        encoder = Mockito.mock(PasswordEncoder.class);
        roomRepo = Mockito.mock(RoomRepository.class);
        roomService = Mockito.mock(RoomService.class);
        service = new UserService(userRepo, encoder);
        injectField(service, "roomRepository", roomRepo);
        injectField(service, "roomService", roomService);
    }

    @Test
    void registerPersistsHashedPassword() {
        when(userRepo.existsByEmail(anyString())).thenReturn(false);
        when(userRepo.existsByUsernameLower(anyString())).thenReturn(false);
        when(encoder.encode("supersecret")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.register(
                new RegistrationRequest("Alice@Example.com", "Alice", "supersecret"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getUsernameLower()).isEqualTo("alice");
        assertThat(saved.getUsername()).isEqualTo("Alice");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepo.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegistrationRequest("alice@example.com", "alice", "supersecret")))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("field").isEqualTo("email");

        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsernameRegardlessOfCasing() {
        when(userRepo.existsByEmail(anyString())).thenReturn(false);
        when(userRepo.existsByUsernameLower("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegistrationRequest("new@example.com", "ALICE", "supersecret")))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("field").isEqualTo("username");
    }

    @Test
    void changePasswordVerifiesCurrentThenReHashes() {
        User user = new User("alice@example.com", "alice", "oldHash");
        when(userRepo.findById(any())).thenReturn(Optional.of(user));
        when(encoder.matches("old", "oldHash")).thenReturn(true);
        when(encoder.encode("newpw")).thenReturn("newHash");

        service.changePassword(user.getId(), "old", "newpw");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    void changePasswordRejectsWrongCurrent() {
        User user = new User("alice@example.com", "alice", "oldHash");
        when(userRepo.findById(any())).thenReturn(Optional.of(user));
        when(encoder.matches("bad", "oldHash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(user.getId(), "bad", "newpw"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getPasswordHash()).isEqualTo("oldHash");
    }

    @Test
    void deleteAccountCascadesOwnedRoomsThenDeletesUser() {
        User user = new User("alice@example.com", "alice", "hash");
        when(userRepo.findById(any())).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "hash")).thenReturn(true);
        Room owned1 = new Room("r1", "", "public", user.getId());
        Room owned2 = new Room("r2", "", "private", user.getId());
        when(roomRepo.findAllByOwnerId(user.getId())).thenReturn(List.of(owned1, owned2));

        service.deleteAccount(user.getId(), "pw");

        verify(roomService, times(2)).deleteAsOwnerCascade(any());
        verify(userRepo).delete(user);
    }

    @Test
    void countRoomsOwnedByDelegates() {
        UUID userId = UUID.randomUUID();
        when(roomRepo.countByOwnerId(userId)).thenReturn(3L);

        assertThat(service.countRoomsOwnedBy(userId)).isEqualTo(3L);
    }

    private static void injectField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
