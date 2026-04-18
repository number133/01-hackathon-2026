package com.hackathon.chat.presence;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PresenceStatus {
    ONLINE,
    AFK,
    OFFLINE;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
