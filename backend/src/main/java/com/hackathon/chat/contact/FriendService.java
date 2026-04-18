package com.hackathon.chat.contact;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import com.hackathon.chat.user.UserService;
import com.hackathon.chat.ws.UserEventPublisher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FriendService {

    private final FriendRequestRepository requestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserBanRepository banRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserEventPublisher events;

    public FriendService(FriendRequestRepository requestRepository,
                         FriendshipRepository friendshipRepository,
                         UserBanRepository banRepository,
                         UserRepository userRepository,
                         UserService userService,
                         UserEventPublisher events) {
        this.requestRepository = requestRepository;
        this.friendshipRepository = friendshipRepository;
        this.banRepository = banRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.events = events;
    }

    public FriendRequestView sendRequest(UUID requesterId, String username, String message) {
        User recipient;
        try {
            recipient = userService.requireByUsername(username);
        } catch (Exception ex) {
            throw new NoSuchElementException("User not found");
        }
        if (recipient.getId().equals(requesterId)) {
            throw new AccountConflictException("Cannot friend yourself");
        }
        if (isBlockedBetween(requesterId, recipient.getId())) {
            throw new AccountConflictException("User not reachable");
        }
        if (areFriends(requesterId, recipient.getId())) {
            throw new AccountConflictException("Already friends");
        }
        if (requestRepository.findOpenBetween(requesterId, recipient.getId()).isPresent()) {
            throw new AccountConflictException("A pending request already exists");
        }
        if (requestRepository.findOpenBetween(recipient.getId(), requesterId).isPresent()) {
            throw new AccountConflictException(
                    "Incoming request pending from this user — accept that instead");
        }

        FriendRequest saved = requestRepository.save(
                new FriendRequest(requesterId, recipient.getId(), emptyToNull(message)));
        FriendRequestView view = toView(saved);
        events.publish(recipient.getId(), "friend-request.created", view);
        return view;
    }

    public FriendRequestView accept(UUID requestId, UUID callerId) {
        FriendRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!req.getRecipientId().equals(callerId)) {
            throw new ForbiddenException("Not the recipient");
        }
        if (req.getStatus() != FriendRequestStatus.PENDING) {
            throw new AccountConflictException("Request is not pending");
        }
        if (isBlockedBetween(req.getRequesterId(), req.getRecipientId())) {
            throw new AccountConflictException("Cannot accept — one side has blocked the other");
        }

        OrderedPair pair = OrderedPair.of(req.getRequesterId(), req.getRecipientId());
        try {
            friendshipRepository.save(new Friendship(pair.low(), pair.high()));
        } catch (DataIntegrityViolationException dup) {
            // Already friends (concurrent accept) — idempotent.
        }
        req.resolve(FriendRequestStatus.ACCEPTED);

        supersedePendingBetween(req.getRequesterId(), req.getRecipientId(), req.getId());

        FriendRequestView view = toView(req);
        events.publish(req.getRequesterId(), "friend-request.resolved", view);
        events.publish(req.getRequesterId(), "friend.added", Map.of(
                "userId", req.getRecipientId().toString()));
        events.publish(req.getRecipientId(), "friend.added", Map.of(
                "userId", req.getRequesterId().toString()));
        return view;
    }

    public FriendRequestView decline(UUID requestId, UUID callerId) {
        FriendRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!req.getRecipientId().equals(callerId)) {
            throw new ForbiddenException("Not the recipient");
        }
        if (req.getStatus() != FriendRequestStatus.PENDING) {
            throw new AccountConflictException("Request is not pending");
        }
        req.resolve(FriendRequestStatus.DECLINED);
        FriendRequestView view = toView(req);
        events.publish(req.getRequesterId(), "friend-request.resolved", view);
        return view;
    }

    public FriendRequestView revoke(UUID requestId, UUID callerId) {
        FriendRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!req.getRequesterId().equals(callerId)) {
            throw new ForbiddenException("Not the requester");
        }
        if (req.getStatus() != FriendRequestStatus.PENDING) {
            throw new AccountConflictException("Request is not pending");
        }
        req.resolve(FriendRequestStatus.REVOKED);
        return toView(req);
    }

    public void unfriend(UUID callerId, UUID otherId) {
        OrderedPair pair = OrderedPair.of(callerId, otherId);
        friendshipRepository.deleteById(new FriendshipId(pair.low(), pair.high()));
        events.publish(callerId, "friend.removed", Map.of("userId", otherId.toString()));
        events.publish(otherId, "friend.removed", Map.of("userId", callerId.toString()));
    }

    @Transactional(readOnly = true)
    public List<FriendView> listFriends(UUID userId) {
        List<Friendship> rows = friendshipRepository.findAllTouching(userId);
        if (rows.isEmpty()) return List.of();
        List<UUID> others = new ArrayList<>(rows.size());
        for (Friendship f : rows) {
            others.add(f.getUserAId().equals(userId) ? f.getUserBId() : f.getUserAId());
        }
        Map<UUID, User> byId = new HashMap<>();
        userRepository.findAllById(others).forEach(u -> byId.put(u.getId(), u));
        List<FriendView> out = new ArrayList<>(rows.size());
        for (Friendship f : rows) {
            UUID otherId = f.getUserAId().equals(userId) ? f.getUserBId() : f.getUserAId();
            User other = byId.get(otherId);
            if (other == null) continue;
            out.add(new FriendView(other.getId(), other.getUsername(), f.getEstablishedAt()));
        }
        out.sort(Comparator.comparing(FriendView::username, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional(readOnly = true)
    public List<FriendRequestView> list(UUID userId, String direction) {
        List<FriendRequest> rows;
        String d = direction == null ? "both" : direction.toLowerCase();
        rows = switch (d) {
            case "incoming" -> requestRepository.findAllIncoming(userId);
            case "outgoing" -> requestRepository.findAllOutgoing(userId);
            default -> requestRepository.findAllTouching(userId);
        };
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID a, UUID b) {
        if (a.equals(b)) return false;
        OrderedPair pair = OrderedPair.of(a, b);
        return friendshipRepository.existsById(new FriendshipId(pair.low(), pair.high()));
    }

    @Transactional(readOnly = true)
    public boolean isBlockedBetween(UUID a, UUID b) {
        return banRepository.existsByOwnerIdAndTargetId(a, b)
                || banRepository.existsByOwnerIdAndTargetId(b, a);
    }

    private void supersedePendingBetween(UUID a, UUID b, UUID exceptId) {
        List<FriendRequest> pending = requestRepository.findAllPendingBetween(a, b);
        for (FriendRequest fr : pending) {
            if (fr.getId().equals(exceptId)) continue;
            fr.resolve(FriendRequestStatus.SUPERSEDED);
        }
    }

    private FriendRequestView toView(FriendRequest fr) {
        Map<UUID, User> byId = new HashMap<>();
        userRepository.findAllById(List.of(fr.getRequesterId(), fr.getRecipientId()))
                .forEach(u -> byId.put(u.getId(), u));
        FriendRequestView.UserRef reqRef = userRefOrDeleted(fr.getRequesterId(), byId);
        FriendRequestView.UserRef recRef = userRefOrDeleted(fr.getRecipientId(), byId);
        return new FriendRequestView(
                fr.getId(), reqRef, recRef, fr.getMessage(),
                fr.getStatus(), fr.getCreatedAt(), fr.getResolvedAt());
    }

    private static FriendRequestView.UserRef userRefOrDeleted(UUID id, Map<UUID, User> byId) {
        User u = byId.get(id);
        return new FriendRequestView.UserRef(id, u == null ? "(deleted)" : u.getUsername());
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
