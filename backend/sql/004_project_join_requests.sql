-- SmartLab project join request lifecycle.
-- Idempotent schema extension; run after 001_smartlab_postgres_schema_seed.sql.

CREATE TABLE IF NOT EXISTS project_join_requests (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  requester_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  message VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_project_join_requests_status CHECK (
    status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_project_join_requests_pending_project_requester
  ON project_join_requests(project_id, requester_user_id)
  WHERE status = 'PENDING';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT USAGE ON SCHEMA public TO smartlab_user;
    GRANT SELECT, INSERT, UPDATE, DELETE
      ON project_join_requests
      TO smartlab_user;
    GRANT USAGE, SELECT
      ON SEQUENCE project_join_requests_id_seq
      TO smartlab_user;
  END IF;
END
$$;
