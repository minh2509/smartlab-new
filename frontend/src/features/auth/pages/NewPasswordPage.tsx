import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { resetPassword } from '../api'
import { Feedback } from '../../../shared/components/Feedback'
import { Logo } from '../../../shared/components/Logo'
import { RESET_EMAIL_KEY, RESET_OTP_KEY } from './ForgotPasswordPage'

export function NewPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { email, otp } = useMemo(() => {
    const stateEmail =
      typeof location.state === 'object' && location.state && 'email' in location.state ? String(location.state.email) : ''
    const stateOtp = typeof location.state === 'object' && location.state && 'otp' in location.state ? String(location.state.otp) : ''
    return {
      email: stateEmail || sessionStorage.getItem(RESET_EMAIL_KEY) || '',
      otp: stateOtp || sessionStorage.getItem(RESET_OTP_KEY) || '',
    }
  }, [location.state])
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)

  if (!email || !otp) {
    return <Navigate to="/forgot-password" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      if (newPassword !== confirmPassword) {
        throw new Error('Mật khẩu nhập lại không khớp')
      }
      await resetPassword({ email, otp, newPassword })
      sessionStorage.removeItem(RESET_EMAIL_KEY)
      sessionStorage.removeItem(RESET_OTP_KEY)
      navigate('/login', { replace: true, state: { resetPasswordDone: true } })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không đổi được mật khẩu')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-form-wrapper">
      <form className="auth-form-console" onSubmit={handleSubmit}>
        <div className="auth-mobile-brand">
          <Logo />
        </div>
        <div className="auth-console-header">
          <span className="auth-console-eyebrow">Xác thực khôi phục · Bước 3/3</span>
          <h1>Đặt mật khẩu mới</h1>
          <p>Thiết lập mật khẩu an toàn mới cho tài khoản <strong>{email}</strong>.</p>
        </div>
        <Feedback error={error} />
        <label className="field">
          <span>Mật khẩu mới</span>
          <input
            className="input"
            type="password"
            autoComplete="new-password"
            placeholder="Tối thiểu 6 ký tự"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Nhập lại mật khẩu mới</span>
          <input
            className="input"
            type="password"
            autoComplete="new-password"
            placeholder="Nhập lại mật khẩu trên"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            required
          />
        </label>
        <button className="btn primary block auth-submit-btn" type="submit" disabled={isSubmitting}>
          <KeyRound size={18} />
          {isSubmitting ? 'Đang đổi...' : 'Cập nhật mật khẩu mới'}
        </button>
        <div className="auth-console-actions">
          <Link className="auth-sublink" to="/forgot-password/otp">
            ← Quay lại nhập OTP
          </Link>
        </div>
      </form>
    </div>
  )
}
