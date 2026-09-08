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
  previewBulkInvitations,
  provisionBulkInvitations,
  provisionAccount,
  removeUserPermissionOverride,
  resendInvitation,
  setAccountActive,
  setUserPermissionOverride,
  updateAccountRoles,
} from '../api'
import type { AccountResponse, BulkInvitationPreviewResponse, BulkInvitationRow, PaginatedResponse, Permission, Role } from '../../../shared/types/api'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { PopupSelect } from '../../../shared/ui/PopupSelect'

const PAGE_SIZE = 10

type AccountDialog = 'provision' | 'resend' | null

type EditDraft = {
  roleCode: string
  activeValue: string
  permissionCode: string
  permissionEffect: 'GRANT' | 'DENY'
}

type BulkCsvParseResult = {
  items: BulkInvitationRow[]
  errors: string[]
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
  const [isBulkDialogOpen, setBulkDialogOpen] = useState(false)
  const [bulkCsv, setBulkCsv] = useState('')
  const [bulkRole, setBulkRole] = useState('')
  const [bulkPreview, setBulkPreview] = useState<BulkInvitationPreviewResponse | null>(null)
  const [bulkError, setBulkError] = useState('')
  const [isBulkSubmitting, setBulkSubmitting] = useState(false)
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
    if (!provisionRole) {
      setError('Chọn một role đang hoạt động trước khi cấp tài khoản.')
      return
    }
    setError('')
    try {
      await provisionAccount(token, {
        name: name.trim(),
        email: email.trim(),
        roleCodes: [provisionRole],
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

  async function handleBulkPreview() {
    if (!token) return
    const items = validateBulkInput()
    if (!items) return
    setBulkError('')
    setBulkSubmitting(true)
    try {
      setBulkPreview(await previewBulkInvitations(token, items, [bulkRole]))
    } catch (err) {
      setBulkError(err instanceof Error ? err.message : 'Không kiểm tra được danh sách import')
    } finally {
      setBulkSubmitting(false)
    }
  }

  async function handleBulkProvision() {
    if (!token) return
    const items = validateBulkInput()
    if (!items) return
    if (!bulkPreview) {
      setBulkError('Hãy bấm “Kiểm tra danh sách” trước khi tạo batch.')
      return
    }
    if (bulkPreview.acceptedCount === 0) {
      setBulkError('Danh sách không có dòng hợp lệ để tạo batch.')
      return
    }
    setBulkError('')
    setBulkSubmitting(true)
    try {
      const batch = await provisionBulkInvitations(token, items, [bulkRole])
      setBulkDialogOpen(false)
      setBulkCsv('')
      setBulkRole('')
      setBulkPreview(null)
      setBulkError('')
      setPage(0)
      await loadAccounts(0)
      toast.success('Đã tạo batch mời', `${batch.acceptedCount} tài khoản được xếp hàng gửi email.`)
    } catch (err) {
      setBulkError(err instanceof Error ? err.message : 'Không tạo được batch mời')
    } finally {
      setBulkSubmitting(false)
    }
  }

  function validateBulkInput(): BulkInvitationRow[] | null {
    const parsed = parseBulkRows(bulkCsv)
    if (parsed.errors.length) {
      setBulkError(parsed.errors.join(' '))
      return null
    }
    if (!parsed.items.length) {
      setBulkError('Nhập ít nhất một dòng theo mẫu “Họ tên, email”.')
      return null
    }
    if (parsed.items.length > 100) {
      setBulkError('Mỗi batch tối đa 100 dòng. Hãy tách danh sách thành các batch nhỏ hơn.')
      return null
    }
    if (!bulkRole) {
      setBulkError('Chọn role áp dụng cho cả batch trước khi tiếp tục.')
      return null
    }
    return parsed.items
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
        <button className="btn primary" type="button" onClick={() => { setError(''); setBulkError(''); setBulkPreview(null); setBulkDialogOpen(true) }}>
          <UserPlus />
          Cấp hàng loạt
        </button>
      </div>

      <AccountActionDialog
        activeDialog={activeDialog}
        roles={roles.filter((role) => role.isActive)}
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

      <BulkInvitationDialog
        open={isBulkDialogOpen}
        roles={roles.filter((role) => role.isActive)}
        csv={bulkCsv}
        role={bulkRole}
        preview={bulkPreview}
        error={bulkError}
        submitting={isBulkSubmitting}
        onClose={() => { if (!isBulkSubmitting) { setBulkDialogOpen(false); setBulkError('') } }}
        onCsvChange={(value) => { setBulkCsv(value); setBulkPreview(null); setBulkError('') }}
        onRoleChange={(value) => { setBulkRole(value); setBulkPreview(null); setBulkError('') }}
        onPreview={() => void handleBulkPreview()}
        onProvision={() => void handleBulkProvision()}
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

function BulkInvitationDialog({
  open, roles, csv, role, preview, error, submitting, onClose, onCsvChange, onRoleChange, onPreview, onProvision,
}: {
  open: boolean
  roles: Role[]
  csv: string
  role: string
  preview: BulkInvitationPreviewResponse | null
  error: string
  submitting: boolean
  onClose: () => void
  onCsvChange: (value: string) => void
  onRoleChange: (value: string) => void
  onPreview: () => void
  onProvision: () => void
}) {
  if (!open) return null
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="modal-panel account-modal bulk-invitation-modal" role="dialog" aria-modal="true" aria-labelledby="bulk-invite-title" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h2 id="bulk-invite-title">Cấp tài khoản hàng loạt</h2>
            <p>Dán tối đa 100 dòng theo mẫu <code>Họ tên, email</code>. Hệ thống kiểm tra trước, rồi mới tạo account và xếp mail vào hàng đợi.</p>
          </div>
          <button className="icon-btn modal-close" type="button" onClick={onClose} aria-label="Đóng popup" disabled={submitting}><X /></button>
        </div>
        <div className="form-stack">
          <label className="field">
            <span>Danh sách CSV</span>
            <textarea className="textarea" rows={7} value={csv} onChange={(event) => onCsvChange(event.target.value)}
              placeholder={'Nguyen Van A, nguyena@example.edu.vn\nTran Thi B, tranb@example.edu.vn'} disabled={submitting} />
            <small>Một dòng một người. Dấu phẩy thừa ở cuối dòng được bỏ qua.</small>
          </label>
          <label className="field">
            <span>Role áp dụng cho cả batch</span>
            <PopupSelect
              value={role}
              onChange={onRoleChange}
              disabled={submitting}
              ariaLabel="Role áp dụng cho cả batch"
              options={[
                { value: '', label: 'Chọn role đang hoạt động' },
                ...roles.map((item) => ({ value: item.code, label: roleOptionLabel(item) })),
              ]}
            />
          </label>
          {error ? <div className="alert error bulk-invitation-error" role="alert">{error}</div> : null}
          {preview ? (
            <section className="bulk-preview" aria-label="Kết quả kiểm tra danh sách">
              <div className="bulk-preview-head">
                <div>
                  <h3>Kết quả kiểm tra</h3>
                  <p>Kiểm tra kỹ các dòng bị từ chối trước khi tạo batch.</p>
                </div>
                <div className="inline-badges">
                  <span className="badge success">{preview.acceptedCount} hợp lệ</span>
                  <span className={preview.rejectedCount ? 'badge danger' : 'badge info'}>{preview.rejectedCount} bị bỏ qua</span>
                </div>
              </div>
              <div className="admin-table-wrap bulk-preview-table-wrap">
                <table className="admin-table bulk-preview-table">
                  <thead><tr><th>Dòng</th><th>Họ tên</th><th>Email</th><th>Kết quả</th><th>Ghi chú</th></tr></thead>
                  <tbody>
                    {preview.items.slice(0, 10).map((item) => (
                      <tr key={item.sourceRow}>
                        <td className="row-number">{item.sourceRow}</td>
                        <td>{item.fullName || '—'}</td>
                        <td>{item.email || '—'}</td>
                        <td><span className={`badge ${bulkStatusTone(item.status)}`}>{bulkStatusLabel(item.status)}</span></td>
                        <td className="bulk-preview-message">{item.failureMessage || 'Sẵn sàng tạo account'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {preview.items.length > 10 ? <p className="bulk-preview-note">Đang hiển thị 10 / {preview.items.length} dòng. Server vẫn kiểm tra toàn bộ danh sách.</p> : null}
            </section>
          ) : null}
          <div className="modal-actions">
            <button className="btn ghost" type="button" onClick={onClose} disabled={submitting}>Hủy</button>
            <button className="btn brand" type="button" onClick={onPreview} disabled={submitting}>Kiểm tra danh sách</button>
            <button className="btn primary" type="button" onClick={onProvision} disabled={submitting}>
              <Send />
              {submitting ? 'Đang xử lý...' : 'Tạo batch và gửi mail'}
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}

function parseBulkRows(value: string): BulkCsvParseResult {
  const items: BulkInvitationRow[] = []
  const errors: string[] = []
  value.split(/\r?\n/).forEach((rawLine, index) => {
    const line = index === 0 ? rawLine.replace(/^\uFEFF/, '') : rawLine
    const parsedLine = parseCsvLine(line)
    if (parsedLine.hasUnclosedQuote) {
      errors.push(`Dòng ${index + 1} có dấu nháy kép chưa đóng.`)
      return
    }
    const columns = parsedLine.columns.map((column) => column.trim())
    if (columns.every((column) => !column)) return
    while (columns.length > 2 && !columns.at(-1)) columns.pop()
    if (columns.length > 2) {
      errors.push(`Dòng ${index + 1} chỉ được gồm Họ tên và Email.`)
      return
    }
    items.push({ fullName: columns[0] ?? '', email: columns[1] ?? '' })
  })
  return { items, errors }
}

function parseCsvLine(line: string): { columns: string[]; hasUnclosedQuote: boolean } {
  const columns: string[] = []
  let current = ''
  let quoted = false
  for (let index = 0; index < line.length; index += 1) {
    const character = line[index]
    if (character === '"') {
      if (quoted && line[index + 1] === '"') { current += '"'; index += 1 } else quoted = !quoted
    } else if (!quoted && (character === ',' || character === ';')) {
      columns.push(current); current = ''
    } else current += character
  }
  columns.push(current)
  return { columns, hasUnclosedQuote: quoted }
}

function bulkStatusLabel(status: string) {
  switch (status) {
    case 'VALID': return 'Hợp lệ'
    case 'REJECTED_INVALID': return 'Dữ liệu sai'
    case 'REJECTED_DUPLICATE': return 'Trùng trong danh sách'
    case 'REJECTED_ALREADY_EXISTS': return 'Email đã tồn tại'
    default: return status
  }
}

function bulkStatusTone(status: string) {
  return status === 'VALID' ? 'success' : 'danger'
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
            <PopupSelect
              value={draft.activeValue}
              onChange={(activeValue) => onDraftChange({ activeValue })}
              ariaLabel="Trạng thái đăng nhập"
              options={[{ value: 'true', label: 'Mở đăng nhập' }, { value: 'false', label: 'Khoá đăng nhập' }]}
            />
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
                <PopupSelect
                  value={draft.permissionCode}
                  onChange={(permissionCode) => onDraftChange({ permissionCode })}
                  ariaLabel="Permission riêng"
                  options={[{ value: '', label: 'Chọn permission' }, ...permissionOptions.map((permission) => ({ value: permission.code, label: permission.code }))]}
                />
              </label>
              <label className="field">
                <span>Hiệu lực</span>
                <PopupSelect
                  value={draft.permissionEffect}
                  onChange={(permissionEffect) => onDraftChange({ permissionEffect: permissionEffect as 'GRANT' | 'DENY' })}
                  ariaLabel="Hiệu lực permission riêng"
                  options={[{ value: 'GRANT', label: 'GRANT' }, { value: 'DENY', label: 'DENY' }]}
                />
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
      <PopupSelect
        value={value}
        onChange={onChange}
        ariaLabel="Chọn role"
        options={[
          { value: '', label: 'Chọn role' },
          ...roles.map((role) => ({ value: role.code, label: roleOptionLabel(role) })),
        ]}
      />
    </label>
  )
}

function roleOptionLabel(role: Role) {
  return role.name.trim().toUpperCase() === role.code.toUpperCase()
    ? role.code
    : `${role.name} (${role.code})`
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
