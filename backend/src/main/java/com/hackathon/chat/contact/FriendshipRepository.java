package com.hackathon.chat.contact;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {

    @Query("SELECT f FROM Friendship f WHERE f.userAId = :userId OR f.userBId = :userId")
    List<Friendship> findAllTouching(@Param("userId") UUID userId);
}
