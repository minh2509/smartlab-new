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
    <div className="auth-form-wrapper">
      <form className="auth-form-console" onSubmit={handleSubmit}>
        <div className="auth-mobile-brand">
          <Logo />
        </div>
        <div className="auth-console-header">
          <span className="auth-console-eyebrow">Xác thực khôi phục · Bước 2/3</span>
          <h1>Nhập mã OTP</h1>
          <p>Mã bảo mật đã được gửi tới <strong>{email}</strong>.</p>
        </div>
        <Feedback error={error} />
        <label className="field">
          <span>Mã xác thực (OTP)</span>
          <input
            className="input auth-otp-input"
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="123456"
            value={otp}
            onChange={(event) => setOtp(event.target.value)}
            required
          />
        </label>
        <button className="btn primary block auth-submit-btn" type="submit" disabled={isSubmitting}>
          <ShieldCheck size={18} />
          {isSubmitting ? 'Đang kiểm tra...' : 'Xác nhận mã OTP'}
        </button>
        <div className="auth-console-actions">
          <Link className="auth-sublink" to="/forgot-password">
            ← Đổi địa chỉ email khác
          </Link>
        </div>
      </form>
    </div>
  )
}
