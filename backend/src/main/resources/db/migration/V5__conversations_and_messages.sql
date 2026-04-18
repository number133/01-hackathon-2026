CREATE TABLE conversation (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('room', 'dialog')),
    last_seq   BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE room ADD COLUMN conversation_id UUID
    REFERENCES conversation(id) ON DELETE CASCADE;

WITH new_convs AS (
    SELECT r.id AS room_id, gen_random_uuid() AS conv_id
    FROM room r
    WHERE r.conversation_id IS NULL
),
inserted AS (
    INSERT INTO conversation (id, type)
    SELECT conv_id, 'room' FROM new_convs
    RETURNING id
)
UPDATE room r
SET    conversation_id = nc.conv_id
FROM   new_convs nc
WHERE  r.id = nc.room_id;

ALTER TABLE room
    ALTER COLUMN conversation_id SET NOT NULL;
ALTER TABLE room
    ADD CONSTRAINT ux_room_conversation UNIQUE (conversation_id);

CREATE TABLE message (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID         NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    seq             BIGINT       NOT NULL,
    author_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    body            TEXT         NOT NULL CHECK (octet_length(body) <= 3072),
    reply_to_id     UUID         REFERENCES message(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_message_conversation_seq ON message (conversation_id, seq);
CREATE INDEX idx_message_conversation_seq_desc ON message (conversation_id, seq DESC);
CREATE INDEX idx_message_author ON message (author_id);
