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
    <section className="auth auth-shell">
      <div className="auth-side">
        <h2>Đặt mật khẩu mới</h2>
        <p>Sau khi đổi mật khẩu, backend sẽ thu hồi toàn bộ phiên đăng nhập cũ của tài khoản.</p>
      </div>

      <div className="auth-form">
        <form className="auth-box card pad" onSubmit={handleSubmit}>
          <Logo />
          <div>
            <h1>Mật khẩu mới</h1>
            <p className="muted">Bước 3/3 · Email: {email}</p>
          </div>
          <Feedback error={error} />
          <label className="field">
            <span>Mật khẩu mới</span>
            <input
              className="input"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Nhập lại mật khẩu</span>
            <input
              className="input"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
            />
          </label>
          <button className="btn primary block" type="submit" disabled={isSubmitting}>
            <KeyRound />
            {isSubmitting ? 'Đang đổi...' : 'Đổi mật khẩu'}
          </button>
          <Link className="muted-link" to="/forgot-password/otp">
            Quay lại nhập OTP
          </Link>
        </form>
      </div>
    </section>
  )
}
