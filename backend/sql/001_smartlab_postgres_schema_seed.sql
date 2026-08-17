-- SmartLab PostgreSQL schema + seed
-- Run:
--   psql -U postgres -d smartlab -f backend/sql/001_smartlab_postgres_schema_seed.sql
--
-- Default admin:
--   email: admin@smartlab.local
--   password: Admin@123456

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tbl_user (
  id BIGSERIAL PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,
  name VARCHAR(150) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  is_account_verified BOOLEAN NOT NULL DEFAULT FALSE,
  reset_otp VARCHAR(20),
  reset_otp_expire_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'tbl_user'
      AND column_name = 'reset_otp_expire_at'
      AND data_type = 'bigint'
  ) THEN
    ALTER TABLE tbl_user
      ALTER COLUMN reset_otp_expire_at DROP DEFAULT,
      ALTER COLUMN reset_otp_expire_at DROP NOT NULL,
      ALTER COLUMN reset_otp_expire_at TYPE TIMESTAMPTZ
        USING CASE
          WHEN reset_otp_expire_at IS NULL OR reset_otp_expire_at <= 0 THEN NULL
          ELSE to_timestamp(reset_otp_expire_at / 1000.0)
        END;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS roles (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  description TEXT,
  is_system BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS permissions (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  module VARCHAR(80),
  description TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS role_permissions (
  id BIGSERIAL PRIMARY KEY,
  role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_roles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  assigned_by VARCHAR(36),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_permission_overrides (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  effect VARCHAR(10) NOT NULL,
  changed_by VARCHAR(36),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_user_permission_overrides_user_permission UNIQUE (user_id, permission_id),
  CONSTRAINT chk_user_permission_overrides_effect CHECK (effect IN ('GRANT', 'DENY'))
);

CREATE TABLE IF NOT EXISTS user_sessions (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
  user_agent TEXT,
  ip_address VARCHAR(45),
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  last_seen_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS account_invitations (
  id BIGSERIAL PRIMARY KEY,
  invitation_id VARCHAR(36) NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,
  email VARCHAR(190) NOT NULL UNIQUE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  invited_by VARCHAR(36),
  resend_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_account_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active ON user_sessions(user_id, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_id);

-- Future SmartLab domain tables from the agreed DB design.
CREATE TABLE IF NOT EXISTS research_fields (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  description TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS projects (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(60) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  goal TEXT,
  project_type VARCHAR(20) NOT NULL DEFAULT 'RESEARCH',
  leader_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PROPOSED',
  start_date DATE,
  expected_end_date DATE,
  actual_end_date DATE,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_projects_type CHECK (project_type IN ('RESEARCH', 'PRODUCTION')),
  CONSTRAINT chk_projects_status CHECK (status IN ('PROPOSED', 'PREPARING', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS files (
  id BIGSERIAL PRIMARY KEY,
  owner_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
  storage_provider VARCHAR(40) NOT NULL DEFAULT 'CLOUD',
  storage_key VARCHAR(500) NOT NULL,
  public_url VARCHAR(1000),
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  access_scope VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_files_size CHECK (size_bytes >= 0),
  CONSTRAINT chk_files_access_scope CHECK (access_scope IN ('PUBLIC', 'PRIVATE', 'PROJECT', 'LAB'))
);

CREATE TABLE IF NOT EXISTS member_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES tbl_user(id) ON DELETE CASCADE,
  avatar_file_id BIGINT REFERENCES files(id) ON DELETE SET NULL,
  phone VARCHAR(40),
  public_email VARCHAR(190),
  bio TEXT,
  joined_lab_at DATE,
  active_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  featured_order INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_member_profiles_status CHECK (active_status IN ('ACTIVE', 'INACTIVE', 'ALUMNI'))
);

CREATE TABLE IF NOT EXISTS member_research_fields (
  member_profile_id BIGINT NOT NULL REFERENCES member_profiles(id) ON DELETE CASCADE,
  field_id BIGINT NOT NULL REFERENCES research_fields(id) ON DELETE CASCADE,
  PRIMARY KEY (member_profile_id, field_id)
);

CREATE TABLE IF NOT EXISTS project_research_fields (
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  field_id BIGINT NOT NULL REFERENCES research_fields(id) ON DELETE CASCADE,
  PRIMARY KEY (project_id, field_id)
);

CREATE TABLE IF NOT EXISTS project_members (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  project_role VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  removed_at TIMESTAMPTZ,
  CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
  CONSTRAINT chk_project_members_role CHECK (project_role IN ('LEADER', 'MEMBER')),
  CONSTRAINT chk_project_members_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

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

CREATE TABLE IF NOT EXISTS tasks (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  parent_task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  start_at TIMESTAMPTZ,
  due_at TIMESTAMPTZ,
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE', 'CANCELLED')),
  CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE TABLE IF NOT EXISTS task_assignees (
  task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (task_id, user_id)
);

CREATE TABLE IF NOT EXISTS task_attachments (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  file_id BIGINT NOT NULL REFERENCES files(id) ON DELETE CASCADE,
  attachment_type VARCHAR(20) NOT NULL DEFAULT 'INPUT',
  description TEXT,
  uploaded_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_task_attachments_type CHECK (attachment_type IN ('INPUT', 'RESULT', 'REFERENCE'))
);

CREATE TABLE IF NOT EXISTS evaluations (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  evaluator_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  evaluated_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS evaluation_criteria (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name VARCHAR(150) NOT NULL,
  description TEXT,
  max_score NUMERIC(5,2) NOT NULL DEFAULT 10,
  display_order INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS evaluation_scores (
  evaluation_id BIGINT NOT NULL REFERENCES evaluations(id) ON DELETE CASCADE,
  criterion_id BIGINT NOT NULL REFERENCES evaluation_criteria(id) ON DELETE CASCADE,
  score NUMERIC(5,2) NOT NULL,
  note TEXT,
  PRIMARY KEY (evaluation_id, criterion_id)
);

CREATE TABLE IF NOT EXISTS content_categories (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS posts (
  id BIGSERIAL PRIMARY KEY,
  author_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  category_id BIGINT REFERENCES content_categories(id) ON DELETE SET NULL,
  project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
  title VARCHAR(250) NOT NULL,
  slug VARCHAR(260) NOT NULL,
  excerpt VARCHAR(500),
  content_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  content_html TEXT,
  cover_file_id BIGINT REFERENCES files(id) ON DELETE SET NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'LAB',
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT uk_posts_slug UNIQUE (slug),
  CONSTRAINT chk_posts_visibility CHECK (visibility IN ('PUBLIC', 'LAB', 'PROJECT')),
  CONSTRAINT chk_posts_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REVISION_REQUIRED', 'APPROVED', 'PUBLISHED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS post_reviews (
  id BIGSERIAL PRIMARY KEY,
  post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  reviewer_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  decision VARCHAR(20) NOT NULL,
  reason VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_post_reviews_decision CHECK (decision IN ('APPROVED', 'REVISION_REQUIRED', 'REJECTED')),
  CONSTRAINT chk_post_reviews_rejected_reason CHECK (
    decision <> 'REJECTED' OR (reason IS NOT NULL AND length(btrim(reason)) > 0)
  )
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGSERIAL PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  actor_user_id BIGINT,
  type VARCHAR(100) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  related_type VARCHAR(80),
  related_id BIGINT,
  target_url VARCHAR(500),
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS documents (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  current_file_id BIGINT REFERENCES files(id) ON DELETE SET NULL,
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS document_versions (
  id BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  file_id BIGINT NOT NULL REFERENCES files(id) ON DELETE CASCADE,
  version_no INTEGER NOT NULL,
  uploaded_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_document_versions_document_version UNIQUE (document_id, version_no)
);

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
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_events_mode CHECK (mode IN ('IN_PERSON', 'ONLINE')),
  CONSTRAINT chk_events_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
  CONSTRAINT chk_events_visibility CHECK (visibility IN ('PUBLIC', 'LAB', 'PROJECT'))
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  action VARCHAR(120) NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id VARCHAR(80),
  before_json JSONB,
  after_json JSONB,
  ip_address VARCHAR(45),
  user_agent TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Current-lineage upgrades. These blocks intentionally target only shapes
-- produced by earlier revisions of this 001 script; they are not a migration
-- path for the unrelated legacy smartlab_db lineage.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'posts' AND column_name = 'summary') THEN
    ALTER TABLE posts ADD COLUMN IF NOT EXISTS excerpt TEXT;
    UPDATE posts SET excerpt = summary WHERE excerpt IS NULL AND summary IS NOT NULL;
    ALTER TABLE posts DROP COLUMN summary;
  END IF;

  ALTER TABLE posts ADD COLUMN IF NOT EXISTS slug VARCHAR(260);
  IF EXISTS (
    SELECT 1
    FROM posts missing_slug
    JOIN posts existing_slug
      ON existing_slug.slug = 'post-' || missing_slug.id
     AND existing_slug.id <> missing_slug.id
    WHERE missing_slug.slug IS NULL OR btrim(missing_slug.slug) = ''
  ) THEN
    RAISE EXCEPTION 'Cannot backfill posts.slug: generated post-<id> value collides with an existing slug';
  END IF;
  UPDATE posts
  SET slug = 'post-' || id
  WHERE slug IS NULL OR btrim(slug) = '';
  ALTER TABLE posts ALTER COLUMN slug SET NOT NULL;

  IF EXISTS (
    SELECT 1
    FROM posts
    GROUP BY slug
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'Cannot enforce UNIQUE(posts.slug): duplicate legacy slugs require manual resolution';
  END IF;

  IF EXISTS (SELECT 1 FROM posts WHERE char_length(title) > 250) THEN
    RAISE EXCEPTION 'Cannot narrow posts.title to VARCHAR(250): legacy data exceeds the runtime contract';
  END IF;
  ALTER TABLE posts ALTER COLUMN title TYPE VARCHAR(250);

  IF EXISTS (SELECT 1 FROM posts WHERE char_length(excerpt) > 500) THEN
    RAISE EXCEPTION 'Cannot narrow posts.excerpt to VARCHAR(500): legacy data exceeds the runtime contract';
  END IF;
  ALTER TABLE posts ALTER COLUMN excerpt TYPE VARCHAR(500);
  ALTER TABLE posts ALTER COLUMN status TYPE VARCHAR(30);

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint constraint_metadata
    WHERE constraint_metadata.conrelid = 'posts'::regclass
      AND constraint_metadata.contype = 'u'
      AND constraint_metadata.conkey = ARRAY[(
        SELECT attribute.attnum
        FROM pg_attribute attribute
        WHERE attribute.attrelid = 'posts'::regclass
          AND attribute.attname = 'slug'
          AND NOT attribute.attisdropped
      )]::smallint[]
  ) THEN
    ALTER TABLE posts ADD CONSTRAINT uk_posts_slug UNIQUE (slug);
  END IF;

  IF EXISTS (
    SELECT 1
    FROM posts
    WHERE status IS NULL
       OR status NOT IN ('DRAFT', 'PENDING_REVIEW', 'REVISION_REQUIRED', 'APPROVED', 'PUBLISHED', 'REJECTED')
  ) THEN
    RAISE EXCEPTION 'Cannot install canonical posts status constraint: legacy rows contain statuses outside PostStatus (for example ARCHIVED)';
  END IF;
  ALTER TABLE posts DROP CONSTRAINT IF EXISTS chk_posts_status;
  ALTER TABLE posts ADD CONSTRAINT chk_posts_status CHECK (
    status IN ('DRAFT', 'PENDING_REVIEW', 'REVISION_REQUIRED', 'APPROVED', 'PUBLISHED', 'REJECTED')
  );
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'post_reviews' AND column_name = 'reject_reason') THEN
    ALTER TABLE post_reviews ADD COLUMN IF NOT EXISTS reason TEXT;
    UPDATE post_reviews SET reason = reject_reason WHERE reason IS NULL AND reject_reason IS NOT NULL;
    ALTER TABLE post_reviews DROP COLUMN reject_reason;
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'post_reviews' AND column_name = 'reviewed_at') THEN
    ALTER TABLE post_reviews ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
    UPDATE post_reviews SET created_at = reviewed_at WHERE created_at IS NULL;
    ALTER TABLE post_reviews DROP COLUMN reviewed_at;
  END IF;

  UPDATE post_reviews SET created_at = now() WHERE created_at IS NULL;
  ALTER TABLE post_reviews ALTER COLUMN created_at SET NOT NULL;
  ALTER TABLE post_reviews ALTER COLUMN created_at SET DEFAULT now();
  IF EXISTS (SELECT 1 FROM post_reviews WHERE char_length(reason) > 1000) THEN
    RAISE EXCEPTION 'Cannot narrow post_reviews.reason to VARCHAR(1000): legacy data exceeds the runtime contract';
  END IF;
  ALTER TABLE post_reviews ALTER COLUMN reason TYPE VARCHAR(1000);

  IF EXISTS (
    SELECT 1
    FROM post_reviews
    WHERE decision NOT IN ('APPROVED', 'REVISION_REQUIRED', 'REJECTED')
  ) THEN
    RAISE EXCEPTION 'Cannot install canonical post review decision constraint: legacy rows contain unsupported decisions';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM post_reviews
    WHERE decision = 'REJECTED' AND (reason IS NULL OR btrim(reason) = '')
  ) THEN
    RAISE EXCEPTION 'Cannot install canonical rejected-review constraint: rejected legacy rows require a non-blank reason';
  END IF;
  ALTER TABLE post_reviews DROP CONSTRAINT IF EXISTS chk_post_reviews_decision;
  ALTER TABLE post_reviews DROP CONSTRAINT IF EXISTS chk_post_reviews_reject_reason;
  ALTER TABLE post_reviews DROP CONSTRAINT IF EXISTS chk_post_reviews_rejected_reason;
  ALTER TABLE post_reviews ADD CONSTRAINT chk_post_reviews_decision CHECK (
    decision IN ('APPROVED', 'REVISION_REQUIRED', 'REJECTED')
  );
  ALTER TABLE post_reviews ADD CONSTRAINT chk_post_reviews_rejected_reason CHECK (
    decision <> 'REJECTED' OR (reason IS NOT NULL AND length(btrim(reason)) > 0)
  );
END $$;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'link_url') THEN
    ALTER TABLE notifications ADD COLUMN IF NOT EXISTS target_url VARCHAR(500);
    UPDATE notifications SET target_url = link_url WHERE target_url IS NULL AND link_url IS NOT NULL;
    ALTER TABLE notifications DROP COLUMN link_url;
  END IF;

  ALTER TABLE notifications ADD COLUMN IF NOT EXISTS actor_user_id BIGINT;
  ALTER TABLE notifications ADD COLUMN IF NOT EXISTS type VARCHAR(100);
  ALTER TABLE notifications ADD COLUMN IF NOT EXISTS related_type VARCHAR(80);
  ALTER TABLE notifications ADD COLUMN IF NOT EXISTS related_id BIGINT;
  ALTER TABLE notifications ADD COLUMN IF NOT EXISTS target_url VARCHAR(500);
  ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
  UPDATE notifications SET type = 'LEGACY' WHERE type IS NULL OR btrim(type) = '';
  ALTER TABLE notifications ALTER COLUMN type SET NOT NULL;
  ALTER TABLE notifications ALTER COLUMN message TYPE VARCHAR(1000);
END $$;

DO $$
BEGIN
  ALTER TABLE events ADD COLUMN IF NOT EXISTS mode VARCHAR(20);
  ALTER TABLE events ADD COLUMN IF NOT EXISTS meeting_url VARCHAR(2048);
  ALTER TABLE events ADD COLUMN IF NOT EXISTS status VARCHAR(20);
  ALTER TABLE events ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
  UPDATE events SET mode = 'IN_PERSON' WHERE mode IS NULL;
  UPDATE events SET status = 'SCHEDULED' WHERE status IS NULL;
  ALTER TABLE events ALTER COLUMN mode SET NOT NULL;
  ALTER TABLE events ALTER COLUMN mode SET DEFAULT 'IN_PERSON';
  ALTER TABLE events ALTER COLUMN status SET NOT NULL;
  ALTER TABLE events ALTER COLUMN status SET DEFAULT 'SCHEDULED';
  ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_mode;
  ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_status;
  ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_visibility;
  ALTER TABLE events ADD CONSTRAINT chk_events_mode CHECK (mode IN ('IN_PERSON', 'ONLINE'));
  ALTER TABLE events ADD CONSTRAINT chk_events_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'));
  ALTER TABLE events ADD CONSTRAINT chk_events_visibility CHECK (visibility IN ('PUBLIC', 'LAB', 'PROJECT'));
END $$;

INSERT INTO roles (code, name, description, is_system, is_active)
VALUES
  ('ADMIN', 'Admin', 'System administrator', TRUE, TRUE),
  ('LEADER', 'Leader', 'Project leader', TRUE, TRUE),
  ('MEMBER', 'Member', 'Lab member', TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    is_active = EXCLUDED.is_active,
    updated_at = now();

INSERT INTO permissions (code, name, module, description, is_active)
VALUES
  ('USER_MANAGE', 'Quản lý tài khoản', 'USER', 'Cấp tài khoản, gửi lại invite, khoá hoặc mở đăng nhập cho thành viên', TRUE),
  ('ROLE_MANAGE', 'Quản lý vai trò', 'ROLE', 'Tạo, cập nhật role và gán role cho thành viên', TRUE),
  ('PERMISSION_MANAGE', 'Quản lý quyền', 'PERMISSION', 'Gán quyền cho role và cấp quyền riêng cho thành viên khi cần', TRUE),
  ('PROFILE_READ', 'Xem hồ sơ cá nhân', 'PROFILE', 'Xem thông tin tài khoản và quyền hiệu lực của chính mình', TRUE),
  ('DASHBOARD_READ', 'Xem bảng điều khiển', 'DASHBOARD', 'Xem tổng quan hoạt động của Smart Lab', TRUE),
  ('PROJECT_READ', 'Xem dự án', 'PROJECT', 'Xem thông tin dự án', TRUE),
  ('PROJECT_MANAGE', 'Quản lý dự án', 'PROJECT', 'Tạo và cập nhật dự án', TRUE),
  ('TASK_READ', 'Xem công việc', 'TASK', 'Xem công việc trong dự án', TRUE),
  ('TASK_MANAGE', 'Quản lý công việc', 'TASK', 'Tạo và cập nhật công việc trong dự án', TRUE),
  ('POST_MANAGE', 'Quản lý bài viết', 'POST', 'Quản lý nội dung bài viết', TRUE),
  ('FILE_UPLOAD', 'Tải tệp lên', 'FILE', 'Tải tệp lên SmartLab', TRUE),
  ('FILE_DELETE', 'Xoá tệp', 'FILE', 'Xoá tệp do người dùng quản lý', TRUE),
  ('PROFILE_UPDATE', 'Cập nhật hồ sơ cá nhân', 'PROFILE', 'Cập nhật thông tin hồ sơ của chính mình', TRUE),
  ('MEMBER_MANAGE', 'Quản lý thành viên', 'MEMBER', 'Quản lý hồ sơ và trạng thái thành viên', TRUE),
  ('RESEARCH_FIELD_MANAGE', 'Quản lý lĩnh vực nghiên cứu', 'RESEARCH_FIELD', 'Quản lý danh mục lĩnh vực nghiên cứu', TRUE),
  ('posts.submit', 'Submit Post', 'POST', 'Gửi bài viết để duyệt', TRUE),
  ('posts.review', 'Review Post', 'POST', 'Duyệt bài viết đang chờ duyệt', TRUE),
  ('posts.publish', 'Publish Post', 'POST', 'Xuất bản bài viết đã được phê duyệt', TRUE),
  ('posts.publish.direct', 'Direct Publish Post', 'POST', 'Xuất bản bài viết trực tiếp', TRUE),
  ('notifications.read_own', 'Xem thông báo cá nhân', 'NOTIFICATION', 'Xem thông báo của chính mình', TRUE),
  ('notifications.mark_read_own', 'Đánh dấu đã đọc thông báo', 'NOTIFICATION', 'Đánh dấu thông báo của chính mình là đã đọc', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    updated_at = now();

-- Fresh bootstrap creates 20 ADMIN, 12 LEADER, and 10 MEMBER mappings. Re-runs
-- add missing canonical grants without removing operator-managed grants.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('ADMIN', 'USER_MANAGE'),
    ('ADMIN', 'ROLE_MANAGE'),
    ('ADMIN', 'PERMISSION_MANAGE'),
    ('ADMIN', 'PROFILE_READ'),
    ('ADMIN', 'PROFILE_UPDATE'),
    ('ADMIN', 'DASHBOARD_READ'),
    ('ADMIN', 'PROJECT_READ'),
    ('ADMIN', 'PROJECT_MANAGE'),
    ('ADMIN', 'TASK_READ'),
    ('ADMIN', 'TASK_MANAGE'),
    ('ADMIN', 'POST_MANAGE'),
    ('ADMIN', 'FILE_UPLOAD'),
    ('ADMIN', 'FILE_DELETE'),
    ('ADMIN', 'MEMBER_MANAGE'),
    ('ADMIN', 'RESEARCH_FIELD_MANAGE'),
    ('ADMIN', 'posts.review'),
    ('ADMIN', 'posts.publish'),
    ('ADMIN', 'posts.publish.direct'),
    ('ADMIN', 'notifications.read_own'),
    ('ADMIN', 'notifications.mark_read_own'),
    ('LEADER', 'PROFILE_READ'),
    ('LEADER', 'PROFILE_UPDATE'),
    ('LEADER', 'DASHBOARD_READ'),
    ('LEADER', 'PROJECT_READ'),
    ('LEADER', 'PROJECT_MANAGE'),
    ('LEADER', 'TASK_READ'),
    ('LEADER', 'TASK_MANAGE'),
    ('LEADER', 'FILE_UPLOAD'),
    ('LEADER', 'FILE_DELETE'),
    ('LEADER', 'posts.submit'),
    ('LEADER', 'notifications.read_own'),
    ('LEADER', 'notifications.mark_read_own'),
    ('MEMBER', 'PROFILE_READ'),
    ('MEMBER', 'PROFILE_UPDATE'),
    ('MEMBER', 'DASHBOARD_READ'),
    ('MEMBER', 'PROJECT_READ'),
    ('MEMBER', 'TASK_READ'),
    ('MEMBER', 'FILE_UPLOAD'),
    ('MEMBER', 'FILE_DELETE'),
    ('MEMBER', 'posts.submit'),
    ('MEMBER', 'notifications.read_own'),
    ('MEMBER', 'notifications.mark_read_own')
) AS desired(role_code, permission_code)
JOIN roles r ON r.code = desired.role_code
JOIN permissions p ON p.code = desired.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO tbl_user (user_id, name, email, password, is_active, is_account_verified, reset_otp_expire_at)
VALUES (
  gen_random_uuid()::text,
  'Smart Lab Admin',
  'admin@smartlab.local',
  crypt('Admin@123456', gen_salt('bf', 10)),
  TRUE,
  TRUE,
  NULL
)
ON CONFLICT (email) DO UPDATE
SET name = EXCLUDED.name,
    password = EXCLUDED.password,
    is_active = TRUE,
    is_account_verified = TRUE,
    updated_at = now();

INSERT INTO user_roles (user_id, role_id, assigned_by)
SELECT u.id, r.id, 'system'
FROM tbl_user u
JOIN roles r ON r.code = 'ADMIN'
WHERE u.email = 'admin@smartlab.local'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO research_fields (code, name)
VALUES
  ('AI', 'Artificial Intelligence'),
  ('DATA_SCIENCE', 'Data Science'),
  ('IOT', 'Internet of Things'),
  ('CYBER_SECURITY', 'Cyber Security'),
  ('SOFTWARE_ENGINEERING', 'Software Engineering')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, updated_at = now();

INSERT INTO content_categories (code, name)
VALUES
  ('NEWS', 'News'),
  ('RESEARCH', 'Research'),
  ('EVENT', 'Event'),
  ('ANNOUNCEMENT', 'Announcement')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

-- The runtime role receives data access only to SmartLab-owned objects; it
-- receives no schema-changing privilege or access to unrelated public objects.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
    GRANT USAGE ON SCHEMA public TO smartlab_user;
    GRANT SELECT, INSERT, UPDATE, DELETE ON
      tbl_user, roles, permissions, role_permissions, user_roles,
      user_permission_overrides, user_sessions, account_invitations,
      research_fields, projects, files, member_profiles, member_research_fields,
      project_research_fields, project_members, project_join_requests,
      tasks, task_assignees, task_attachments, evaluations,
      evaluation_criteria, evaluation_scores,
      content_categories, posts, post_reviews, notifications, documents,
      document_versions, events, audit_logs
    TO smartlab_user;
    GRANT USAGE, SELECT ON SEQUENCE
      tbl_user_id_seq, roles_id_seq, permissions_id_seq, role_permissions_id_seq,
      user_roles_id_seq, user_permission_overrides_id_seq, user_sessions_id_seq,
      account_invitations_id_seq, research_fields_id_seq, projects_id_seq,
      files_id_seq, member_profiles_id_seq, project_members_id_seq,
      project_join_requests_id_seq, tasks_id_seq, task_attachments_id_seq,
      evaluations_id_seq, evaluation_criteria_id_seq,
      content_categories_id_seq, posts_id_seq, post_reviews_id_seq,
      notifications_id_seq, documents_id_seq, document_versions_id_seq,
      events_id_seq, audit_logs_id_seq
    TO smartlab_user;
  END IF;
END
$$;
