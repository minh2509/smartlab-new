-- Realtime chat persistence foundation.
-- Apply after 018_document_categories.sql.
-- This migration is intentionally idempotent for the current SmartLab lineage.

CREATE TABLE IF NOT EXISTS chat_conversations (
  id BIGSERIAL PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,
  conversation_type VARCHAR(20) NOT NULL,
  title VARCHAR(200),
  direct_key VARCHAR(100),
  project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  last_message_seq BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_chat_conversations_type CHECK (conversation_type IN ('DIRECT', 'GROUP')),
  CONSTRAINT chk_chat_conversations_direct_key CHECK (
    (conversation_type = 'DIRECT' AND direct_key IS NOT NULL)
    OR (conversation_type = 'GROUP' AND direct_key IS NULL)
  ),
  CONSTRAINT chk_chat_conversations_last_message_seq CHECK (last_message_seq >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_conversations_direct_key
  ON chat_conversations(direct_key)
  WHERE direct_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_conversations_project
  ON chat_conversations(project_id)
  WHERE project_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS chat_conversation_members (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  membership_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at TIMESTAMPTZ,
  last_read_seq BIGINT NOT NULL DEFAULT 0,
  is_muted BOOLEAN NOT NULL DEFAULT FALSE,
  is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
  archived_at TIMESTAMPTZ,
  CONSTRAINT uk_chat_conversation_members_conversation_user UNIQUE (conversation_id, user_id),
  CONSTRAINT chk_chat_conversation_members_role CHECK (membership_role IN ('OWNER', 'ADMIN', 'MEMBER')),
  CONSTRAINT chk_chat_conversation_members_last_read_seq CHECK (last_read_seq >= 0)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_members_user_active
  ON chat_conversation_members(user_id, conversation_id)
  WHERE left_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_conversation_members_user_unread
  ON chat_conversation_members(user_id, last_read_seq)
  WHERE left_at IS NULL;

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
  message_seq BIGINT NOT NULL,
  sender_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  client_message_id VARCHAR(64),
  message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
  content TEXT,
  reply_to_message_id BIGINT REFERENCES chat_messages(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  edited_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ,
  CONSTRAINT uk_chat_messages_conversation_seq UNIQUE (conversation_id, message_seq),
  CONSTRAINT chk_chat_messages_seq CHECK (message_seq > 0),
  CONSTRAINT chk_chat_messages_type CHECK (message_type IN ('TEXT', 'FILE', 'MIXED', 'SYSTEM')),
  CONSTRAINT chk_chat_messages_payload CHECK (
    deleted_at IS NOT NULL
    OR message_type = 'SYSTEM'
    OR content IS NOT NULL
    OR message_type IN ('FILE', 'MIXED')
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_messages_sender_client_id
  ON chat_messages(sender_user_id, client_message_id)
  WHERE sender_user_id IS NOT NULL AND client_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_seq_desc
  ON chat_messages(conversation_id, message_seq DESC);

CREATE TABLE IF NOT EXISTS chat_message_files (
  id BIGSERIAL PRIMARY KEY,
  message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
  file_id BIGINT NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
  position INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uk_chat_message_files_message_file UNIQUE (message_id, file_id),
  CONSTRAINT chk_chat_message_files_position CHECK (position >= 0)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_files_message
  ON chat_message_files(message_id, position, id);
