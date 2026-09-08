-- Refreshes the original SmartLab default email copy without overwriting operator-customized templates.
-- Apply once after 012_bulk_account_invitations.sql.

BEGIN;

UPDATE email_templates
SET subject_template = 'Kích hoạt tài khoản SmartLab',
    body_template = E'Xin chào {{fullName}},\n\nTài khoản SmartLab đã được tạo cho bạn. Để thiết lập mật khẩu và kích hoạt tài khoản, vui lòng mở liên kết sau:\n\n{{inviteLink}}\n\nLiên kết có hiệu lực {{expiresAt}} và chỉ sử dụng một lần.\n\nNếu bạn không yêu cầu tạo tài khoản, vui lòng bỏ qua email này hoặc liên hệ quản trị viên SmartLab.\n\nTrân trọng,\nSmartLab\n',
    updated_at = now()
WHERE code = 'ACCOUNT_INVITATION'
  AND subject_template = 'Hoàn tất tài khoản SmartLab của bạn'
  AND body_template = E'Xin chào {{fullName}},\n\nBạn đã được mời tham gia SmartLab. Mở liên kết dưới đây để đặt mật khẩu và kích hoạt tài khoản:\n{{inviteLink}}\n\nLiên kết có hiệu lực đến {{expiresAt}} và chỉ dùng một lần. Nếu bạn không mong đợi email này, hãy liên hệ quản trị viên SmartLab.\n';

UPDATE email_templates
SET subject_template = 'Mã xác thực đặt lại mật khẩu SmartLab',
    body_template = E'Xin chào bạn,\n\nBạn vừa yêu cầu đặt lại mật khẩu SmartLab. Mã xác thực của bạn là:\n\n{{otp}}\n\nKhông chia sẻ mã này với bất kỳ ai. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ quản trị viên SmartLab.\n\nTrân trọng,\nSmartLab\n',
    updated_at = now()
WHERE code = 'PASSWORD_RESET_OTP'
  AND subject_template = 'Mã đặt lại mật khẩu SmartLab'
  AND body_template = E'Bạn đã yêu cầu đặt lại mật khẩu SmartLab. Mã xác thực của bạn là: {{otp}}\n\nKhông chia sẻ mã này với bất kỳ ai. Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email hoặc liên hệ quản trị viên SmartLab.\n';

COMMIT;
