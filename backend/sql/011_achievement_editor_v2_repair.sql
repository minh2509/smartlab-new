-- Forward-only repair for a partially provisioned achievement editor schema.
-- This migration is additive and does not rewrite existing achievement data.

DO $$
BEGIN
  IF to_regclass('public.lab_achievements') IS NULL THEN
    RAISE EXCEPTION 'Migration 011 requires public.lab_achievements';
  END IF;
  IF to_regclass('public.files') IS NULL THEN
    RAISE EXCEPTION 'Migration 011 requires public.files';
  END IF;
  IF to_regclass('public.projects') IS NULL THEN
    RAISE EXCEPTION 'Migration 011 requires public.projects';
  END IF;
END $$;

ALTER TABLE public.lab_achievements
  ADD COLUMN IF NOT EXISTS recognizing_organization VARCHAR(500);

CREATE TABLE IF NOT EXISTS public.lab_achievement_files (
  id BIGSERIAL PRIMARY KEY,
  achievement_id BIGINT NOT NULL REFERENCES public.lab_achievements(id),
  file_id BIGINT NOT NULL REFERENCES public.files(id),
  label VARCHAR(500),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_lab_achievement_files_sort_order CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lab_achievement_files_active_achievement_file
  ON public.lab_achievement_files (achievement_id, file_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lab_achievement_files_active_achievement_sort_id
  ON public.lab_achievement_files (achievement_id, sort_order, id)
  WHERE deleted_at IS NULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON public.lab_achievement_files TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE public.lab_achievement_files_id_seq TO smartlab_user;
  END IF;
END $$;

DO $$
DECLARE
  has_achievement_fk BOOLEAN;
  has_file_fk BOOLEAN;
  has_sort_check BOOLEAN;
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievements'
      AND column_name = 'recognizing_organization'
      AND data_type = 'character varying'
      AND character_maximum_length = 500
      AND is_nullable = 'YES'
  ) THEN
    RAISE EXCEPTION 'Migration 011 failed to provision recognizing_organization';
  END IF;

  IF to_regclass('public.lab_achievement_files') IS NULL THEN
    RAISE EXCEPTION 'Migration 011 failed to provision lab_achievement_files';
  END IF;

  IF pg_get_serial_sequence('public.lab_achievement_files', 'id') IS NULL THEN
    RAISE EXCEPTION 'Migration 011 failed to provision lab_achievement_files_id_seq';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'id'
      AND data_type = 'bigint'
      AND is_nullable = 'NO'
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'achievement_id'
      AND data_type = 'bigint'
      AND is_nullable = 'NO'
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'file_id'
      AND data_type = 'bigint'
      AND is_nullable = 'NO'
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'label'
      AND data_type = 'character varying'
      AND character_maximum_length = 500
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'sort_order'
      AND data_type = 'integer'
      AND is_nullable = 'NO'
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'created_at'
      AND data_type = 'timestamp with time zone'
      AND is_nullable = 'NO'
  ) OR NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'lab_achievement_files'
      AND column_name = 'deleted_at'
      AND data_type = 'timestamp with time zone'
      AND is_nullable = 'YES'
  ) THEN
    RAISE EXCEPTION 'Migration 011 found incompatible lab_achievement_files columns';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.lab_achievement_files'::regclass
      AND contype = 'p'
      AND pg_get_constraintdef(oid) = 'PRIMARY KEY (id)'
  ) THEN
    RAISE EXCEPTION 'Migration 011 found no compatible lab_achievement_files primary key';
  END IF;

  SELECT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.lab_achievement_files'::regclass
      AND contype = 'f'
      AND confrelid = 'public.lab_achievements'::regclass
      AND pg_get_constraintdef(oid) LIKE '%achievement_id%lab_achievements%'
  ) INTO has_achievement_fk;

  SELECT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.lab_achievement_files'::regclass
      AND contype = 'f'
      AND confrelid = 'public.files'::regclass
      AND pg_get_constraintdef(oid) LIKE '%file_id%files%'
  ) INTO has_file_fk;

  SELECT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.lab_achievement_files'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%sort_order >= 0%'
  ) INTO has_sort_check;

  IF NOT has_achievement_fk THEN
    RAISE EXCEPTION 'Migration 011 found no compatible achievement foreign key';
  END IF;
  IF NOT has_file_fk THEN
    RAISE EXCEPTION 'Migration 011 found no compatible file foreign key';
  END IF;
  IF NOT has_sort_check THEN
    RAISE EXCEPTION 'Migration 011 found no compatible sort-order check';
  END IF;

  IF to_regclass('public.uq_lab_achievement_files_active_achievement_file') IS NULL
     OR to_regclass('public.idx_lab_achievement_files_active_achievement_sort_id') IS NULL THEN
    RAISE EXCEPTION 'Migration 011 failed to provision attachment indexes';
  END IF;

  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    IF NOT has_table_privilege('smartlab_user', 'public.lab_achievement_files', 'SELECT')
       OR NOT has_table_privilege('smartlab_user', 'public.lab_achievement_files', 'INSERT')
       OR NOT has_table_privilege('smartlab_user', 'public.lab_achievement_files', 'UPDATE')
       OR NOT has_table_privilege('smartlab_user', 'public.lab_achievement_files', 'DELETE') THEN
      RAISE EXCEPTION 'Migration 011 failed to provision attachment table grants';
    END IF;
    IF NOT has_sequence_privilege('smartlab_user', 'public.lab_achievement_files_id_seq', 'USAGE')
       OR NOT has_sequence_privilege('smartlab_user', 'public.lab_achievement_files_id_seq', 'SELECT') THEN
      RAISE EXCEPTION 'Migration 011 failed to provision attachment sequence grants';
    END IF;
  END IF;
END $$;
