package com.hackathon.chat.unread;

import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.dialog.Dialog;
import com.hackathon.chat.dialog.DialogRepository;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomMember;
import com.hackathon.chat.room.RoomMemberRepository;
import com.hackathon.chat.room.RoomRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConversationParticipantsQuery {

    private final ConversationService conversationService;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final DialogRepository dialogRepository;

    public ConversationParticipantsQuery(ConversationService conversationService,
                                         RoomRepository roomRepository,
                                         RoomMemberRepository roomMemberRepository,
                                         DialogRepository dialogRepository) {
        this.conversationService = conversationService;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.dialogRepository = dialogRepository;
    }

    public Set<UUID> participants(UUID conversationId) {
        Conversation conv = conversationService.require(conversationId);
        if (Conversation.TYPE_DIALOG.equals(conv.getType())) {
            return dialogRepository.findById(conversationId)
                    .map(d -> Set.of(d.getUserAId(), d.getUserBId()))
                    .orElse(Set.of());
        }
        Room room = roomRepository.findByConversationId(conversationId).orElse(null);
        if (room == null) return Set.of();
        List<RoomMember> members = roomMemberRepository.findAllByRoomId(room.getId());
        return members.stream().map(RoomMember::getUserId).collect(Collectors.toSet());
    }

    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participants(conversationId).contains(userId);
    }
}
