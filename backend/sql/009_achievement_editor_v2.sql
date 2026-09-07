-- Bootstrap the replacement achievement model before adding editor v2 fields.
-- This migration is new/unshipped, so it may safely establish the table needed
-- by both fresh installs and the 010 legacy-publication transition.
CREATE TABLE IF NOT EXISTS lab_achievements (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  summary TEXT,
  achievement_type VARCHAR(30) NOT NULL,
  achievement_year INTEGER NOT NULL,
  achievement_date DATE,
  evidence_url VARCHAR(2048),
  related_project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_lab_achievements_type CHECK (achievement_type IN ('RESEARCH_RESULT', 'PRODUCT', 'AWARD', 'CERTIFICATE', 'MILESTONE', 'OTHER')),
  CONSTRAINT chk_lab_achievements_year CHECK (achievement_year BETWEEN 1900 AND 2100),
  CONSTRAINT chk_lab_achievements_date_year CHECK (
    achievement_date IS NULL OR EXTRACT(YEAR FROM achievement_date) = achievement_year
  )
);

ALTER TABLE lab_achievements
  ADD COLUMN IF NOT EXISTS recognizing_organization VARCHAR(500);

CREATE TABLE IF NOT EXISTS lab_achievement_files (
  id BIGSERIAL PRIMARY KEY,
  achievement_id BIGINT NOT NULL REFERENCES lab_achievements(id),
  file_id BIGINT NOT NULL REFERENCES files(id),
  label VARCHAR(500),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_lab_achievement_files_sort_order CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lab_achievement_files_active_achievement_file
  ON lab_achievement_files (achievement_id, file_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lab_achievement_files_active_achievement_sort_id
  ON lab_achievement_files (achievement_id, sort_order, id)
  WHERE deleted_at IS NULL;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON lab_achievements TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE lab_achievements_id_seq TO smartlab_user;
    GRANT SELECT, INSERT, UPDATE, DELETE ON lab_achievement_files TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE lab_achievement_files_id_seq TO smartlab_user;
  END IF;
END $$;
