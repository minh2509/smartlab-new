import { useState } from 'react'
import type { FormEvent } from 'react'
import { MailCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { sendResetOtp } from '../api'
import { Logo } from '../../../shared/components/Logo'

export const RESET_EMAIL_KEY = 'smartlab.reset.email'
export const RESET_OTP_KEY = 'smartlab.reset.otp'

export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setEmailError('')
    try {
      const normalizedEmail = email.trim()
      await sendResetOtp(normalizedEmail)
      sessionStorage.setItem(RESET_EMAIL_KEY, normalizedEmail)
      sessionStorage.removeItem(RESET_OTP_KEY)
      navigate('/forgot-password/otp', { state: { email: normalizedEmail } })
    } catch (err) {
      setEmailError(err instanceof Error ? err.message : 'Không gửi được OTP')
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
          <span className="auth-console-eyebrow">Xác thực khôi phục · Bước 1/3</span>
          <h1>Quên mật khẩu</h1>
          <p>Nhập email tài khoản đã đăng ký để nhận mã OTP khôi phục quyền truy cập.</p>
        </div>
        <label className="field">
          <span>Email tài khoản</span>
          <input
            className={emailError ? 'input invalid' : 'input'}
            type="email"
            autoComplete="email"
            placeholder="name@smartlab.local"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              if (emailError) setEmailError('')
            }}
            aria-invalid={Boolean(emailError)}
            aria-describedby={emailError ? 'forgot-email-error' : undefined}
            required
          />
          {emailError ? (
            <small className="field-error" id="forgot-email-error">
              {emailError}
            </small>
          ) : null}
        </label>
        <button className="btn primary block auth-submit-btn" type="submit" disabled={isSubmitting}>
          <MailCheck size={18} />
          {isSubmitting ? 'Đang gửi yêu cầu...' : 'Gửi mã xác thực OTP'}
        </button>
        <div className="auth-console-actions">
          <Link className="auth-sublink" to="/login">
            ← Quay lại đăng nhập
          </Link>
        </div>
      </form>
    </div>
  )
}
