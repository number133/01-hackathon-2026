package com.hackathon.chat.unread;

import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.dialog.Dialog;
import com.hackathon.chat.dialog.DialogRepository;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomMember;
import com.hackathon.chat.room.RoomMemberRepository;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.ws.UserEventPublisher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UnreadService {

    private final UnreadRepository repository;
    private final ConversationService conversationService;
    private final ConversationParticipantsQuery participantsQuery;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final DialogRepository dialogRepository;
    private final UserEventPublisher events;

    public UnreadService(UnreadRepository repository,
                         ConversationService conversationService,
                         ConversationParticipantsQuery participantsQuery,
                         RoomRepository roomRepository,
                         RoomMemberRepository roomMemberRepository,
                         DialogRepository dialogRepository,
                         UserEventPublisher events) {
        this.repository = repository;
        this.conversationService = conversationService;
        this.participantsQuery = participantsQuery;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.dialogRepository = dialogRepository;
        this.events = events;
    }

    public void initMarker(UUID userId, UUID conversationId, long atSeq) {
        UnreadMarkerId id = new UnreadMarkerId(userId, conversationId);
        if (repository.existsById(id)) return;
        try {
            repository.save(new UnreadMarker(userId, conversationId, atSeq));
        } catch (DataIntegrityViolationException concurrent) {
            // Another concurrent init — idempotent.
        }
    }

    public void bumpForMessage(UUID conversationId, UUID authorId, long newSeq) {
        Set<UUID> participants = participantsQuery.participants(conversationId);
        if (participants.isEmpty()) return;

        // Author's marker tracks last_seq so they don't see their own bump.
        UnreadMarkerId authorKey = new UnreadMarkerId(authorId, conversationId);
        UnreadMarker authorMarker = repository.findById(authorKey).orElse(null);
        if (authorMarker == null) {
            try {
                repository.save(new UnreadMarker(authorId, conversationId, newSeq));
            } catch (DataIntegrityViolationException ignore) {
                authorMarker = repository.findById(authorKey).orElse(null);
                if (authorMarker != null) authorMarker.setLastReadSeq(newSeq);
            }
        } else if (authorMarker.getLastReadSeq() < newSeq) {
            authorMarker.setLastReadSeq(newSeq);
        }

        List<UUID> otherIds = new ArrayList<>(participants);
        otherIds.remove(authorId);
        if (otherIds.isEmpty()) return;

        Map<UUID, Long> readBy = new HashMap<>();
        repository.findAllForUserInConversation(conversationId, otherIds)
                .forEach(m -> readBy.put(m.getUserId(), m.getLastReadSeq()));

        for (UUID userId : otherIds) {
            long lastRead = readBy.getOrDefault(userId, 0L);
            long count = Math.max(0L, newSeq - lastRead);
            events.publish(userId, "unread.updated",
                    Map.of("conversationId", conversationId.toString(), "count", count));
        }
    }

    public void markRead(UUID userId, UUID conversationId, long seq) {
        if (!participantsQuery.isParticipant(conversationId, userId)) {
            throw new ForbiddenException("Not a participant");
        }
        Conversation conv = conversationService.require(conversationId);
        long cap = Math.min(seq, conv.getLastSeq());
        UnreadMarkerId key = new UnreadMarkerId(userId, conversationId);
        UnreadMarker marker = repository.findById(key).orElse(null);
        if (marker == null) {
            try {
                repository.save(new UnreadMarker(userId, conversationId, cap));
            } catch (DataIntegrityViolationException concurrent) {
                marker = repository.findById(key).orElse(null);
                if (marker != null && marker.getLastReadSeq() < cap) {
                    marker.setLastReadSeq(cap);
                }
            }
        } else if (marker.getLastReadSeq() < cap) {
            marker.setLastReadSeq(cap);
        }
        events.publish(userId, "unread.updated",
                Map.of("conversationId", conversationId.toString(), "count", 0L));
    }

    public void catchUp(UUID userId, UUID conversationId) {
        Conversation conv = conversationService.require(conversationId);
        markRead(userId, conversationId, conv.getLastSeq());
    }

    @Transactional(readOnly = true)
    public List<UnreadView> snapshot(UUID userId) {
        Set<UUID> convIds = conversationIdsForUser(userId);
        if (convIds.isEmpty()) return List.of();

        List<UUID> convList = new ArrayList<>(convIds);
        Map<UUID, Long> lastSeqs = new HashMap<>();
        for (UUID id : convList) {
            lastSeqs.put(id, conversationService.require(id).getLastSeq());
        }

        Map<UUID, Long> reads = new HashMap<>();
        repository.findAllForUserIn(userId, convList)
                .forEach(m -> reads.put(m.getConversationId(), m.getLastReadSeq()));

        List<UnreadView> out = new ArrayList<>(convList.size());
        for (UUID id : convList) {
            long last = lastSeqs.getOrDefault(id, 0L);
            long read = reads.getOrDefault(id, 0L);
            out.add(new UnreadView(id, Math.max(0L, last - read)));
        }
        out.sort(Comparator.comparing(v -> v.conversationId().toString()));
        return out;
    }

    private Set<UUID> conversationIdsForUser(UUID userId) {
        Set<UUID> out = new HashSet<>();
        List<RoomMember> memberships = roomMemberRepository.findAllByUserId(userId);
        List<UUID> roomIds = memberships.stream().map(RoomMember::getRoomId).toList();
        for (UUID rid : roomIds) {
            roomRepository.findById(rid).map(Room::getConversationId).ifPresent(out::add);
        }
        for (Dialog d : dialogRepository.findAllTouching(userId)) {
            out.add(d.getConversationId());
        }
        return out;
    }
}
