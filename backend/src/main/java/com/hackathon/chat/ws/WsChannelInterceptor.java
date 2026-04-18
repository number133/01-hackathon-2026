package com.hackathon.chat.ws;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class WsChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsChannelInterceptor.class);
    private static final String TOPIC_PREFIX = "/topic/rooms/";

    private final UserService userService;
    private final RoomService roomService;
    private final RoomBanRepository banRepository;

    public WsChannelInterceptor(UserService userService,
                                RoomService roomService,
                                RoomBanRepository banRepository) {
        this.userService = userService;
        this.roomService = roomService;
        this.banRepository = banRepository;
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
            if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
                throw new AccessDeniedException("Only /topic/rooms/{id} subscriptions are allowed");
            }
            UUID roomId = parseRoomId(destination.substring(TOPIC_PREFIX.length()));
            User user = currentUser(accessor);
            if (banRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
                throw new AccessDeniedException("Banned from this room");
            }
            roomService.requireMember(roomId, user.getId());
            return message;
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

    private static UUID parseRoomId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid room destination");
        }
    }
}
