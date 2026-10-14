-- Messenger-style chat features. Apply after 019_realtime_chat_foundation.sql.

ALTER TABLE chat_messages
  ADD COLUMN IF NOT EXISTS forwarded_from_message_id BIGINT REFERENCES chat_messages(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS metadata_json TEXT;

ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS chk_chat_messages_type;
ALTER TABLE chat_messages ADD CONSTRAINT chk_chat_messages_type CHECK (
  message_type IN ('TEXT', 'FILE', 'MIXED', 'SYSTEM', 'IMAGE', 'VOICE', 'GIF', 'STICKER', 'POLL')
);

ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS chk_chat_messages_payload;
ALTER TABLE chat_messages ADD CONSTRAINT chk_chat_messages_payload CHECK (
  deleted_at IS NOT NULL
  OR message_type = 'SYSTEM'
  OR content IS NOT NULL
  OR metadata_json IS NOT NULL
  OR message_type IN ('FILE', 'MIXED', 'IMAGE', 'VOICE')
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_forwarded_from
  ON chat_messages(forwarded_from_message_id)
  WHERE forwarded_from_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_content_search
  ON chat_messages USING GIN (to_tsvector('simple', COALESCE(content, '')))
  WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS chat_message_reactions (
  id BIGSERIAL PRIMARY KEY,
  message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  emoji VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_chat_message_reactions_message_user_emoji UNIQUE (message_id, user_id, emoji),
  CONSTRAINT chk_chat_message_reactions_emoji CHECK (length(trim(emoji)) BETWEEN 1 AND 16)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_reactions_message
  ON chat_message_reactions(message_id, created_at, id);
