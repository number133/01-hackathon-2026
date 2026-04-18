package com.hackathon.chat.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friendship")
@IdClass(FriendshipId.class)
public class Friendship {

    @Id
    @Column(name = "user_a_id")
    private UUID userAId;

    @Id
    @Column(name = "user_b_id")
    private UUID userBId;

    @Column(name = "established_at", nullable = false, updatable = false)
    private Instant establishedAt;

    protected Friendship() {
    }

    public Friendship(UUID userAId, UUID userBId) {
        int cmp = OrderedPair.unsignedCompare(userAId, userBId);
        if (cmp == 0) {
            throw new IllegalArgumentException("user_a_id must differ from user_b_id");
        }
        this.userAId = cmp < 0 ? userAId : userBId;
        this.userBId = cmp < 0 ? userBId : userAId;
        this.establishedAt = Instant.now();
    }

    public UUID getUserAId() {
        return userAId;
    }

    public UUID getUserBId() {
        return userBId;
    }

    public Instant getEstablishedAt() {
        return establishedAt;
    }
}
