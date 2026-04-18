package com.hackathon.chat.ws;

import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.room.RoomBanRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class WsChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsChannelInterceptor.class);
    private static final String ROOMS_PREFIX = "/topic/rooms/";
    private static final String PRESENCE_PREFIX = "/topic/presence/";
    private static final String DIALOGS_PREFIX = "/topic/dialogs/";
    private static final String USERS_PREFIX = "/topic/users/";

    private final UserService userService;
    private final RoomService roomService;
    private final RoomBanRepository banRepository;
    private final DialogService dialogService;

    public WsChannelInterceptor(UserService userService,
                                RoomService roomService,
                                RoomBanRepository banRepository,
                                @Lazy DialogService dialogService) {
        this.userService = userService;
        this.roomService = roomService;
        this.banRepository = banRepository;
        this.dialogService = dialogService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        log.debug("STOMP {} user={} destination={}",
                command, accessor.getUser(), accessor.getDestination());
        if (command == StompCommand.CONNECT) {
            if (accessor.getUser() == null) {
                throw new BadCredentialsException("WebSocket requires an authenticated session");
            }
            return message;
        }
        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination == null) {
                throw new AccessDeniedException("Subscribe requires a destination");
            }
            if (destination.startsWith(ROOMS_PREFIX)) {
                UUID roomId = parseUuidOr403(destination.substring(ROOMS_PREFIX.length()),
                        "Invalid room destination");
                User user = currentUser(accessor);
                if (banRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
                    throw new AccessDeniedException("Banned from this room");
                }
                roomService.requireMember(roomId, user.getId());
                return message;
            }
            if (destination.startsWith(PRESENCE_PREFIX)) {
                // Presence is visible to every authenticated user; the CONNECT
                // gate above already enforced that. Validate shape only.
                parseUuidOr403(destination.substring(PRESENCE_PREFIX.length()),
                        "Invalid presence destination");
                return message;
            }
            if (destination.startsWith(DIALOGS_PREFIX)) {
                UUID conversationId = parseUuidOr403(
                        destination.substring(DIALOGS_PREFIX.length()),
                        "Invalid dialog destination");
                User user = currentUser(accessor);
                if (!dialogService.isParticipant(conversationId, user.getId())) {
                    throw new AccessDeniedException("Not a participant in this dialog");
                }
                return message;
            }
            if (destination.startsWith(USERS_PREFIX)) {
                UUID userId = parseUuidOr403(
                        destination.substring(USERS_PREFIX.length()),
                        "Invalid user destination");
                User user = currentUser(accessor);
                if (!user.getId().equals(userId)) {
                    throw new AccessDeniedException("Cannot subscribe to another user's topic");
                }
                return message;
            }
            throw new AccessDeniedException("Unsupported subscribe destination");
        }
        if (command == StompCommand.SEND) {
            throw new AccessDeniedException("Clients may not send to STOMP destinations");
        }
        return message;
    }

    private User currentUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new BadCredentialsException("Session required");
        }
        return userService.requireByUsername(accessor.getUser().getName());
    }

    private static UUID parseUuidOr403(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException(message);
        }
    }
}
