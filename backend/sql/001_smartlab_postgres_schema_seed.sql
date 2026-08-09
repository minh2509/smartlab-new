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
  reset_otp_expire_at BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

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
  title VARCHAR(255) NOT NULL,
  summary TEXT,
  content_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  content_html TEXT,
  cover_file_id BIGINT REFERENCES files(id) ON DELETE SET NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'LAB',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT chk_posts_visibility CHECK (visibility IN ('PUBLIC', 'LAB', 'PROJECT')),
  CONSTRAINT chk_posts_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS post_reviews (
  id BIGSERIAL PRIMARY KEY,
  post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  reviewer_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  decision VARCHAR(20) NOT NULL,
  reject_reason TEXT,
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_post_reviews_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
  CONSTRAINT chk_post_reviews_reject_reason CHECK (decision <> 'REJECTED' OR reject_reason IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGSERIAL PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL REFERENCES tbl_user(id) ON DELETE CASCADE,
  message VARCHAR(500) NOT NULL,
  link_url VARCHAR(500),
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
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
  location VARCHAR(255),
  start_at TIMESTAMPTZ NOT NULL,
  end_at TIMESTAMPTZ,
  visibility VARCHAR(20) NOT NULL DEFAULT 'LAB',
  created_by_user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
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
  ('USER_MANAGE', 'Manage users', 'USER', 'Provision accounts, resend invitations, and activate/deactivate users', TRUE),
  ('ROLE_MANAGE', 'Manage roles', 'ROLE', 'Create and update roles and assign roles to users', TRUE),
  ('PERMISSION_MANAGE', 'Manage permissions', 'PERMISSION', 'Create permissions, assign permissions to roles, and override user permissions', TRUE),
  ('PROFILE_READ', 'Read own profile', 'PROFILE', 'Read own account profile and effective permissions', TRUE),
  ('DASHBOARD_READ', 'Read dashboard', 'DASHBOARD', 'Read SmartLab dashboard', TRUE),
  ('PROJECT_READ', 'Read projects', 'PROJECT', 'Read project information', TRUE),
  ('PROJECT_MANAGE', 'Manage projects', 'PROJECT', 'Create and update projects', TRUE),
  ('TASK_READ', 'Read tasks', 'TASK', 'Read project tasks', TRUE),
  ('TASK_MANAGE', 'Manage tasks', 'TASK', 'Create and update project tasks', TRUE),
  ('POST_MANAGE', 'Manage posts', 'POST', 'Create, review, and publish posts', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    updated_at = now();

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'PROFILE_READ', 'DASHBOARD_READ', 'PROJECT_READ', 'PROJECT_MANAGE', 'TASK_READ', 'TASK_MANAGE'
)
WHERE r.code = 'LEADER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('PROFILE_READ', 'DASHBOARD_READ', 'PROJECT_READ', 'TASK_READ')
WHERE r.code = 'MEMBER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO tbl_user (user_id, name, email, password, is_active, is_account_verified, reset_otp_expire_at)
VALUES (
  gen_random_uuid()::text,
  'Smart Lab Admin',
  'admin@smartlab.local',
  crypt('Admin@123456', gen_salt('bf', 10)),
  TRUE,
  TRUE,
  0
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
