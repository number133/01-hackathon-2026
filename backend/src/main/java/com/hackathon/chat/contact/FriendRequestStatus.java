package com.hackathon.chat.contact;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    SUPERSEDED;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }

    public static FriendRequestStatus ofDb(String db) {
        return valueOf(db.toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
