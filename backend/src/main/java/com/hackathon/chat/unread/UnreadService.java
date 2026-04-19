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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final SimpMessagingTemplate broker;

    public UnreadService(UnreadRepository repository,
                         ConversationService conversationService,
                         ConversationParticipantsQuery participantsQuery,
                         RoomRepository roomRepository,
                         RoomMemberRepository roomMemberRepository,
                         DialogRepository dialogRepository,
                         UserEventPublisher events,
                         SimpMessagingTemplate broker) {
        this.repository = repository;
        this.conversationService = conversationService;
        this.participantsQuery = participantsQuery;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.dialogRepository = dialogRepository;
        this.events = events;
        this.broker = broker;
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

        // One broadcast per message, regardless of participant count.
        // Clients subscribed to the per-conversation topic derive their own
        // count as (newSeq - clientLastRead).
        afterCommit(() -> broker.convertAndSend(
                "/topic/conversations/" + conversationId + "/unread",
                Map.of("conversationId", conversationId.toString(),
                        "authorId", authorId.toString(),
                        "lastSeq", newSeq)));
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
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
