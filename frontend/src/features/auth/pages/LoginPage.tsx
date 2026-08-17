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
    <section className="login-screen">
      <div className="login-card-wrap">
        <form className="login-card card" onSubmit={handleSubmit}>
          <Logo />
          <div className="login-card-head">
            <h1>Đăng nhập</h1>
            <span>Smart Lab Workspace</span>
          </div>
          {resetPasswordDone ? <Feedback message="Đã đổi mật khẩu. Đăng nhập lại bằng mật khẩu mới." /> : null}
          <label className="field">
            <span>Email</span>
            <input
              className={formError ? 'input invalid' : 'input'}
              type="email"
              autoComplete="email"
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
          <button className="btn primary block" type="submit" disabled={isSubmitting}>
            <LogIn />
            {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
          <Link className="btn ghost block" to="/forgot-password">
            <KeyRound />
            Quên mật khẩu
          </Link>
        </form>
      </div>
    </section>
  )
}
