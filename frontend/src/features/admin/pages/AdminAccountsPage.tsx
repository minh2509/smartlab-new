import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound, RefreshCw, Save, Send, UserPlus, X } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import {
  listAccounts,
  listPermissions,
  listRoles,
  provisionAccount,
  removeUserPermissionOverride,
  resendInvitation,
  setAccountActive,
  setUserPermissionOverride,
  updateAccountRoles,
} from '../api'
import type { AccountResponse, Permission, Role } from '../../../shared/types/api'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'

type AccountDialog = 'provision' | 'resend' | null

type AccountRowEdit = {
  roleCode: string
  activeValue: string
  permissionCode: string
  permissionEffect: 'GRANT' | 'DENY'
}

export function AdminAccountsPage() {
  const { token } = useAuth()
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [rowEdits, setRowEdits] = useState<Record<string, AccountRowEdit>>({})
  const [activeDialog, setActiveDialog] = useState<AccountDialog>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [provisionRole, setProvisionRole] = useState('')
  const [resendEmail, setResendEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setLoading] = useState(false)

  const mergeRowEdits = useCallback(
    (nextAccounts: AccountResponse[], current: Record<string, AccountRowEdit>) => {
      const next: Record<string, AccountRowEdit> = {}
      const fallbackPermission = permissions[0]?.code || ''
      for (const account of nextAccounts) {
        next[account.userId] = {
          roleCode: account.roles[0] || current[account.userId]?.roleCode || '',
          activeValue: String(account.isActive),
          permissionCode: current[account.userId]?.permissionCode || fallbackPermission,
          permissionEffect: current[account.userId]?.permissionEffect || 'GRANT',
        }
      }
      return next
    },
    [permissions],
  )

  const refreshAccounts = useCallback(async () => {
    if (!token) return
    const accountResult = await listAccounts(token)
    setAccounts(accountResult)
    setRowEdits((current) => mergeRowEdits(accountResult, current))
  }, [mergeRowEdits, token])

  const loadCatalogs = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const [roleResult, permissionResult, accountResult] = await Promise.all([listRoles(token), listPermissions(token), listAccounts(token)])
      setRoles(roleResult)
      setPermissions(permissionResult)
      setAccounts(accountResult)
      setRowEdits((current) => {
        const next: Record<string, AccountRowEdit> = {}
        const fallbackPermission = permissionResult[0]?.code || ''
        for (const account of accountResult) {
          next[account.userId] = {
            roleCode: account.roles[0] || current[account.userId]?.roleCode || '',
            activeValue: String(account.isActive),
            permissionCode: current[account.userId]?.permissionCode || fallbackPermission,
            permissionEffect: current[account.userId]?.permissionEffect || 'GRANT',
          }
        }
        return next
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được dữ liệu quản trị')
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadCatalogs()
  }, [loadCatalogs])

  function openDialog(dialog: Exclude<AccountDialog, null>) {
    setError('')
    setMessage('')
    setActiveDialog(dialog)
  }

  async function handleProvision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setMessage('')
    setError('')
    try {
      await provisionAccount(token, {
        name: name.trim(),
        email: email.trim(),
        roleCodes: provisionRole ? [provisionRole] : undefined,
      })
      setName('')
      setEmail('')
      setProvisionRole('')
      setActiveDialog(null)
      await refreshAccounts()
      setMessage('Đã cấp tài khoản và gửi invite qua email.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cấp được tài khoản')
    }
  }

  async function handleResend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setMessage('')
    setError('')
    try {
      await resendInvitation(token, resendEmail.trim())
      setResendEmail('')
      setActiveDialog(null)
      setMessage('Đã gửi lại invite qua email và cập nhật record cũ.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không gửi lại được invite')
    }
  }

  function updateRowEdit(userId: string, patch: Partial<AccountRowEdit>) {
    setRowEdits((current) => ({
      ...current,
      [userId]: mergeSingleRowEdit(current[userId], patch, permissions[0]?.code || ''),
    }))
  }

  async function handleRowRole(account: AccountResponse) {
    if (!token) return
    const row = rowEdits[account.userId]
    if (!row?.roleCode) return
    setMessage('')
    setError('')
    try {
      await updateAccountRoles(token, account.userId, [row.roleCode])
      await refreshAccounts()
      setMessage('Đã cập nhật role của thành viên.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được role của thành viên')
    }
  }

  async function handleRowActive(account: AccountResponse) {
    if (!token) return
    const row = rowEdits[account.userId]
    setMessage('')
    setError('')
    try {
      await setAccountActive(token, account.userId, row?.activeValue === 'true')
      await refreshAccounts()
      setMessage('Đã cập nhật trạng thái tài khoản.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được trạng thái tài khoản')
    }
  }

  async function handleRowPermissionOverride(account: AccountResponse) {
    if (!token) return
    const row = rowEdits[account.userId]
    if (!row?.permissionCode) return
    setMessage('')
    setError('')
    try {
      await setUserPermissionOverride(token, account.userId, row.permissionCode, row.permissionEffect)
      await refreshAccounts()
      setMessage('Đã cập nhật permission riêng cho thành viên.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được permission riêng')
    }
  }

  async function handleRowRemoveOverride(account: AccountResponse) {
    if (!token) return
    const row = rowEdits[account.userId]
    if (!row?.permissionCode) return
    setMessage('')
    setError('')
    try {
      await removeUserPermissionOverride(token, account.userId, row.permissionCode)
      await refreshAccounts()
      setMessage('Đã xoá override permission của thành viên.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không xoá được override permission')
    }
  }

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Quản trị tài khoản</h1>
          <p>Cấp tài khoản bởi Admin, gửi lại invite, sửa role và permission riêng theo từng thành viên.</p>
        </div>
        <button className="btn" type="button" onClick={() => void loadCatalogs()} disabled={isLoading}>
          <RefreshCw />
          {isLoading ? 'Đang tải...' : 'Tải lại dữ liệu'}
        </button>
      </div>

      <Feedback message={message} error={error} />

      <div className="account-actions">
        <button className="btn primary" type="button" onClick={() => openDialog('provision')}>
          <UserPlus />
          Cấp tài khoản
        </button>
        <button className="btn brand" type="button" onClick={() => openDialog('resend')}>
          <Send />
          Gửi lại invite
        </button>
      </div>

      {activeDialog ? (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setActiveDialog(null)}>
          <section
            className="modal-panel account-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="account-dialog-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-head">
              <div>
                <h2 id="account-dialog-title">{activeDialog === 'provision' ? 'Cấp tài khoản' : 'Gửi lại invite'}</h2>
                <p>{activeDialog === 'provision' ? 'Tạo tài khoản nội bộ và gửi link invite qua email.' : 'Cấp lại token invite mới cho tài khoản đã được tạo.'}</p>
              </div>
              <button className="icon-btn modal-close" type="button" onClick={() => setActiveDialog(null)} aria-label="Đóng popup">
                <X />
              </button>
            </div>

            {activeDialog === 'provision' ? (
              <form className="form-stack" onSubmit={handleProvision}>
                <label className="field">
                  <span>Họ tên</span>
                  <input className="input" value={name} onChange={(event) => setName(event.target.value)} required autoFocus />
                </label>
                <label className="field">
                  <span>Email</span>
                  <input className="input" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
                </label>
                <RoleSelect roles={roles} value={provisionRole} onChange={setProvisionRole} />
                <button className="btn primary full" type="submit">
                  <UserPlus />
                  Cấp tài khoản
                </button>
              </form>
            ) : (
              <form className="form-stack" onSubmit={handleResend}>
                <label className="field">
                  <span>Email</span>
                  <input className="input" type="email" value={resendEmail} onChange={(event) => setResendEmail(event.target.value)} required autoFocus />
                </label>
                <button className="btn brand full" type="submit">
                  <Send />
                  Gửi lại invite
                </button>
              </form>
            )}
          </section>
        </div>
      ) : null}

      <section className="panel page-section">
        <div className="panel-head">
          <div>
            <h2>Danh sách thành viên</h2>
            <p>Quản lý role, trạng thái đăng nhập và permission riêng trực tiếp trên từng thành viên.</p>
          </div>
          <KeyRound />
        </div>

        <AccountsTable
          accounts={accounts}
          roles={roles}
          permissions={permissions}
          rowEdits={rowEdits}
          onEdit={updateRowEdit}
          onSaveRole={(account) => void handleRowRole(account)}
          onSaveActive={(account) => void handleRowActive(account)}
          onSavePermission={(account) => void handleRowPermissionOverride(account)}
          onRemovePermission={(account) => void handleRowRemoveOverride(account)}
        />
      </section>

      {!roles.length && !permissions.length ? (
        <section className="page-section">
          <EmptyState title="Chưa tải được role/permission" description="Kiểm tra token admin hoặc quyền ROLE_MANAGE/PERMISSION_MANAGE." />
        </section>
      ) : null}
    </>
  )
}

function AccountsTable({
  accounts,
  roles,
  permissions,
  rowEdits,
  onEdit,
  onSaveRole,
  onSaveActive,
  onSavePermission,
  onRemovePermission,
}: {
  accounts: AccountResponse[]
  roles: Role[]
  permissions: Permission[]
  rowEdits: Record<string, AccountRowEdit>
  onEdit: (userId: string, patch: Partial<AccountRowEdit>) => void
  onSaveRole: (account: AccountResponse) => void
  onSaveActive: (account: AccountResponse) => void
  onSavePermission: (account: AccountResponse) => void
  onRemovePermission: (account: AccountResponse) => void
}) {
  if (!accounts.length) {
    return <EmptyState title="Chưa có thành viên" description="Khi Admin cấp tài khoản, thành viên sẽ xuất hiện ở bảng này." />
  }

  return (
    <div className="admin-table-wrap">
      <table className="admin-table accounts-table">
        <thead>
          <tr>
            <th>Thành viên</th>
            <th>Role</th>
            <th>Trạng thái</th>
            <th>Permission riêng</th>
            <th>User ID</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((account) => {
            const edit = rowEdits[account.userId] ?? {
              roleCode: account.roles[0] || '',
              activeValue: String(account.isActive),
              permissionCode: permissions[0]?.code || '',
              permissionEffect: 'GRANT' as const,
            }
            return (
              <tr key={account.userId}>
                <td>
                  <div className="member-cell">
                    <span className="member-avatar">{getInitials(account.name)}</span>
                    <span>
                      <strong>{account.name}</strong>
                      <small>{account.email}</small>
                      <span className="inline-badges">
                        <span className={account.isActive ? 'badge success' : 'badge danger'}>{account.isActive ? 'Đang hoạt động' : 'Khoá'}</span>
                        <span className={account.isAccountVerified ? 'badge success' : 'badge info'}>
                          {account.isAccountVerified ? 'Đã kích hoạt' : 'Chờ invite'}
                        </span>
                      </span>
                    </span>
                  </div>
                </td>
                <td>
                  <div className="table-control">
                    <select className="select dense" value={edit.roleCode} onChange={(event) => onEdit(account.userId, { roleCode: event.target.value })}>
                      <option value="">Chọn role</option>
                      {roles.map((role) => (
                        <option key={role.code} value={role.code}>
                          {role.code}
                        </option>
                      ))}
                    </select>
                    <button className="btn table-btn" type="button" onClick={() => onSaveRole(account)} disabled={!edit.roleCode}>
                      <Save />
                      Lưu
                    </button>
                  </div>
                </td>
                <td>
                  <div className="table-control">
                    <select
                      className="select dense"
                      value={edit.activeValue}
                      onChange={(event) => onEdit(account.userId, { activeValue: event.target.value })}
                    >
                      <option value="true">Mở đăng nhập</option>
                      <option value="false">Khoá đăng nhập</option>
                    </select>
                    <button className="btn table-btn" type="button" onClick={() => onSaveActive(account)}>
                      <Save />
                      Lưu
                    </button>
                  </div>
                </td>
                <td>
                  <div className="permission-table-cell">
                    <div className="table-control">
                      <select
                        className="select dense"
                        value={edit.permissionCode}
                        onChange={(event) => onEdit(account.userId, { permissionCode: event.target.value })}
                      >
                        {permissions.map((permission) => (
                          <option key={permission.code} value={permission.code}>
                            {permission.code}
                          </option>
                        ))}
                      </select>
                      <select
                        className="select dense effect-select"
                        value={edit.permissionEffect}
                        onChange={(event) => onEdit(account.userId, { permissionEffect: event.target.value as 'GRANT' | 'DENY' })}
                      >
                        <option value="GRANT">GRANT</option>
                        <option value="DENY">DENY</option>
                      </select>
                    </div>
                    <div className="table-actions">
                      <button className="btn table-btn primary" type="button" onClick={() => onSavePermission(account)} disabled={!edit.permissionCode}>
                        Lưu quyền
                      </button>
                      <button className="btn table-btn" type="button" onClick={() => onRemovePermission(account)} disabled={!edit.permissionCode}>
                        Xoá override
                      </button>
                    </div>
                    <small>{account.permissions.length} permission hiệu lực</small>
                  </div>
                </td>
                <td>
                  <span className="mono user-id-text">{account.userId}</span>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function RoleSelect({
  roles,
  value,
  onChange,
}: {
  roles: Role[]
  value: string
  onChange: (next: string) => void
}) {
  if (!roles.length) {
    return <EmptyState title="Chưa có role" description="Role sẽ được tải từ backend khi tài khoản có quyền xem." />
  }

  return (
    <label className="field">
      <span>Role</span>
      <select className="select" value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">Chọn role mặc định</option>
        {roles.map((role) => (
          <option key={role.code} value={role.code}>
            {role.code} - {role.name}
          </option>
        ))}
      </select>
    </label>
  )
}

function getInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('')
}

function mergeSingleRowEdit(
  current: AccountRowEdit | undefined,
  patch: Partial<AccountRowEdit>,
  fallbackPermission: string,
): AccountRowEdit {
  return {
    roleCode: patch.roleCode ?? current?.roleCode ?? '',
    activeValue: patch.activeValue ?? current?.activeValue ?? 'true',
    permissionCode: patch.permissionCode ?? current?.permissionCode ?? fallbackPermission,
    permissionEffect: patch.permissionEffect ?? current?.permissionEffect ?? 'GRANT',
  }
}
