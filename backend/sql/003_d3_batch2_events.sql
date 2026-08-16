-- D3 batch 2: event management without participant registration.
-- This migration is intentionally rerunnable and preserves nullable legacy fields.

CREATE TABLE IF NOT EXISTS events (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  mode VARCHAR(20) NOT NULL DEFAULT 'IN_PERSON',
  location VARCHAR(255),
  meeting_url VARCHAR(2048),
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
  visibility VARCHAR(20) NOT NULL DEFAULT 'LAB',
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

ALTER TABLE events
  ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NOT NULL DEFAULT 'IN_PERSON',
  ADD COLUMN IF NOT EXISTS meeting_url VARCHAR(2048),
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_events_mode'
      AND conrelid = 'events'::regclass
  ) THEN
    ALTER TABLE events
      ADD CONSTRAINT chk_events_mode
      CHECK (mode IN ('IN_PERSON', 'ONLINE'));
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_events_status'
      AND conrelid = 'events'::regclass
  ) THEN
    ALTER TABLE events
      ADD CONSTRAINT chk_events_status
      CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'));
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_events_visibility'
      AND conrelid = 'events'::regclass
  ) THEN
    ALTER TABLE events
      ADD CONSTRAINT chk_events_visibility
      CHECK (visibility IN ('PUBLIC', 'LAB', 'PROJECT'));
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_events_active_start_id
  ON events (start_at, id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_events_active_project_status_start
  ON events (project_id, status, start_at, id)
  WHERE deleted_at IS NULL;
