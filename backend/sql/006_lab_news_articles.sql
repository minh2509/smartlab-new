CREATE TABLE IF NOT EXISTS lab_news_articles (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  excerpt TEXT,
  source_name VARCHAR(255) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  published_at TIMESTAMPTZ NOT NULL,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_lab_news_articles_public_published_created_id
  ON lab_news_articles (published_at DESC, created_at DESC, id DESC)
  WHERE deleted_at IS NULL AND is_public = TRUE;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON lab_news_articles TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE lab_news_articles_id_seq TO smartlab_user;
  END IF;
END $$;
