CREATE TABLE attachment (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID         REFERENCES message(id) ON DELETE CASCADE,
    conversation_id UUID         NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    uploader_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    stored_path     VARCHAR(500) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL CHECK (size_bytes >= 0),
    comment         VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_message      ON attachment (message_id);
CREATE INDEX idx_attachment_conversation ON attachment (conversation_id);
CREATE INDEX idx_attachment_orphans      ON attachment (created_at)
    WHERE message_id IS NULL;
