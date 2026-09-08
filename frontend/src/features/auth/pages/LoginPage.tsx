import { useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound, LogIn } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../authContext'
import { Feedback } from '../../../shared/components/Feedback'
import { Logo } from '../../../shared/components/Logo'

export function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [formError, setFormError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)
  const resetPasswordDone =
    typeof location.state === 'object' && location.state && 'resetPasswordDone' in location.state

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError('')
    try {
      await login(email.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Đăng nhập không thành công')
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
          <span className="auth-console-eyebrow">Smart Lab Workspace</span>
          <h1>Đăng nhập</h1>
          <p>Nhập thông tin tài khoản được cấp để truy cập không gian nghiên cứu.</p>
        </div>
        {resetPasswordDone ? <Feedback message="Đã đổi mật khẩu. Đăng nhập lại bằng mật khẩu mới." /> : null}
        <label className="field">
          <span>Email</span>
          <input
            className={formError ? 'input invalid' : 'input'}
            type="email"
            autoComplete="email"
            placeholder="name@smartlab.local"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              if (formError) setFormError('')
            }}
            aria-invalid={Boolean(formError)}
            aria-describedby={formError ? 'login-error' : undefined}
            required
          />
        </label>
        <label className="field">
          <span>Mật khẩu</span>
          <input
            className={formError ? 'input invalid' : 'input'}
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value)
              if (formError) setFormError('')
            }}
            aria-invalid={Boolean(formError)}
            aria-describedby={formError ? 'login-error' : undefined}
            required
          />
          {formError ? (
            <small className="field-error" id="login-error">
              {formError}
            </small>
          ) : null}
        </label>
        <button className="btn primary block auth-submit-btn" type="submit" disabled={isSubmitting}>
          <LogIn size={18} />
          {isSubmitting ? 'Đang xác thực...' : 'Đăng nhập vào Workspace'}
        </button>
        <div className="auth-console-actions">
          <Link className="auth-sublink" to="/forgot-password">
            <KeyRound size={14} />
            Quên mật khẩu?
          </Link>
        </div>
      </form>
    </div>
  )
}
