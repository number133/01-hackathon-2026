package com.hackathon.chat.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {

    @Query("SELECT fr FROM FriendRequest fr "
            + "WHERE fr.requesterId = :requester AND fr.recipientId = :recipient "
            + "AND fr.status = 'pending'")
    Optional<FriendRequest> findOpenBetween(@Param("requester") UUID requester,
                                            @Param("recipient") UUID recipient);

    @Query("SELECT fr FROM FriendRequest fr "
            + "WHERE fr.requesterId = :userId OR fr.recipientId = :userId")
    List<FriendRequest> findAllTouching(@Param("userId") UUID userId);

    @Query("SELECT fr FROM FriendRequest fr "
            + "WHERE fr.recipientId = :userId")
    List<FriendRequest> findAllIncoming(@Param("userId") UUID userId);

    @Query("SELECT fr FROM FriendRequest fr "
            + "WHERE fr.requesterId = :userId")
    List<FriendRequest> findAllOutgoing(@Param("userId") UUID userId);

    @Query("SELECT fr FROM FriendRequest fr "
            + "WHERE ((fr.requesterId = :a AND fr.recipientId = :b) "
            + "   OR (fr.requesterId = :b AND fr.recipientId = :a)) "
            + "AND fr.status = 'pending'")
    List<FriendRequest> findAllPendingBetween(@Param("a") UUID a, @Param("b") UUID b);
}
