import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { CheckCircle2, KeyRound } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { acceptInvitation } from '../api'
import type { AccountResponse } from '../../../shared/types/api'
import { Feedback } from '../../../shared/components/Feedback'
import { Logo } from '../../../shared/components/Logo'

export function AcceptInvitePage() {
  const [searchParams] = useSearchParams()
  const token = useMemo(() => searchParams.get('token')?.trim() ?? '', [searchParams])
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [error, setError] = useState('')
  const [isSubmitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    setAccount(null)
    try {
      if (!token) {
        throw new Error('Link invite không hợp lệ hoặc thiếu token')
      }
      if (password !== confirmPassword) {
        throw new Error('Mật khẩu nhập lại không khớp')
      }
      const result = await acceptInvitation({ token, password })
      setAccount(result)
      setPassword('')
      setConfirmPassword('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không kích hoạt được tài khoản')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth auth-shell">
      <div className="auth-side">
        <h2>Hoàn tất tài khoản được cấp phát</h2>
        <p>Mở link invite trong email, đặt mật khẩu mới và kích hoạt tài khoản Smart Lab.</p>
      </div>

      <div className="auth-form">
        <form className="auth-box card pad" onSubmit={handleSubmit}>
          <Logo />
          <div>
            <h1>Kích hoạt tài khoản</h1>
            <p className="muted">Invite được xác thực bằng token trong link email. Bạn chỉ cần đặt mật khẩu.</p>
          </div>
          <Feedback error={error} />
          {!token ? (
            <div className="alert error">
              <KeyRound />
              <span>Link invite không hợp lệ. Hãy mở đúng link được gửi qua email.</span>
            </div>
          ) : null}
          {account && (
            <div className="alert">
              <CheckCircle2 />
              <span>Tài khoản {account.email} đã được kích hoạt.</span>
            </div>
          )}
          <label className="field">
            <span>Mật khẩu</span>
            <input
              className="input"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              disabled={!token || Boolean(account)}
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
              disabled={!token || Boolean(account)}
            />
          </label>
          <button className="btn primary block" type="submit" disabled={isSubmitting || !token || Boolean(account)}>
            <KeyRound />
            {isSubmitting ? 'Đang kích hoạt...' : 'Kích hoạt tài khoản'}
          </button>
          <Link className="muted-link" to="/login">
            Đã kích hoạt? Đăng nhập
          </Link>
        </form>
      </div>
    </section>
  )
}
