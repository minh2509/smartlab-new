-- Transition the historical research_publications contract to the current
-- lab_achievements model. The source table is intentionally retained for
-- rollback/audit because it was created by the shipped 005 migration.

DO $$ BEGIN
  IF to_regclass('public.research_publications') IS NULL THEN
    RAISE EXCEPTION 'Migration 010 requires historical research_publications from migration 005';
  END IF;
END $$;

-- A duplicate run is valid only when an ID already represents the same
-- migrated source row. Refuse a conflicting pre-existing achievement instead
-- of silently discarding legacy data behind ON CONFLICT below.
DO $$ BEGIN
  IF EXISTS (
    SELECT 1
    FROM research_publications publication
    JOIN lab_achievements achievement ON achievement.id = publication.id
    WHERE achievement.title IS DISTINCT FROM publication.title
       OR achievement.achievement_type IS DISTINCT FROM
          CASE WHEN publication.publication_type = 'OTHER' THEN 'OTHER' ELSE 'RESEARCH_RESULT' END
       OR achievement.achievement_year IS DISTINCT FROM publication.publication_year
       OR achievement.achievement_date IS DISTINCT FROM
          CASE WHEN publication.publication_date IS NULL
                    OR EXTRACT(YEAR FROM publication.publication_date)::INTEGER = publication.publication_year
                THEN publication.publication_date END
       OR achievement.evidence_url IS DISTINCT FROM publication.public_url
       OR achievement.related_project_id IS NOT NULL
       OR achievement.recognizing_organization IS NOT NULL
       OR achievement.is_public IS DISTINCT FROM publication.is_public
       OR achievement.created_at IS DISTINCT FROM publication.created_at
       OR achievement.updated_at IS DISTINCT FROM publication.updated_at
       OR achievement.deleted_at IS DISTINCT FROM publication.deleted_at
  ) THEN
    RAISE EXCEPTION 'Migration 010 found conflicting lab_achievements IDs for legacy research_publications';
  END IF;
END $$;

-- Mapping notes:
-- * id, title, summary, publication_year, publication_date, is_public, created_at,
--   updated_at, and deleted_at are preserved directly.
-- * publication_type is mapped to RESEARCH_RESULT, except OTHER remains OTHER;
--   the new achievement taxonomy has no publication-specific values.
-- * public_url becomes evidence_url because it is the closest public evidence
--   field. authors, venue, DOI, and a date/year mismatch are retained as labeled
--   legacy metadata in summary rather than being discarded or invented as new
--   achievement metadata.
-- * related_project_id and recognizing_organization have no source equivalent
--   and therefore remain NULL.
INSERT INTO lab_achievements (
  id,
  title,
  summary,
  achievement_type,
  achievement_year,
  achievement_date,
  evidence_url,
  related_project_id,
  is_public,
  created_at,
  updated_at,
  deleted_at
)
SELECT
  publication.id,
  publication.title,
  concat_ws(
    E'\n\n',
    nullif(publication.summary, ''),
    CASE WHEN nullif(publication.authors, '') IS NOT NULL
      THEN 'Legacy publication authors: ' || publication.authors END,
    CASE WHEN nullif(publication.venue, '') IS NOT NULL
      THEN 'Legacy publication venue: ' || publication.venue END,
    CASE WHEN nullif(publication.doi, '') IS NOT NULL
      THEN 'Legacy publication DOI: ' || publication.doi END,
    CASE WHEN publication.publication_date IS NOT NULL
          AND EXTRACT(YEAR FROM publication.publication_date)::INTEGER <> publication.publication_year
      THEN 'Legacy publication date: ' || publication.publication_date::TEXT END
  ),
  CASE WHEN publication.publication_type = 'OTHER' THEN 'OTHER' ELSE 'RESEARCH_RESULT' END,
  publication.publication_year,
  CASE WHEN publication.publication_date IS NULL
          OR EXTRACT(YEAR FROM publication.publication_date)::INTEGER = publication.publication_year
    THEN publication.publication_date END,
  publication.public_url,
  NULL,
  publication.is_public,
  publication.created_at,
  publication.updated_at,
  publication.deleted_at
FROM research_publications publication
ON CONFLICT (id) DO NOTHING;

-- Keep identity generation above migrated IDs on both first and duplicate runs.
SELECT setval(
  pg_get_serial_sequence('lab_achievements', 'id'),
  COALESCE((SELECT MAX(id) FROM lab_achievements), 1),
  (SELECT COUNT(*) > 0 FROM lab_achievements)
);

COMMENT ON TABLE research_publications IS
  'Historical source retained after migration 010 to lab_achievements for rollback and audit.';
