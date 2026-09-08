-- SmartLab bulk account invitations.
-- Apply once after the approved 001..011 SmartLab PostgreSQL lineage.
-- This migration is additive and does not alter existing single-account invitations.

BEGIN;

CREATE TABLE IF NOT EXISTS account_invitation_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL UNIQUE,
    created_by VARCHAR(190) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    requested_count INTEGER NOT NULL,
    accepted_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_account_invitation_batches_status
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_FAILURES')),
    CONSTRAINT chk_account_invitation_batches_requested_count CHECK (requested_count > 0),
    CONSTRAINT chk_account_invitation_batches_counts CHECK (accepted_count >= 0 AND rejected_count >= 0)
);

CREATE TABLE IF NOT EXISTS account_invitation_batch_roles (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES account_invitation_batches(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_account_invitation_batch_roles UNIQUE (batch_id, role_id)
);

CREATE TABLE IF NOT EXISTS account_invitation_items (
    id BIGSERIAL PRIMARY KEY,
    item_id VARCHAR(36) NOT NULL UNIQUE,
    batch_id BIGINT NOT NULL REFERENCES account_invitation_batches(id) ON DELETE CASCADE,
    source_row INTEGER NOT NULL,
    full_name VARCHAR(150),
    email VARCHAR(190) NOT NULL,
    user_id BIGINT REFERENCES tbl_user(id) ON DELETE SET NULL,
    invitation_id BIGINT REFERENCES account_invitations(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(80),
    failure_message VARCHAR(500),
    resend_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_account_invitation_items_source_row UNIQUE (batch_id, source_row),
    CONSTRAINT chk_account_invitation_items_status CHECK (
        status IN ('VALID', 'REJECTED_INVALID', 'REJECTED_DUPLICATE', 'REJECTED_ALREADY_EXISTS',
                   'QUEUED', 'SENDING', 'SENT', 'EMAIL_FAILED', 'ACTIVATED')
    ),
    CONSTRAINT chk_account_invitation_items_source_row CHECK (source_row > 0),
    CONSTRAINT chk_account_invitation_items_resend_count CHECK (resend_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_account_invitation_items_batch_status
    ON account_invitation_items(batch_id, status);
CREATE INDEX IF NOT EXISTS idx_account_invitation_items_email
    ON account_invitation_items(email);

CREATE TABLE IF NOT EXISTS email_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    subject_template VARCHAR(255) NOT NULL,
    body_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO email_templates (code, subject_template, body_template, is_active)
VALUES (
    'ACCOUNT_INVITATION',
    'Kích hoạt tài khoản SmartLab',
    E'Xin chào {{fullName}},\n\nTài khoản SmartLab đã được tạo cho bạn. Để thiết lập mật khẩu và kích hoạt tài khoản, vui lòng mở liên kết sau:\n\n{{inviteLink}}\n\nLiên kết có hiệu lực {{expiresAt}} và chỉ sử dụng một lần.\n\nNếu bạn không yêu cầu tạo tài khoản, vui lòng bỏ qua email này hoặc liên hệ quản trị viên SmartLab.\n\nTrân trọng,\nSmartLab\n',
    TRUE
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO email_templates (code, subject_template, body_template, is_active)
VALUES (
    'PASSWORD_RESET_OTP',
    'Mã xác thực đặt lại mật khẩu SmartLab',
    E'Xin chào bạn,\n\nBạn vừa yêu cầu đặt lại mật khẩu SmartLab. Mã xác thực của bạn là:\n\n{{otp}}\n\nKhông chia sẻ mã này với bất kỳ ai. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ quản trị viên SmartLab.\n\nTrân trọng,\nSmartLab\n',
    TRUE
)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS email_outbox (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL UNIQUE,
    template_code VARCHAR(100) NOT NULL REFERENCES email_templates(code) ON DELETE RESTRICT,
    recipient_email VARCHAR(190) NOT NULL,
    invitation_id BIGINT REFERENCES account_invitations(id) ON DELETE SET NULL,
    batch_item_id BIGINT REFERENCES account_invitation_items(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(500),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_email_outbox_status CHECK (status IN ('QUEUED', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT chk_email_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_email_outbox_recipient_orphan CHECK (invitation_id IS NOT NULL OR batch_item_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_email_outbox_available
    ON email_outbox(status, next_attempt_at, created_at)
    WHERE status = 'QUEUED';
CREATE INDEX IF NOT EXISTS idx_email_outbox_batch_item
    ON email_outbox(batch_item_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartlab_user') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON
            account_invitation_batches, account_invitation_batch_roles,
            account_invitation_items, email_templates, email_outbox
        TO smartlab_user;
        GRANT USAGE, SELECT ON SEQUENCE
            account_invitation_batches_id_seq, account_invitation_batch_roles_id_seq,
            account_invitation_items_id_seq, email_templates_id_seq, email_outbox_id_seq
        TO smartlab_user;
    END IF;
END
$$;

COMMIT;
