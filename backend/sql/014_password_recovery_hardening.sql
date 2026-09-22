-- Apply before starting the backend containing the task-5 recovery changes.
BEGIN;
ALTER TABLE tbl_user ALTER COLUMN reset_otp TYPE VARCHAR(100);
ALTER TABLE tbl_user ADD COLUMN IF NOT EXISTS reset_otp_failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tbl_user ADD COLUMN IF NOT EXISTS reset_otp_requested_at TIMESTAMPTZ;
-- Old plaintext codes are deliberately invalidated; request a fresh OTP.
UPDATE tbl_user SET reset_otp = NULL, reset_otp_expire_at = NULL
WHERE reset_otp IS NOT NULL AND reset_otp NOT LIKE '$2%';
COMMIT;
