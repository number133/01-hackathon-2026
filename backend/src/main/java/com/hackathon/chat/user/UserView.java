package com.hackathon.chat.user;

import java.util.UUID;

public record UserView(UUID id, String username, String email) {

    public static UserView of(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getEmail());
    }
}
