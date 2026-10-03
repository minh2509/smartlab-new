-- 018_document_categories.sql
-- Provisions document_categories, archive_date, and DOCUMENT_MANAGE for the
-- public document archive. Re-running this file preserves operator changes.
-- Idempotent and safe to run multiple times.

BEGIN;

CREATE TABLE IF NOT EXISTS public.document_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.documents
    ADD COLUMN IF NOT EXISTS category_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.documents'::regclass
          AND c.confrelid = 'public.document_categories'::regclass
          AND c.contype = 'f'
          AND c.conkey = ARRAY[(
              SELECT a.attnum
              FROM pg_attribute a
              WHERE a.attrelid = 'public.documents'::regclass
                AND a.attname = 'category_id'
          )]::smallint[]
    ) THEN
        ALTER TABLE public.documents
            ADD CONSTRAINT fk_documents_category
            FOREIGN KEY (category_id) REFERENCES public.document_categories(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_documents_category_id ON public.documents(category_id);

ALTER TABLE public.documents
    ADD COLUMN IF NOT EXISTS archive_date TIMESTAMPTZ;

UPDATE public.documents
SET archive_date = created_at
WHERE archive_date IS NULL;

ALTER TABLE public.documents
    ALTER COLUMN archive_date SET DEFAULT now(),
    ALTER COLUMN archive_date SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_documents_archive_date_active
    ON public.documents (archive_date DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_documents_category_archive_active
    ON public.documents (category_id, archive_date DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_categories_active_order
    ON public.document_categories (display_order, name, id)
    WHERE is_active = TRUE;

-- Provision canonical academic document categories
INSERT INTO public.document_categories (code, name, description, display_order, is_active)
VALUES
    ('nghien-cuu-hoc-thuat', 'Nghiên cứu & Học thuật', 'Các báo cáo đề tài, tiểu luận chuyên đề và công bố nghiên cứu khoa học của phòng thí nghiệm.', 1, TRUE),
    ('tai-lieu-ky-thuat', 'Tài liệu kỹ thuật & Đặc tả', 'Kiến trúc hệ thống, hướng dẫn kỹ thuật, đặc tả API và tài liệu thiết kế giải pháp.', 2, TRUE),
    ('huong-dan-quy-trinh', 'Hướng dẫn & Quy trình', 'Quy chế phòng thí nghiệm, quy trình vận hành nghiên cứu và tài liệu chuyển giao công nghệ.', 3, TRUE),
    ('an-pham-dao-tao', 'Ấn phẩm & Bài giảng', 'Tài liệu seminar, slide tập huấn, giáo trình nội bộ và chuyên đề sinh hoạt học thuật.', 4, TRUE)
ON CONFLICT (code) DO UPDATE SET
    code = EXCLUDED.code;

-- Provision DOCUMENT_MANAGE permission and assign to ADMIN
INSERT INTO public.permissions (code, name, module, description, is_active, created_at)
VALUES ('DOCUMENT_MANAGE', 'Quản lý tài liệu & chuyên mục', 'DOCUMENTS', 'Tổ chức, phân loại, sắp xếp và quản trị nhóm tài liệu thư viện', TRUE, now())
ON CONFLICT (code) DO UPDATE SET
    code = EXCLUDED.code;

INSERT INTO public.role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM public.roles r
CROSS JOIN public.permissions p
WHERE r.code = 'ADMIN' AND p.code = 'DOCUMENT_MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMIT;
