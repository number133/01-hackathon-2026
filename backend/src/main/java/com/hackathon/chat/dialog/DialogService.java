package com.hackathon.chat.dialog;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.contact.FriendService;
import com.hackathon.chat.contact.OrderedPair;
import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.message.Message;
import com.hackathon.chat.message.MessageRepository;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DialogService {

    private final DialogRepository dialogRepository;
    private final ConversationService conversationService;
    private final FriendService friendService;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public DialogService(DialogRepository dialogRepository,
                         ConversationService conversationService,
                         FriendService friendService,
                         UserRepository userRepository,
                         MessageRepository messageRepository) {
        this.dialogRepository = dialogRepository;
        this.conversationService = conversationService;
        this.friendService = friendService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    public DialogView getOrCreate(UUID callerId, UUID counterpartId) {
        if (callerId.equals(counterpartId)) {
            throw new AccountConflictException("Cannot open a dialog with yourself");
        }
        if (friendService.isBlockedBetween(callerId, counterpartId)) {
            throw new AccountConflictException("User not reachable");
        }
        if (!friendService.areFriends(callerId, counterpartId)) {
            throw new AccountConflictException("Must be friends to open a dialog");
        }
        OrderedPair pair = OrderedPair.of(callerId, counterpartId);
        Optional<Dialog> existing = dialogRepository.findByUserAIdAndUserBId(pair.low(), pair.high());
        Dialog d = existing.orElseGet(() -> createFresh(pair));
        return toView(d, callerId, false);
    }

    private Dialog createFresh(OrderedPair pair) {
        Conversation conv = conversationService.create(Conversation.TYPE_DIALOG);
        try {
            return dialogRepository.save(new Dialog(conv.getId(), pair.low(), pair.high()));
        } catch (DataIntegrityViolationException race) {
            // Concurrent create won — drop the stray conversation and read the winner.
            conversationService.deleteConversation(conv.getId());
            return dialogRepository.findByUserAIdAndUserBId(pair.low(), pair.high())
                    .orElseThrow(() -> race);
        }
    }

    @Transactional(readOnly = true)
    public Dialog require(UUID conversationId) {
        return dialogRepository.findById(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Dialog not found"));
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return dialogRepository.findById(conversationId)
                .map(d -> d.hasParticipant(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isFrozen(UUID conversationId) {
        Dialog d = require(conversationId);
        return isFrozenFor(d);
    }

    private boolean isFrozenFor(Dialog d) {
        if (friendService.isBlockedBetween(d.getUserAId(), d.getUserBId())) return true;
        return !friendService.areFriends(d.getUserAId(), d.getUserBId());
    }

    public void assertCanSend(UUID conversationId, UUID callerId) {
        Dialog d = require(conversationId);
        if (!d.hasParticipant(callerId)) {
            throw new ForbiddenException("Not a participant");
        }
        if (isFrozenFor(d)) {
            throw new AccountConflictException("Dialog is frozen");
        }
    }

    public void assertCanMutate(UUID conversationId) {
        Dialog d = require(conversationId);
        if (isFrozenFor(d)) {
            throw new AccountConflictException("Dialog is frozen");
        }
    }

    public void assertReadable(UUID conversationId, UUID callerId) {
        Dialog d = require(conversationId);
        if (!d.hasParticipant(callerId)) {
            throw new ForbiddenException("Not a participant");
        }
    }

    @Transactional(readOnly = true)
    public List<DialogView> list(UUID callerId) {
        List<Dialog> rows = dialogRepository.findAllTouching(callerId);
        if (rows.isEmpty()) return List.of();
        List<UUID> others = rows.stream()
                .map(d -> d.otherUser(callerId))
                .toList();
        Map<UUID, User> byId = new HashMap<>();
        userRepository.findAllById(others).forEach(u -> byId.put(u.getId(), u));

        Map<UUID, java.time.Instant> lastAt = new HashMap<>();
        for (Dialog d : rows) {
            messageRepository
                    .findLatest(d.getConversationId(),
                            org.springframework.data.domain.PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .map(Message::getCreatedAt)
                    .ifPresent(t -> lastAt.put(d.getConversationId(), t));
        }

        List<DialogView> out = new ArrayList<>(rows.size());
        for (Dialog d : rows) {
            UUID otherId = d.otherUser(callerId);
            User other = byId.get(otherId);
            out.add(new DialogView(
                    d.getConversationId(),
                    otherId,
                    other == null ? "(deleted)" : other.getUsername(),
                    isFrozenFor(d),
                    lastAt.get(d.getConversationId())));
        }
        out.sort(Comparator.comparing(
                (DialogView v) -> v.lastMessageAt(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    @Transactional(readOnly = true)
    public DialogView view(UUID conversationId, UUID callerId) {
        Dialog d = require(conversationId);
        if (!d.hasParticipant(callerId)) {
            throw new ForbiddenException("Not a participant");
        }
        UUID otherId = d.otherUser(callerId);
        User other = userRepository.findById(otherId).orElse(null);
        java.time.Instant last = messageRepository
                .findLatest(d.getConversationId(),
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().map(Message::getCreatedAt).orElse(null);
        return new DialogView(
                d.getConversationId(),
                otherId,
                other == null ? "(deleted)" : other.getUsername(),
                isFrozenFor(d),
                last);
    }

    private DialogView toView(Dialog d, UUID callerId, boolean forceFrozen) {
        UUID otherId = d.otherUser(callerId);
        User other = userRepository.findById(otherId).orElse(null);
        return new DialogView(
                d.getConversationId(),
                otherId,
                other == null ? "(deleted)" : other.getUsername(),
                forceFrozen || isFrozenFor(d),
                null);
    }
}
