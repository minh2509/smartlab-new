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
    <div className="auth-form-wrapper">
      <form className="auth-form-console" onSubmit={handleSubmit}>
        <div className="auth-mobile-brand">
          <Logo />
        </div>
        <div className="auth-console-header">
          <span className="auth-console-eyebrow">Kích hoạt tài khoản thành viên</span>
          <h1>Kích hoạt tài khoản</h1>
          <p>Tài khoản được xác thực bằng mã mời. Vui lòng đặt mật khẩu để hoàn tất.</p>
        </div>
        <Feedback error={error} />
        {!token ? (
          <div className="alert error">
            <KeyRound size={16} />
            <span>Link invite không hợp lệ. Hãy mở đúng liên kết được gửi qua email.</span>
          </div>
        ) : null}
        {account && (
          <div className="alert">
            <CheckCircle2 size={16} />
            <span>Tài khoản {account.email} đã được kích hoạt thành công.</span>
          </div>
        )}
        <label className="field">
          <span>Mật khẩu mới</span>
          <input
            className="input"
            type="password"
            autoComplete="new-password"
            placeholder="Tối thiểu 6 ký tự"
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
            placeholder="Nhập lại mật khẩu trên"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            required
            disabled={!token || Boolean(account)}
          />
        </label>
        <button className="btn primary block auth-submit-btn" type="submit" disabled={isSubmitting || !token || Boolean(account)}>
          <KeyRound size={18} />
          {isSubmitting ? 'Đang kích hoạt...' : 'Kích hoạt tài khoản'}
        </button>
        <div className="auth-console-actions">
          <Link className="auth-sublink" to="/login">
            ← Đã kích hoạt? Đăng nhập ngay
          </Link>
        </div>
      </form>
    </div>
  )
}
