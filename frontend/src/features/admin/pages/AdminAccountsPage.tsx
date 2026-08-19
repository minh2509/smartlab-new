import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ChevronLeft, ChevronRight, Edit3, KeyRound, RefreshCw, Save, Send, UserPlus, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ApiClientError } from '../../../lib/apiClient'
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
import type { AccountResponse, PaginatedResponse, Permission, Role } from '../../../shared/types/api'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'

const PAGE_SIZE = 10

type AccountDialog = 'provision' | 'resend' | null

type EditDraft = {
  roleCode: string
  activeValue: string
  permissionCode: string
  permissionEffect: 'GRANT' | 'DENY'
}

const emptyPage: PaginatedResponse<AccountResponse> = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

export function AdminAccountsPage() {
  const navigate = useNavigate()
  const { token, clearAuth } = useAuth()
  const toast = useToast()
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [accountPage, setAccountPage] = useState<PaginatedResponse<AccountResponse>>(emptyPage)
  const [page, setPage] = useState(0)
  const [activeDialog, setActiveDialog] = useState<AccountDialog>(null)
  const [editingAccount, setEditingAccount] = useState<AccountResponse | null>(null)
  const [editDraft, setEditDraft] = useState<EditDraft>(createEditDraft(null, []))
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [provisionRole, setProvisionRole] = useState('')
  const [resendEmail, setResendEmail] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setLoading] = useState(false)

  const accounts = accountPage.content
  const pageStart = accountPage.totalElements === 0 ? 0 : accountPage.page * accountPage.size + 1
  const pageEnd = Math.min((accountPage.page + 1) * accountPage.size, accountPage.totalElements)

  const loadAccounts = useCallback(async (targetPage = page) => {
    if (!token) return
    const result = await listAccounts(token, targetPage, PAGE_SIZE)
    setAccountPage(result)
    if (result.totalPages > 0 && targetPage >= result.totalPages) {
      setPage(result.totalPages - 1)
    }
  }, [page, token])

  const loadCatalogs = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const [roleResult, permissionResult, accountResult] = await Promise.all([
        listRoles(token),
        listPermissions(token),
        listAccounts(token, page, PAGE_SIZE),
      ])
      setRoles(roleResult)
      setPermissions(permissionResult)
      setAccountPage(accountResult)
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 401) {
        clearAuth()
        navigate('/login', { replace: true })
        return
      }
      setError(err instanceof Error ? err.message : 'Không tải được dữ liệu quản trị')
    } finally {
      setLoading(false)
    }
  }, [clearAuth, navigate, page, token])

  useEffect(() => {
    void loadCatalogs()
  }, [loadCatalogs])

  function openDialog(dialog: Exclude<AccountDialog, null>) {
    setError('')
    setActiveDialog(dialog)
  }

  function openEditDialog(account: AccountResponse) {
    setError('')
    setEditingAccount(account)
    setEditDraft(createEditDraft(account, permissions))
  }

  function closeEditDialog() {
    setEditingAccount(null)
  }

  async function handleProvision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
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
      setPage(0)
      await loadAccounts(0)
      toast.success('Đã cấp tài khoản', 'Invite đã được gửi qua email.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cấp được tài khoản')
    }
  }

  async function handleResend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setError('')
    try {
      await resendInvitation(token, resendEmail.trim())
      setResendEmail('')
      setActiveDialog(null)
      toast.success('Đã gửi lại invite', 'Bản ghi trước đó đã được cập nhật.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không gửi lại được invite')
    }
  }

  async function handleSaveAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !editingAccount || !editDraft.roleCode) return
    setError('')
    try {
      await updateAccountRoles(token, editingAccount.userId, [editDraft.roleCode])
      await setAccountActive(token, editingAccount.userId, editDraft.activeValue === 'true')
      await loadAccounts()
      closeEditDialog()
      toast.success('Đã cập nhật thành viên', 'Role và trạng thái đã được lưu.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được thành viên')
    }
  }

  async function handleSavePermissionOverride() {
    if (!token || !editingAccount || !editDraft.permissionCode) return
    setError('')
    try {
      await setUserPermissionOverride(token, editingAccount.userId, editDraft.permissionCode, editDraft.permissionEffect)
      await loadAccounts()
      closeEditDialog()
      toast.success('Đã cập nhật permission riêng')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được permission riêng')
    }
  }

  async function handleRemovePermissionOverride() {
    if (!token || !editingAccount || !editDraft.permissionCode) return
    setError('')
    try {
      await removeUserPermissionOverride(token, editingAccount.userId, editDraft.permissionCode)
      await loadAccounts()
      closeEditDialog()
      toast.success('Đã xóa permission override')
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
        <button className="btn ghost" type="button" onClick={() => void loadCatalogs()} disabled={isLoading}>
          <RefreshCw aria-hidden="true" />
          {isLoading ? 'Đang tải...' : 'Tải lại'}
        </button>
      </div>

      <Feedback error={error} />

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

      <AccountActionDialog
        activeDialog={activeDialog}
        roles={roles}
        name={name}
        email={email}
        provisionRole={provisionRole}
        resendEmail={resendEmail}
        onClose={() => setActiveDialog(null)}
        onNameChange={setName}
        onEmailChange={setEmail}
        onProvisionRoleChange={setProvisionRole}
        onResendEmailChange={setResendEmail}
        onProvision={handleProvision}
        onResend={handleResend}
      />

      <EditAccountDialog
        account={editingAccount}
        roles={roles}
        permissions={permissions}
        draft={editDraft}
        onDraftChange={(patch) => setEditDraft((current) => ({ ...current, ...patch }))}
        onClose={closeEditDialog}
        onSaveAccount={handleSaveAccount}
        onSavePermission={() => void handleSavePermissionOverride()}
        onRemovePermission={() => void handleRemovePermissionOverride()}
      />

      <section className="panel page-section">
        <div className="panel-head">
          <div>
            <h2>Danh sách thành viên</h2>
            <p>Bảng chỉ hiển thị thông tin chính. Bấm sửa để cập nhật role, trạng thái và permission riêng.</p>
          </div>
          <KeyRound />
        </div>

        <AccountsTable
          accounts={accounts}
          page={accountPage.page}
          size={accountPage.size}
          onEdit={openEditDialog}
        />

        <PaginationBar
          page={accountPage.page}
          totalPages={accountPage.totalPages}
          totalElements={accountPage.totalElements}
          pageStart={pageStart}
          pageEnd={pageEnd}
          onPrevious={() => setPage((current) => Math.max(current - 1, 0))}
          onNext={() => setPage((current) => current + 1)}
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
  page,
  size,
  onEdit,
}: {
  accounts: AccountResponse[]
  page: number
  size: number
  onEdit: (account: AccountResponse) => void
}) {
  if (!accounts.length) {
    return <EmptyState title="Chưa có thành viên" description="Khi Admin cấp tài khoản, thành viên sẽ xuất hiện ở bảng này." />
  }

  return (
    <div className="admin-table-wrap">
      <table className="admin-table accounts-table">
        <thead>
          <tr>
            <th>#</th>
            <th>Thành viên</th>
            <th>Role</th>
            <th>Trạng thái</th>
            <th>Permission</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((account, index) => (
            <tr key={account.userId}>
              <td className="row-number">{page * size + index + 1}</td>
              <td>
                <div className="member-cell">
                  <span className="member-avatar">{getInitials(account.name)}</span>
                  <span>
                    <strong>{account.name}</strong>
                    <small>{account.email}</small>
                  </span>
                </div>
              </td>
              <td>
                <div className="role-stack">
                  {account.roles.length ? account.roles.map((role) => <span className="badge info" key={role}>{role}</span>) : <span className="muted">Chưa có role</span>}
                </div>
              </td>
              <td>
                <div className="inline-badges account-status-badges">
                  <span className={account.isActive ? 'badge success' : 'badge danger'}>{account.isActive ? 'Đang hoạt động' : 'Khoá đăng nhập'}</span>
                  <span className={account.isAccountVerified ? 'badge success' : 'badge info'}>
                    {account.isAccountVerified ? 'Đã kích hoạt' : 'Chờ invite'}
                  </span>
                </div>
              </td>
              <td>
                <strong>{account.permissions.length}</strong>
                <small className="table-muted">permission hiệu lực</small>
              </td>
              <td>
                <button className="btn table-btn" type="button" onClick={() => onEdit(account)}>
                  <Edit3 />
                  Sửa
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PaginationBar({
  page,
  totalPages,
  totalElements,
  pageStart,
  pageEnd,
  onPrevious,
  onNext,
}: {
  page: number
  totalPages: number
  totalElements: number
  pageStart: number
  pageEnd: number
  onPrevious: () => void
  onNext: () => void
}) {
  if (totalElements === 0) return null

  return (
    <div className="pagination-bar">
      <span>
        Hiển thị {pageStart}-{pageEnd} / {totalElements} thành viên
      </span>
      <div className="pagination-actions">
        <button className="btn table-btn" type="button" onClick={onPrevious} disabled={page <= 0}>
          <ChevronLeft />
          Trước
        </button>
        <strong>
          Trang {page + 1} / {Math.max(totalPages, 1)}
        </strong>
        <button className="btn table-btn" type="button" onClick={onNext} disabled={totalPages === 0 || page + 1 >= totalPages}>
          Sau
          <ChevronRight />
        </button>
      </div>
    </div>
  )
}

function AccountActionDialog({
  activeDialog,
  roles,
  name,
  email,
  provisionRole,
  resendEmail,
  onClose,
  onNameChange,
  onEmailChange,
  onProvisionRoleChange,
  onResendEmailChange,
  onProvision,
  onResend,
}: {
  activeDialog: AccountDialog
  roles: Role[]
  name: string
  email: string
  provisionRole: string
  resendEmail: string
  onClose: () => void
  onNameChange: (next: string) => void
  onEmailChange: (next: string) => void
  onProvisionRoleChange: (next: string) => void
  onResendEmailChange: (next: string) => void
  onProvision: (event: FormEvent<HTMLFormElement>) => void
  onResend: (event: FormEvent<HTMLFormElement>) => void
}) {
  if (!activeDialog) return null

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
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
          <button className="icon-btn modal-close" type="button" onClick={onClose} aria-label="Đóng popup">
            <X />
          </button>
        </div>

        {activeDialog === 'provision' ? (
          <form className="form-stack" onSubmit={onProvision}>
            <label className="field">
              <span>Họ tên</span>
              <input className="input" value={name} onChange={(event) => onNameChange(event.target.value)} required autoFocus />
            </label>
            <label className="field">
              <span>Email</span>
              <input className="input" type="email" value={email} onChange={(event) => onEmailChange(event.target.value)} required />
            </label>
            <RoleSelect roles={roles} value={provisionRole} onChange={onProvisionRoleChange} />
            <button className="btn primary full" type="submit">
              <UserPlus />
              Cấp tài khoản
            </button>
          </form>
        ) : (
          <form className="form-stack" onSubmit={onResend}>
            <label className="field">
              <span>Email</span>
              <input className="input" type="email" value={resendEmail} onChange={(event) => onResendEmailChange(event.target.value)} required autoFocus />
            </label>
            <button className="btn brand full" type="submit">
              <Send />
              Gửi lại invite
            </button>
          </form>
        )}
      </section>
    </div>
  )
}

function EditAccountDialog({
  account,
  roles,
  permissions,
  draft,
  onDraftChange,
  onClose,
  onSaveAccount,
  onSavePermission,
  onRemovePermission,
}: {
  account: AccountResponse | null
  roles: Role[]
  permissions: Permission[]
  draft: EditDraft
  onDraftChange: (patch: Partial<EditDraft>) => void
  onClose: () => void
  onSaveAccount: (event: FormEvent<HTMLFormElement>) => void
  onSavePermission: () => void
  onRemovePermission: () => void
}) {
  const permissionOptions = useMemo(() => permissions.filter((permission) => permission.isActive), [permissions])
  const [isPermissionOverrideOpen, setPermissionOverrideOpen] = useState(false)

  useEffect(() => {
    setPermissionOverrideOpen(false)
  }, [account?.userId])

  if (!account) return null

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="modal-panel account-modal edit-account-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-account-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal-head">
          <div>
            <h2 id="edit-account-title">Sửa thành viên</h2>
            <p>Cập nhật role và trạng thái đăng nhập. Permission mặc định sẽ đi theo role.</p>
          </div>
          <button className="icon-btn modal-close" type="button" onClick={onClose} aria-label="Đóng popup">
            <X />
          </button>
        </div>

        <div className="edit-account-summary">
          <span className="member-avatar">{getInitials(account.name)}</span>
          <span>
            <strong>{account.name}</strong>
            <small>{account.email}</small>
          </span>
        </div>

        <form className="form-stack" onSubmit={onSaveAccount}>
          <RoleSelect roles={roles} value={draft.roleCode} onChange={(roleCode) => onDraftChange({ roleCode })} />
          <label className="field">
            <span>Trạng thái đăng nhập</span>
            <select className="select" value={draft.activeValue} onChange={(event) => onDraftChange({ activeValue: event.target.value })}>
              <option value="true">Mở đăng nhập</option>
              <option value="false">Khoá đăng nhập</option>
            </select>
          </label>
          <button className="btn primary full" type="submit" disabled={!draft.roleCode}>
            <Save />
            Lưu role & trạng thái
          </button>
        </form>

        <div className="permission-action-box">
          <div>
            <strong>Permission riêng</strong>
            <small>Tuỳ chọn mở rộng, chỉ dùng khi cần override ngoài permission mặc định của role.</small>
          </div>
          <button className="btn table-btn" type="button" onClick={() => setPermissionOverrideOpen((current) => !current)}>
            <KeyRound />
            {isPermissionOverrideOpen ? 'Ẩn override' : 'Cấp permission riêng'}
          </button>
        </div>

        {isPermissionOverrideOpen ? (
          <div className="permission-edit-box">
            <div>
              <h3>Override permission</h3>
              <p>Grant hoặc deny một quyền riêng cho user này, nằm trên role permission.</p>
            </div>
            <div className="permission-edit-grid">
              <label className="field">
                <span>Permission</span>
                <select className="select" value={draft.permissionCode} onChange={(event) => onDraftChange({ permissionCode: event.target.value })}>
                  <option value="">Chọn permission</option>
                  {permissionOptions.map((permission) => (
                    <option key={permission.code} value={permission.code}>
                      {permission.code}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Hiệu lực</span>
                <select
                  className="select"
                  value={draft.permissionEffect}
                  onChange={(event) => onDraftChange({ permissionEffect: event.target.value as 'GRANT' | 'DENY' })}
                >
                  <option value="GRANT">GRANT</option>
                  <option value="DENY">DENY</option>
                </select>
              </label>
            </div>
            <div className="form-actions">
              <button className="btn brand" type="button" onClick={onSavePermission} disabled={!draft.permissionCode}>
                <Save />
                Lưu
              </button>
              <button className="btn" type="button" onClick={onRemovePermission} disabled={!draft.permissionCode}>
                Xoá
              </button>
            </div>
            <small>{account.permissions.length} permission hiệu lực hiện tại</small>
          </div>
        ) : null}
      </section>
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
        <option value="">Chọn role</option>
        {roles.map((role) => (
          <option key={role.code} value={role.code}>
            {role.code} - {role.name}
          </option>
        ))}
      </select>
    </label>
  )
}

function createEditDraft(account: AccountResponse | null, permissions: Permission[]): EditDraft {
  return {
    roleCode: account?.roles[0] || '',
    activeValue: String(account?.isActive ?? true),
    permissionCode: permissions.find((permission) => permission.isActive)?.code || permissions[0]?.code || '',
    permissionEffect: 'GRANT',
  }
}

function getInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('')
}
