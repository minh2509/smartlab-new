ALTER TABLE research_fields
  ADD COLUMN IF NOT EXISTS cover_file_id BIGINT NULL;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_research_fields_cover_file'
      AND conrelid = 'research_fields'::regclass
  ) THEN
    ALTER TABLE research_fields
      ADD CONSTRAINT fk_research_fields_cover_file
      FOREIGN KEY (cover_file_id) REFERENCES files(id) ON DELETE SET NULL;
  END IF;
END $$;
