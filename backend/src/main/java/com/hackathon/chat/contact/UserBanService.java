package com.hackathon.chat.contact;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import com.hackathon.chat.ws.UserEventPublisher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserBanService {

    private final UserBanRepository banRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final UserEventPublisher events;

    public UserBanService(UserBanRepository banRepository,
                          FriendshipRepository friendshipRepository,
                          FriendRequestRepository requestRepository,
                          UserRepository userRepository,
                          UserEventPublisher events) {
        this.banRepository = banRepository;
        this.friendshipRepository = friendshipRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    public void ban(UUID ownerId, UUID targetId) {
        if (ownerId.equals(targetId)) {
            throw new AccountConflictException("Cannot ban yourself");
        }
        if (!banRepository.existsByOwnerIdAndTargetId(ownerId, targetId)) {
            banRepository.save(new UserBan(ownerId, targetId));
        }
        boolean hadFriendship = removeFriendship(ownerId, targetId);
        supersedePendingBetween(ownerId, targetId);
        events.publish(ownerId, "user-ban.added", Map.of("userId", targetId.toString()));
        if (hadFriendship) {
            events.publish(ownerId, "friend.removed", Map.of("userId", targetId.toString()));
            events.publish(targetId, "friend.removed", Map.of("userId", ownerId.toString()));
        }
    }

    public void unban(UUID ownerId, UUID targetId) {
        banRepository.findByOwnerIdAndTargetId(ownerId, targetId)
                .ifPresent(banRepository::delete);
        events.publish(ownerId, "user-ban.removed", Map.of("userId", targetId.toString()));
    }

    @Transactional(readOnly = true)
    public List<UserBanView> list(UUID ownerId) {
        List<UserBan> rows = banRepository.findAllByOwnerId(ownerId);
        if (rows.isEmpty()) return List.of();
        List<UUID> ids = rows.stream().map(UserBan::getTargetId).toList();
        Map<UUID, User> byId = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> byId.put(u.getId(), u));
        List<UserBanView> out = new ArrayList<>(rows.size());
        for (UserBan b : rows) {
            User u = byId.get(b.getTargetId());
            out.add(new UserBanView(
                    b.getTargetId(),
                    u == null ? "(deleted)" : u.getUsername(),
                    b.getCreatedAt()));
        }
        out.sort(Comparator.comparing(UserBanView::username, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private boolean removeFriendship(UUID a, UUID b) {
        OrderedPair pair = OrderedPair.of(a, b);
        FriendshipId id = new FriendshipId(pair.low(), pair.high());
        if (friendshipRepository.existsById(id)) {
            friendshipRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private void supersedePendingBetween(UUID a, UUID b) {
        requestRepository.findAllPendingBetween(a, b)
                .forEach(fr -> fr.resolve(FriendRequestStatus.SUPERSEDED));
    }
}
