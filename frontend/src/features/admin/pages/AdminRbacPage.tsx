import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useGSAP } from '@gsap/react'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { ChevronRight, Plus, Power, RefreshCw, Save, X } from 'lucide-react'
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

type PermissionGroup = {
  module: string
  permissions: Permission[]
}

const emptyRoleForm: RoleForm = { code: '', name: '', description: '', isActive: true, mode: 'create' }

gsap.registerPlugin(useGSAP, ScrollTrigger)

export function AdminRbacPage() {
  const { token } = useAuth()
  const rbacRootRef = useRef<HTMLElement | null>(null)
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [roleForm, setRoleForm] = useState<RoleForm>(emptyRoleForm)
  const [rolePermissionCode, setRolePermissionCode] = useState('')
  const [selectedPermissionCodes, setSelectedPermissionCodes] = useState<string[]>([])
  const [isRoleDialogOpen, setRoleDialogOpen] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setLoading] = useState(false)

  const permissionGroups = useMemo(() => groupPermissions(permissions), [permissions])
  const selectedPermissionSet = useMemo(() => new Set(selectedPermissionCodes), [selectedPermissionCodes])
  const selectedRole = roles.find((role) => role.code === rolePermissionCode)
  const editingSystemRole = roleForm.mode === 'update' && roles.some((role) => role.code === roleForm.code && role.isSystem)

  useGSAP(
    () => {
      if (!rbacRootRef.current) return
      const media = gsap.matchMedia()
      media.add('(prefers-reduced-motion: no-preference)', () => {
        const context = gsap.context(() => {
          gsap.from('.rbac-panel-head > *, .rbac-matrix-wrap, .rbac-save-row', {
            autoAlpha: 0,
            duration: 0.58,
            ease: 'power3.out',
            stagger: 0.07,
            y: 18,
          })
          const matrix = rbacRootRef.current?.querySelector('.rbac-matrix-wrap')
          const rows = gsap.utils.toArray<HTMLElement>('.permission-module-row')
          if (matrix && rows.length) {
            gsap.from(rows, {
              autoAlpha: 0,
              duration: 0.42,
              ease: 'power2.out',
              stagger: 0.035,
              scrollTrigger: {
                trigger: matrix,
                start: 'top 84%',
                once: true,
              },
              x: -12,
            })
          }
        }, rbacRootRef)
        return () => context.revert()
      })
      return () => media.revert()
    },
    { dependencies: [permissionGroups.length, rolePermissionCode], scope: rbacRootRef },
  )

  const loadCatalogs = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const [roleResult, permissionResult] = await Promise.all([listRoles(token), listPermissions(token)])
      setRoles(roleResult)
      setPermissions(permissionResult)
      setRolePermissionCode((current) => {
        const nextCode = current || roleResult[0]?.code || ''
        const nextRole = roleResult.find((role) => role.code === nextCode) || roleResult[0]
        setSelectedPermissionCodes(nextRole?.permissionCodes ?? [])
        return nextRole?.code ?? ''
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được dữ liệu RBAC')
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadCatalogs()
  }, [loadCatalogs])

  function handleRolePermissionChange(nextCode: string) {
    setRolePermissionCode(nextCode)
    setSelectedPermissionCodes(roles.find((role) => role.code === nextCode)?.permissionCodes ?? [])
  }

  function togglePermission(permissionCode: string) {
    setSelectedPermissionCodes((current) =>
      current.includes(permissionCode)
        ? current.filter((code) => code !== permissionCode)
        : [...current, permissionCode],
    )
  }

  function setGroupPermissions(group: PermissionGroup, checked: boolean) {
    const groupCodes = group.permissions.map((permission) => permission.code)
    setSelectedPermissionCodes((current) => {
      const currentSet = new Set(current)
      groupCodes.forEach((code) => {
        if (checked) currentSet.add(code)
        else currentSet.delete(code)
      })
      return Array.from(currentSet).sort()
    })
  }

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
      setRoles((current) => upsertByCode(current, mergeRolePermissions(result, current)))
      setRoleForm(emptyRoleForm)
      setRoleDialogOpen(false)
      setMessage(roleForm.mode === 'create' ? 'Đã tạo role.' : 'Đã cập nhật role.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không lưu được role')
    }
  }

  async function handleToggleSelectedRoleActive() {
    if (!token || !selectedRole) return
    if (selectedRole.isSystem) {
      setError('Role hệ thống không được khoá hoặc kích hoạt thủ công.')
      return
    }
    setMessage('')
    setError('')
    try {
      const result = await updateRole(token, selectedRole.code, {
        code: selectedRole.code,
        name: selectedRole.name,
        description: selectedRole.description,
        isActive: !selectedRole.isActive,
      })
      setRoles((current) => upsertByCode(current, mergeRolePermissions(result, current)))
      setMessage(result.isActive ? 'Đã kích hoạt role.' : 'Đã khoá role.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không cập nhật được trạng thái role')
    }
  }

  function openSelectedRoleEditor() {
    if (!selectedRole) return
    setRoleForm({
      code: selectedRole.code,
      name: selectedRole.name,
      description: selectedRole.description ?? '',
      isActive: selectedRole.isActive,
      mode: 'update',
    })
    setRoleDialogOpen(true)
  }

  async function handleRolePermissionsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !rolePermissionCode) return
    setMessage('')
    setError('')
    try {
      await replaceRolePermissions(token, rolePermissionCode, selectedPermissionCodes)
      setRoles((current) =>
        current.map((role) => role.code === rolePermissionCode ? { ...role, permissionCodes: selectedPermissionCodes } : role),
      )
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
          <p>Admin quản lý vai trò hệ thống và gán quyền mặc định cho từng vai trò.</p>
        </div>
        <button className="btn" type="button" onClick={() => void loadCatalogs()} disabled={isLoading}>
          <RefreshCw />
          {isLoading ? 'Đang tải...' : 'Tải lại'}
        </button>
      </div>

      <Feedback message={message} error={error} />

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
                  disabled={editingSystemRole}
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

      <section className="panel page-section rbac-command-panel" ref={rbacRootRef}>
        <div className="panel-head rbac-panel-head">
          <div>
            <h2>Gán quyền cho vai trò</h2>
            <p>Chọn vai trò, mở từng module và tick các quyền con cần áp dụng.</p>
          </div>
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
        <form className="form-stack" onSubmit={handleRolePermissionsSubmit}>
          <div className="role-permission-head rbac-command-deck rbac-role-toolbar">
            <label className="field role-select-field rbac-role-select">
              <span>Role</span>
              <select className="select" value={rolePermissionCode} onChange={(event) => handleRolePermissionChange(event.target.value)} required>
                {roles.map((role) => (
                  <option key={role.code} value={role.code}>
                    {role.code} - {role.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="role-permission-summary rbac-role-state">
              <span className={selectedRole?.isActive ? 'badge success' : 'badge danger'}>
                {selectedRole?.isActive ? 'Đang hoạt động' : 'Đã khoá'}
              </span>
              <span className="permission-count-pill">
                <strong>{selectedPermissionCodes.length}</strong>
                <small>quyền đang chọn</small>
              </span>
            </div>
            <div className="rbac-role-actions">
              <button className="btn table-btn" type="button" onClick={openSelectedRoleEditor} disabled={!selectedRole}>
                Sửa role
              </button>
              <button
                className="btn table-btn"
                type="button"
                onClick={() => void handleToggleSelectedRoleActive()}
                disabled={!selectedRole || selectedRole.isSystem}
                title={selectedRole?.isSystem ? 'Role hệ thống không được khoá' : undefined}
              >
                <Power />
                {selectedRole?.isActive ? 'Khoá role' : 'Kích hoạt'}
              </button>
            </div>
          </div>

          {permissionGroups.length ? (
            <PermissionModuleTable
              groups={permissionGroups}
              selectedPermissionSet={selectedPermissionSet}
              onTogglePermission={togglePermission}
              onSetGroup={setGroupPermissions}
            />
          ) : (
            <EmptyState title="Chưa có permission" description="Permission được định nghĩa ở backend và seed dữ liệu hệ thống." />
          )}
          <div className="rbac-save-row">
            <button className="btn primary rbac-save-btn" type="submit" disabled={!rolePermissionCode}>
              <Save />
              Lưu quyền của vai trò
            </button>
          </div>
        </form>
      </section>

    </>
  )
}

function PermissionModuleTable({
  groups,
  selectedPermissionSet,
  onTogglePermission,
  onSetGroup,
}: {
  groups: PermissionGroup[]
  selectedPermissionSet: Set<string>
  onTogglePermission: (permissionCode: string) => void
  onSetGroup: (group: PermissionGroup, checked: boolean) => void
}) {
  const [expandedModule, setExpandedModule] = useState<string | null>(null)

  return (
    <div className="admin-table-wrap permission-module-table-wrap rbac-matrix-wrap">
      <table className="admin-table permission-module-table rbac-matrix">
        <thead>
          <tr>
            <th>Module</th>
            <th>Đã chọn</th>
            <th>Mô tả</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {groups.map((group) => {
            const selectedCount = group.permissions.filter((permission) => selectedPermissionSet.has(permission.code)).length
            const allSelected = selectedCount === group.permissions.length
            const isExpanded = expandedModule === group.module
            return (
              <Fragment key={group.module}>
                <tr className={isExpanded ? 'permission-module-row expanded' : 'permission-module-row'}>
                  <td>
                    <strong>{group.module}</strong>
                  </td>
                  <td>
                    <span className={selectedCount ? 'badge success' : 'badge info'}>
                      {selectedCount}/{group.permissions.length} quyền
                    </span>
                  </td>
                  <td>{describeModule(group)}</td>
                  <td>
                    <button
                      className="btn table-btn permission-menu-trigger"
                      type="button"
                      onClick={() => setExpandedModule(isExpanded ? null : group.module)}
                      aria-expanded={isExpanded}
                    >
                      {isExpanded ? 'Ẩn quyền' : 'Chọn quyền'}
                      <ChevronRight />
                    </button>
                  </td>
                </tr>
                {isExpanded ? (
                  <tr className="permission-expand-row">
                    <td colSpan={4}>
                      <div className="permission-expand-panel rbac-permission-drawer">
                        <div className="permission-expand-head">
                          <div>
                            <strong>{group.module}</strong>
                            <span>{selectedCount}/{group.permissions.length} quyền đang chọn</span>
                          </div>
                          <div className="permission-expand-actions">
                            <button className="btn table-btn" type="button" onClick={() => onSetGroup(group, true)} disabled={allSelected}>
                              Chọn tất cả
                            </button>
                            <button className="btn table-btn" type="button" onClick={() => onSetGroup(group, false)} disabled={!selectedCount}>
                              Bỏ chọn
                            </button>
                          </div>
                        </div>
                        <div className="permission-expand-list">
                          {group.permissions.map((permission) => (
                            <label className="permission-expand-item" key={permission.code}>
                              <input
                                type="checkbox"
                                checked={selectedPermissionSet.has(permission.code)}
                                onChange={() => onTogglePermission(permission.code)}
                              />
                              <span>
                                <strong>{permission.code}</strong>
                                <small>{permission.name}</small>
                              </span>
                            </label>
                          ))}
                        </div>
                      </div>
                    </td>
                  </tr>
                ) : null}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function groupPermissions(permissions: Permission[]): PermissionGroup[] {
  const groups = new Map<string, Permission[]>()
  for (const permission of permissions) {
    const module = (permission.module || permission.code.split('_')[0] || 'SYSTEM').toUpperCase()
    groups.set(module, [...(groups.get(module) ?? []), permission])
  }
  return Array.from(groups.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([module, items]) => ({
      module,
      permissions: items.sort((left, right) => left.code.localeCompare(right.code)),
    }))
}

function describeModule(group: PermissionGroup) {
  return group.permissions
    .slice(0, 2)
    .map((permission) => permission.name)
    .join(', ')
}

function mergeRolePermissions(role: Role, currentRoles: Role[]): Role {
  return {
    ...role,
    permissionCodes: role.permissionCodes ?? currentRoles.find((current) => current.code === role.code)?.permissionCodes ?? [],
  }
}

function upsertByCode<T extends { code: string }>(items: T[], item: T) {
  const exists = items.some((current) => current.code === item.code)
  return exists ? items.map((current) => (current.code === item.code ? item : current)) : [...items, item]
}
