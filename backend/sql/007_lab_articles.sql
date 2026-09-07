CREATE TABLE IF NOT EXISTS lab_articles (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  slug VARCHAR(500) NOT NULL,
  excerpt TEXT,
  content JSONB NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_lab_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
  CONSTRAINT chk_lab_articles_published_at CHECK (
    status <> 'PUBLISHED' OR published_at IS NOT NULL
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lab_articles_active_slug
  ON lab_articles (slug)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lab_articles_public_published_created_id
  ON lab_articles (published_at DESC, created_at DESC, id DESC)
  WHERE deleted_at IS NULL AND status = 'PUBLISHED';

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON lab_articles TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE lab_articles_id_seq TO smartlab_user;
  END IF;
END $$;
