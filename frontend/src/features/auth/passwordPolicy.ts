export function validateNewPassword(password: string) {
  if (password.trim().length === 0 || password.length < 6) {
    throw new Error('Mật khẩu phải có ít nhất 6 ký tự.')
  }
  if (new TextEncoder().encode(password).length > 72) {
    throw new Error('Mật khẩu quá dài. Vui lòng chọn mật khẩu ngắn hơn.')
  }
}
