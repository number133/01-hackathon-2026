CREATE TABLE unread_marker (
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    last_read_seq   BIGINT      NOT NULL DEFAULT 0 CHECK (last_read_seq >= 0),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, conversation_id)
);

CREATE INDEX idx_unread_marker_user ON unread_marker (user_id);

-- Backfill: one marker per existing (user, conversation) membership at the
-- conversation's current last_seq so nothing pre-V8 is flagged as unread.
INSERT INTO unread_marker (user_id, conversation_id, last_read_seq)
SELECT rm.user_id, r.conversation_id, c.last_seq
FROM   room_member rm
JOIN   room r          ON r.id = rm.room_id
JOIN   conversation c  ON c.id = r.conversation_id
ON CONFLICT (user_id, conversation_id) DO NOTHING;

INSERT INTO unread_marker (user_id, conversation_id, last_read_seq)
SELECT d.user_a_id, d.conversation_id, c.last_seq
FROM   dialog d
JOIN   conversation c ON c.id = d.conversation_id
ON CONFLICT (user_id, conversation_id) DO NOTHING;

INSERT INTO unread_marker (user_id, conversation_id, last_read_seq)
SELECT d.user_b_id, d.conversation_id, c.last_seq
FROM   dialog d
JOIN   conversation c ON c.id = d.conversation_id
ON CONFLICT (user_id, conversation_id) DO NOTHING;
