-- SmartLab forward repair migration for Gallery Admin permission.
-- Applies after 014_email_template_copy_refresh.sql.
-- Idempotently provisions GALLERY_MANAGE permission and grants it to ADMIN.
-- Repairs installations where Gallery schema exists but GALLERY_MANAGE permission/mapping is absent.

BEGIN;

INSERT INTO public.permissions (code, name, module, description, is_active, created_at)
VALUES ('GALLERY_MANAGE', 'Quản lý thư viện ảnh', 'GALLERY', 'Tạo, chỉnh sửa, xuất bản và gỡ ảnh công khai', TRUE, now())
ON CONFLICT (code) DO UPDATE SET
  name = EXCLUDED.name,
  module = EXCLUDED.module,
  description = EXCLUDED.description,
  is_active = EXCLUDED.is_active,
  updated_at = now();

INSERT INTO public.role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, now()
FROM public.roles r
CROSS JOIN public.permissions p
WHERE r.code = 'ADMIN' AND p.code = 'GALLERY_MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMIT;
