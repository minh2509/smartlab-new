-- Landing page data contracts: project recruitment and research publications.

ALTER TABLE projects
  ADD COLUMN IF NOT EXISTS is_recruiting BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_projects_public_recruiting_created_id
  ON projects (created_at DESC, id DESC)
  WHERE deleted_at IS NULL
    AND is_public = TRUE
    AND is_recruiting = TRUE
    AND status IN ('PROPOSED', 'PREPARING', 'IN_PROGRESS');

CREATE TABLE IF NOT EXISTS research_publications (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  authors TEXT NOT NULL,
  publication_type VARCHAR(30) NOT NULL,
  venue VARCHAR(500) NOT NULL,
  publication_year INTEGER NOT NULL,
  publication_date DATE,
  doi VARCHAR(255),
  public_url VARCHAR(2048),
  summary TEXT,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_research_publications_type CHECK (publication_type IN ('JOURNAL_ARTICLE', 'CONFERENCE_PAPER', 'BOOK_CHAPTER', 'BOOK', 'TECHNICAL_REPORT', 'PREPRINT', 'OTHER')),
  CONSTRAINT chk_research_publications_year CHECK (publication_year BETWEEN 1900 AND 2100)
);

CREATE INDEX IF NOT EXISTS idx_research_publications_public_year_date_id
  ON research_publications (publication_year DESC, publication_date DESC, created_at DESC, id DESC)
  WHERE deleted_at IS NULL AND is_public = TRUE;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON research_publications TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE research_publications_id_seq TO smartlab_user;
  END IF;
END $$;
