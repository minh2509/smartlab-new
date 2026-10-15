-- D5 Messenger: private chat attachment access
-- Apply after 020_chat_messenger_features.sql.

BEGIN;

-- A legacy LAB file may have been reused by another feature. Converting such a
-- file in place would silently revoke that feature's access. Stop for a manual
-- data audit instead of guessing which audience should own the shared file.
DO $$
DECLARE
  shared_file_id BIGINT;
BEGIN
  SELECT cmf.file_id INTO shared_file_id
  FROM chat_message_files cmf
  WHERE EXISTS (SELECT 1 FROM member_profiles mp WHERE mp.avatar_file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM research_fields rf WHERE rf.cover_file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM posts p WHERE p.cover_file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM documents d WHERE d.current_file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM document_versions dv WHERE dv.file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM task_attachments ta WHERE ta.file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM lab_achievement_files af WHERE af.file_id = cmf.file_id)
     OR EXISTS (SELECT 1 FROM gallery_items gi WHERE gi.file_id = cmf.file_id)
  LIMIT 1;
  IF shared_file_id IS NOT NULL THEN
    RAISE EXCEPTION 'Chat file % is also referenced outside chat; audit and separate the file before migration 021', shared_file_id;
  END IF;
END $$;
ALTER TABLE files
  DROP CONSTRAINT IF EXISTS chk_files_access_scope;

ALTER TABLE files
  ADD CONSTRAINT chk_files_access_scope
  CHECK (access_scope IN ('PUBLIC', 'PRIVATE', 'PROJECT', 'LAB', 'CHAT'));

-- Existing messenger attachments were uploaded as LAB and were therefore readable by
-- every authenticated lab member. Restrict every linked attachment to active members
-- of at least one conversation that references it.
UPDATE files
SET access_scope = 'CHAT'
WHERE id IN (SELECT DISTINCT file_id FROM chat_message_files)
  AND access_scope <> 'CHAT';

COMMIT;
