-- D6 Group lifecycle. Apply after 021_chat_attachment_access.sql.
BEGIN;

ALTER TABLE chat_conversations ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM chat_conversations c
    LEFT JOIN chat_conversation_members m
      ON m.conversation_id = c.id AND m.left_at IS NULL AND m.membership_role = 'OWNER'
    WHERE c.conversation_type = 'GROUP' AND c.closed_at IS NULL
    GROUP BY c.id HAVING count(m.id) <> 1
  ) THEN
    RAISE EXCEPTION 'Active chat groups must have exactly one active owner; audit before migration';
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_group_active_owner
  ON chat_conversation_members(conversation_id)
  WHERE membership_role = 'OWNER' AND left_at IS NULL;

COMMIT;
