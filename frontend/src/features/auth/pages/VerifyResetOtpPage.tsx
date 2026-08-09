import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ShieldCheck } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { verifyResetOtp } from '../api'
import { Feedback } from '../../../shared/components/Feedback'
import { Logo } from '../../../shared/components/Logo'
import { RESET_EMAIL_KEY, RESET_OTP_KEY } from './ForgotPasswordPage'

export function VerifyResetOtpPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const email = useMemo(() => {
    if (typeof location.state === 'object' && location.state && 'email' in location.state) {
      return String(location.state.email)
    }
    return sessionStorage.getItem(RESET_EMAIL_KEY) ?? ''
  }, [location.state])
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)

  if (!email) {
    return <Navigate to="/forgot-password" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const normalizedOtp = otp.trim()
      await verifyResetOtp({ email, otp: normalizedOtp })
      sessionStorage.setItem(RESET_EMAIL_KEY, email)
      sessionStorage.setItem(RESET_OTP_KEY, normalizedOtp)
      navigate('/forgot-password/new-password', { state: { email, otp: normalizedOtp } })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'OTP không hợp lệ')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth auth-shell">
      <div className="auth-side">
        <h2>Xác thực OTP</h2>
        <p>Nhập mã OTP đã gửi tới email để tiếp tục sang bước đặt mật khẩu mới.</p>
      </div>

      <div className="auth-form">
        <form className="auth-box card pad" onSubmit={handleSubmit}>
          <Logo />
          <div>
            <h1>Nhập OTP</h1>
            <p className="muted">Bước 2/3 · Email: {email}</p>
          </div>
          <Feedback error={error} />
          <label className="field">
            <span>OTP</span>
            <input
              className="input"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={otp}
              onChange={(event) => setOtp(event.target.value)}
              required
            />
          </label>
          <button className="btn primary block" type="submit" disabled={isSubmitting}>
            <ShieldCheck />
            {isSubmitting ? 'Đang kiểm tra...' : 'Xác nhận OTP'}
          </button>
          <Link className="muted-link" to="/forgot-password">
            Đổi email
          </Link>
        </form>
      </div>
    </section>
  )
}
