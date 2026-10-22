-- Preserve the original request identity across message edits, deletion and poll votes.
-- Historical rows remain nullable because their original request cannot be reconstructed.
ALTER TABLE chat_messages
  ADD COLUMN IF NOT EXISTS original_payload_fingerprint VARCHAR(64);
