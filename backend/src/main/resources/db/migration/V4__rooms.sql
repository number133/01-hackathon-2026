CREATE TABLE room (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(80)  NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    visibility  VARCHAR(10)  NOT NULL
                CHECK (visibility IN ('public', 'private')),
    owner_id    UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_room_name_lower ON room (LOWER(name));
CREATE INDEX idx_room_owner ON room (owner_id);
CREATE INDEX idx_room_visibility_created ON room (visibility, created_at DESC);

CREATE TABLE room_member (
    room_id   UUID        NOT NULL REFERENCES room(id)  ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role      VARCHAR(10) NOT NULL
                CHECK (role IN ('owner', 'admin', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id)
);

CREATE INDEX idx_room_member_user ON room_member (user_id);

CREATE TABLE room_ban (
    room_id    UUID         NOT NULL REFERENCES room(id)  ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    reason     VARCHAR(200),
    banned_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE room_invitation (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id          UUID        NOT NULL REFERENCES room(id)  ON DELETE CASCADE,
    invitee_user_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    inviter_user_id  UUID        REFERENCES users(id) ON DELETE SET NULL,
    status           VARCHAR(10) NOT NULL
                        CHECK (status IN ('pending', 'accepted', 'declined', 'revoked')),
    message          VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_room_invitation_open
    ON room_invitation (room_id, invitee_user_id)
    WHERE status = 'pending';

CREATE INDEX idx_room_invitation_invitee ON room_invitation (invitee_user_id, status);
