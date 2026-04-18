package com.hackathon.chat.ws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.room.RoomBanRepository;
import com.hackathon.chat.room.RoomMember;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class WsChannelInterceptorTest {

    private UserService userService;
    private RoomService roomService;
    private RoomBanRepository banRepo;
    private DialogService dialogService;
    private WsChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        roomService = Mockito.mock(RoomService.class);
        banRepo = Mockito.mock(RoomBanRepository.class);
        dialogService = Mockito.mock(DialogService.class);
        interceptor = new WsChannelInterceptor(userService, roomService, banRepo, dialogService);
    }

    @Test
    void connectWithoutUserIsRejected() {
        Message<?> msg = frame(StompCommand.CONNECT, null, null);
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void subscribeOutsideTopicRoomsIsRejected() {
        Message<?> msg = frame(StompCommand.SUBSCRIBE, "/queue/notifications", () -> "alice");
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeForBannedUserIsRejected() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User("a@ex.com", "alice", "hash");
        injectId(user, userId);
        when(userService.requireByUsername("alice")).thenReturn(user);
        when(banRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        Message<?> msg = frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId, () -> "alice");
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeForNonMemberIsRejected() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User("a@ex.com", "alice", "hash");
        injectId(user, userId);
        when(userService.requireByUsername("alice")).thenReturn(user);
        when(banRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);
        when(roomService.requireMember(roomId, userId))
                .thenThrow(new ForbiddenException("not a member"));

        Message<?> msg = frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId, () -> "alice");
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void subscribeForMemberPasses() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User("a@ex.com", "alice", "hash");
        injectId(user, userId);
        when(userService.requireByUsername("alice")).thenReturn(user);
        when(banRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);
        when(roomService.requireMember(roomId, userId))
                .thenReturn(new RoomMember(roomId, userId, RoomMember.ROLE_MEMBER));

        Message<?> msg = frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId, () -> "alice");
        interceptor.preSend(msg, null);
    }

    @Test
    void clientSendIsAlwaysRejected() {
        Message<?> msg = frame(StompCommand.SEND, "/topic/rooms/x", () -> "alice");
        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Message<?> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static void injectId(Object target, UUID id) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(target, id);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
