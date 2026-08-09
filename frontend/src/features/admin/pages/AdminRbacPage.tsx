import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, RefreshCw, Save, X } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { createRole, listPermissions, listRoles, replaceRolePermissions, updateRole } from '../api'
import type { Permission, Role } from '../../../shared/types/api'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'

type RoleForm = {
  code: string
  name: string
  description: string
  isActive: boolean
  mode: 'create' | 'update'
}

const emptyRoleForm: RoleForm = { code: '', name: '', description: '', isActive: true, mode: 'create' }

export function AdminRbacPage() {
  const { token } = useAuth()
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [roleForm, setRoleForm] = useState<RoleForm>(emptyRoleForm)
  const [rolePermissionCode, setRolePermissionCode] = useState('')
  const [selectedPermissionCodes, setSelectedPermissionCodes] = useState<string[]>([])
  const [isRoleDialogOpen, setRoleDialogOpen] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setLoading] = useState(false)

  const loadCatalogs = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const [roleResult, permissionResult] = await Promise.all([listRoles(token), listPermissions(token)])
      setRoles(roleResult)
      setPermissions(permissionResult)
      setRolePermissionCode((current) => current || roleResult[0]?.code || '')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được dữ liệu RBAC')
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadCatalogs()
  }, [loadCatalogs])

  async function handleRoleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setMessage('')
    setError('')
    try {
      const payload = {
        code: roleForm.code.trim(),
        name: roleForm.name.trim(),
        description: roleForm.description.trim() || undefined,
        isActive: roleForm.isActive,
      }
      const result =
        roleForm.mode === 'create' ? await createRole(token, payload) : await updateRole(token, roleForm.code.trim(), payload)
      setRoles((current) => upsertByCode(current, result))
      setRoleForm(emptyRoleForm)
      setRoleDialogOpen(false)
      setMessage(roleForm.mode === 'create' ? 'Đã tạo role.' : 'Đã cập nhật role.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không lưu được role')
    }
  }

  async function handleRolePermissionsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !rolePermissionCode) return
    setMessage('')
    setError('')
    try {
      await replaceRolePermissions(token, rolePermissionCode, selectedPermissionCodes)
      setSelectedPermissionCodes([])
      setMessage('Đã cập nhật permission cho role.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được permission cho role')
    }
  }

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Vai trò & quyền</h1>
          <p>Admin quản lý role và gán permission backend-defined vào từng role.</p>
        </div>
        <button className="btn" type="button" onClick={() => void loadCatalogs()} disabled={isLoading}>
          <RefreshCw />
          {isLoading ? 'Đang tải...' : 'Tải lại'}
        </button>
      </div>

      <Feedback message={message} error={error} />

      <div className="account-actions">
        <button
          className="btn primary"
          type="button"
          onClick={() => {
            setRoleForm(emptyRoleForm)
            setRoleDialogOpen(true)
          }}
        >
          <Plus />
          Tạo role
        </button>
      </div>

      {isRoleDialogOpen ? (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setRoleDialogOpen(false)}>
          <section
            className="modal-panel account-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="role-dialog-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-head">
              <div>
                <h2 id="role-dialog-title">{roleForm.mode === 'create' ? 'Tạo role' : 'Sửa role'}</h2>
                <p>Role inactive sẽ chặn đăng nhập với user đang sở hữu role đó.</p>
              </div>
              <button className="icon-btn modal-close" type="button" onClick={() => setRoleDialogOpen(false)} aria-label="Đóng popup">
                <X />
              </button>
            </div>
            <form className="form-stack" onSubmit={handleRoleSubmit}>
              <label className="field">
                <span>Role code</span>
                <input
                  className="input"
                  value={roleForm.code}
                  onChange={(event) => setRoleForm((form) => ({ ...form, code: event.target.value.toUpperCase() }))}
                  disabled={roleForm.mode === 'update'}
                  required
                  autoFocus
                />
              </label>
              <label className="field">
                <span>Tên role</span>
                <input
                  className="input"
                  value={roleForm.name}
                  onChange={(event) => setRoleForm((form) => ({ ...form, name: event.target.value }))}
                  required
                />
              </label>
              <label className="field">
                <span>Mô tả</span>
                <textarea
                  className="textarea"
                  value={roleForm.description}
                  onChange={(event) => setRoleForm((form) => ({ ...form, description: event.target.value }))}
                />
              </label>
              <label className="check-row">
                <input
                  type="checkbox"
                  checked={roleForm.isActive}
                  onChange={(event) => setRoleForm((form) => ({ ...form, isActive: event.target.checked }))}
                />
                Đang hoạt động
              </label>
              <button className="btn primary full" type="submit">
                {roleForm.mode === 'create' ? <Plus /> : <Save />}
                {roleForm.mode === 'create' ? 'Tạo role' : 'Lưu role'}
              </button>
            </form>
          </section>
        </div>
      ) : null}

      <section className="panel page-section">
        <div className="panel-head">
          <div>
            <h2>Gán permission cho role</h2>
            <p>Chọn toàn bộ permission cần áp dụng cho role.</p>
          </div>
        </div>
        <form className="form-stack" onSubmit={handleRolePermissionsSubmit}>
          <label className="field">
            <span>Role</span>
            <select className="select" value={rolePermissionCode} onChange={(event) => setRolePermissionCode(event.target.value)} required>
              {roles.map((role) => (
                <option key={role.code} value={role.code}>
                  {role.code} - {role.name}
                </option>
              ))}
            </select>
          </label>
          {permissions.length ? (
            <div className="checkbox-grid">
              {permissions.map((permission) => (
                <label className="check-tile" key={permission.code}>
                  <input
                    type="checkbox"
                    checked={selectedPermissionCodes.includes(permission.code)}
                    onChange={(event) =>
                      setSelectedPermissionCodes((current) =>
                        event.target.checked ? [...current, permission.code] : current.filter((code) => code !== permission.code),
                      )
                    }
                  />
                  <span>
                    <strong>{permission.code}</strong>
                    <small>{permission.name}</small>
                  </span>
                </label>
              ))}
            </div>
          ) : (
            <EmptyState title="Chưa có permission" description="Permission được định nghĩa ở backend và seed dữ liệu hệ thống." />
          )}
          <button className="btn primary" type="submit" disabled={!rolePermissionCode}>
            <Save />
            Lưu permission của role
          </button>
        </form>
      </section>

      <div className="panel-grid page-section">
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>Danh sách role</h2>
              <p>Dữ liệu lấy trực tiếp từ backend.</p>
            </div>
          </div>
          {roles.length ? (
            <div className="scroll-table">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Tên</th>
                    <th>Loại</th>
                    <th>Trạng thái</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {roles.map((role) => (
                    <tr key={role.code}>
                      <td className="mono">{role.code}</td>
                      <td>{role.name}</td>
                      <td>{role.isSystem ? 'System' : 'Custom'}</td>
                      <td>
                        <span className={role.isActive ? 'badge success' : 'badge danger'}>
                          {role.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td>
                        <button
                          className="btn sm"
                          type="button"
                          onClick={() => {
                            setRoleForm({ ...role, description: role.description ?? '', mode: 'update' })
                            setRoleDialogOpen(true)
                          }}
                        >
                          Sửa
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="Chưa có role" description="Backend chưa trả role nào cho tài khoản hiện tại." />
          )}
        </section>

        <section className="panel">
          <div className="panel-head">
            <div>
              <h2>Danh sách permission</h2>
              <p>Permission là dữ liệu hệ thống do backend định nghĩa.</p>
            </div>
          </div>
          {permissions.length ? (
            <div className="scroll-table">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Tên</th>
                    <th>Module</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {permissions.map((permission) => (
                    <tr key={permission.code}>
                      <td className="mono">{permission.code}</td>
                      <td>{permission.name}</td>
                      <td>{permission.module || '-'}</td>
                      <td>
                        <span className={permission.isActive ? 'badge success' : 'badge danger'}>
                          {permission.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="Chưa có permission" description="Backend chưa trả permission nào cho tài khoản hiện tại." />
          )}
        </section>
      </div>
    </>
  )
}

function upsertByCode<T extends { code: string }>(items: T[], item: T) {
  const exists = items.some((current) => current.code === item.code)
  return exists ? items.map((current) => (current.code === item.code ? item : current)) : [...items, item]
}
