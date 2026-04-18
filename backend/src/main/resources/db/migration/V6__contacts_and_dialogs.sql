CREATE TABLE friend_request (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message      VARCHAR(500),
    status       VARCHAR(12)  NOT NULL
                    CHECK (status IN ('pending','accepted','declined',
                                      'revoked','superseded')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    CHECK (requester_id <> recipient_id)
);

CREATE UNIQUE INDEX ux_friend_request_open
    ON friend_request (requester_id, recipient_id)
    WHERE status = 'pending';

CREATE INDEX idx_friend_request_recipient
    ON friend_request (recipient_id, status);

CREATE TABLE friendship (
    user_a_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    established_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_a_id, user_b_id),
    CHECK (user_a_id < user_b_id)
);

CREATE INDEX idx_friendship_b ON friendship (user_b_id);

CREATE TABLE user_ban (
    owner_id   UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id  UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, target_id),
    CHECK (owner_id <> target_id)
);

CREATE INDEX idx_user_ban_target ON user_ban (target_id);

CREATE TABLE dialog (
    conversation_id UUID         PRIMARY KEY REFERENCES conversation(id) ON DELETE CASCADE,
    user_a_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (user_a_id < user_b_id)
);

CREATE UNIQUE INDEX ux_dialog_pair ON dialog (user_a_id, user_b_id);
CREATE INDEX idx_dialog_b ON dialog (user_b_id);
