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
    <section className="login-screen">
      <div className="login-card-wrap">
        <form className="login-card card" onSubmit={handleSubmit}>
          <Logo />
          <div className="login-card-head">
            <h1>Quên mật khẩu</h1>
            <span>Bước 1/3 · Nhận OTP qua email</span>
          </div>
          <label className="field">
            <span>Email</span>
            <input
              className={emailError ? 'input invalid' : 'input'}
              type="email"
              autoComplete="email"
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
          <button className="btn primary block" type="submit" disabled={isSubmitting}>
            <MailCheck />
            {isSubmitting ? 'Đang gửi...' : 'Gửi OTP'}
          </button>
          <Link className="btn ghost block" to="/login">
            Quay lại đăng nhập
          </Link>
        </form>
      </div>
    </section>
  )
}
