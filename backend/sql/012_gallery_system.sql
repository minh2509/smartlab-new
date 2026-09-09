-- SmartLab Gate E: real public gallery system.
-- Manual, additive migration; run only after verifying the target lineage and a
-- recoverable backup. It intentionally creates no gallery rows or sample files.

DO $$
BEGIN
  IF to_regclass('public.files') IS NULL
     OR to_regclass('public.projects') IS NULL
     OR to_regclass('public.events') IS NULL
     OR to_regclass('public.tbl_user') IS NULL
     OR to_regclass('public.permissions') IS NULL
     OR to_regclass('public.roles') IS NULL
     OR to_regclass('public.role_permissions') IS NULL THEN
    RAISE EXCEPTION 'Migration 012 requires the SmartLab 001/003 lineage';
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.gallery_items (
  id BIGSERIAL PRIMARY KEY,
  file_id BIGINT NOT NULL REFERENCES public.files(id) ON DELETE RESTRICT,
  title VARCHAR(255) NOT NULL,
  caption TEXT,
  alt_text VARCHAR(255) NOT NULL,
  category VARCHAR(30) NOT NULL DEFAULT 'OTHER',
  project_id BIGINT REFERENCES public.projects(id) ON DELETE SET NULL,
  event_id BIGINT REFERENCES public.events(id) ON DELETE SET NULL,
  captured_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  published_at TIMESTAMPTZ,
  created_by_user_id BIGINT REFERENCES public.tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_gallery_items_category CHECK (category IN ('WORKSHOP', 'PROJECT_DEMO', 'EVENT', 'LAB_ACTIVITY', 'OTHER')),
  CONSTRAINT chk_gallery_items_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
  CONSTRAINT chk_gallery_items_published_at CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
  CONSTRAINT chk_gallery_items_title CHECK (length(btrim(title)) BETWEEN 1 AND 255),
  CONSTRAINT chk_gallery_items_alt_text CHECK (length(btrim(alt_text)) BETWEEN 1 AND 255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gallery_items_active_file
  ON public.gallery_items(file_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_gallery_items_active_status_updated_id
  ON public.gallery_items(status, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_gallery_items_public_capture_id
  ON public.gallery_items(captured_at DESC, published_at DESC, id DESC)
  WHERE deleted_at IS NULL AND status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_gallery_items_active_project
  ON public.gallery_items(project_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;

INSERT INTO public.permissions (code, name, module, description, is_active)
VALUES ('GALLERY_MANAGE', 'Quản lý thư viện ảnh', 'GALLERY', 'Tạo, chỉnh sửa, xuất bản và gỡ ảnh công khai', TRUE)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, module = EXCLUDED.module,
  description = EXCLUDED.description, is_active = EXCLUDED.is_active, updated_at = now();

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM public.roles r CROSS JOIN public.permissions p
WHERE r.code = 'ADMIN' AND p.code = 'GALLERY_MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON public.gallery_items TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE public.gallery_items_id_seq TO smartlab_user;
  END IF;
END $$;
