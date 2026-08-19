import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ChevronRight, Plus, Power, RefreshCw, RotateCcw, Save, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ApiClientError } from '../../../lib/apiClient'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import type { Permission, Role } from '../../../shared/types/api'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'
import { OverlayPortalHost } from '../../../shared/ui/OverlayPortalHost'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { useAuth } from '../../auth/authContext'
import { createRole, listPermissions, listRoles, replaceRolePermissions, updateRole } from '../api'
import './AdminRbacPage.css'

type RoleForm = {
  code: string
  name: string
  description: string
  isActive: boolean
  mode: 'create' | 'update'
}

type PermissionGroup = { module: string; permissions: Permission[] }
type ConfirmAction = 'discard-role-switch' | 'lock-role' | null

const emptyRoleForm: RoleForm = { code: '', name: '', description: '', isActive: true, mode: 'create' }

export function AdminRbacPage() {
  const navigate = useNavigate()
  const { token, clearAuth } = useAuth()
  const toast = useToast()
  const roleDialogRef = useRef<HTMLDialogElement>(null)
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [roleForm, setRoleForm] = useState<RoleForm>(emptyRoleForm)
  const [rolePermissionCode, setRolePermissionCode] = useState('')
  const [selectedPermissionCodes, setSelectedPermissionCodes] = useState<string[]>([])
  const [isRoleDialogOpen, setRoleDialogOpen] = useState(false)
  const [pendingRoleCode, setPendingRoleCode] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction>(null)
  const [catalogError, setCatalogError] = useState('')
  const [roleDialogError, setRoleDialogError] = useState('')
  const [workspaceError, setWorkspaceError] = useState('')
  const [isLoading, setLoading] = useState(false)
  const [isSavingRole, setSavingRole] = useState(false)
  const [isSavingPermissions, setSavingPermissions] = useState(false)
  const [isUpdatingRoleState, setUpdatingRoleState] = useState(false)

  const permissionGroups = useMemo(() => groupPermissions(permissions), [permissions])
  const selectedPermissionSet = useMemo(() => new Set(selectedPermissionCodes), [selectedPermissionCodes])
  const selectedRole = roles.find((role) => role.code === rolePermissionCode)
  const selectedRolePermissionCodes = selectedRole?.permissionCodes ?? []
  const hasPermissionChanges = !sameCodeSet(selectedPermissionCodes, selectedRolePermissionCodes)
  const permissionChangeCount = differenceCount(selectedPermissionCodes, selectedRolePermissionCodes)
  const editingSystemRole = roleForm.mode === 'update' && roles.some((role) => role.code === roleForm.code && role.isSystem)
  const roleOptions = useMemo(() => roles.map((role) => ({
    value: role.code,
    label: `${role.code} - ${role.name}`,
    description: `${role.isSystem ? 'Hệ thống' : 'Tùy chỉnh'} · ${role.isActive ? 'Đang hoạt động' : 'Đã khóa'}`,
  })), [roles])

  useEffect(() => {
    const dialog = roleDialogRef.current
    if (!isRoleDialogOpen || !dialog || dialog.open) return
    dialog.showModal()
    window.requestAnimationFrame(() => dialog.querySelector<HTMLElement>('[data-role-dialog-initial]')?.focus())
  }, [isRoleDialogOpen])

  const loadCatalogs = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setCatalogError('')
    try {
      const [roleResult, permissionResult] = await Promise.all([listRoles(token), listPermissions(token)])
      setRoles(roleResult)
      setPermissions(permissionResult)
      setRolePermissionCode((current) => {
        const nextRole = roleResult.find((role) => role.code === current) ?? roleResult[0]
        setSelectedPermissionCodes(normalizeCodes(nextRole?.permissionCodes ?? []))
        return nextRole?.code ?? ''
      })
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 401) {
        clearAuth()
        navigate('/login', { replace: true })
        return
      }
      setCatalogError(err instanceof Error ? err.message : 'Không tải được dữ liệu RBAC')
    } finally {
      setLoading(false)
    }
  }, [clearAuth, navigate, token])

  useEffect(() => { void loadCatalogs() }, [loadCatalogs])

  function switchRole(nextCode: string) {
    const nextRole = roles.find((role) => role.code === nextCode)
    setRolePermissionCode(nextCode)
    setSelectedPermissionCodes(normalizeCodes(nextRole?.permissionCodes ?? []))
    setWorkspaceError('')
  }

  function handleRolePermissionChange(nextCode: string) {
    if (nextCode === rolePermissionCode) return
    if (hasPermissionChanges) {
      setPendingRoleCode(nextCode)
      setConfirmAction('discard-role-switch')
      return
    }
    switchRole(nextCode)
  }

  function togglePermission(permissionCode: string) {
    setWorkspaceError('')
    setSelectedPermissionCodes((current) => normalizeCodes(current.includes(permissionCode)
      ? current.filter((code) => code !== permissionCode)
      : [...current, permissionCode]))
  }

  function setGroupPermissions(group: PermissionGroup, checked: boolean) {
    setWorkspaceError('')
    const groupCodes = group.permissions.map((permission) => permission.code)
    setSelectedPermissionCodes((current) => {
      const next = new Set(current)
      groupCodes.forEach((code) => checked ? next.add(code) : next.delete(code))
      return normalizeCodes(next)
    })
  }

  function restoreSelectedRolePermissions() {
    setSelectedPermissionCodes(normalizeCodes(selectedRolePermissionCodes))
    setWorkspaceError('')
  }

  function closeRoleDialog() {
    if (isSavingRole) return
    finishRoleDialog()
  }

  function finishRoleDialog() {
    if (roleDialogRef.current?.open) roleDialogRef.current.close()
    setRoleDialogOpen(false)
    setRoleDialogError('')
    setRoleForm(emptyRoleForm)
  }

  function openRoleDialog(mode: 'create' | 'update') {
    setRoleDialogError('')
    setRoleForm(mode === 'create' ? emptyRoleForm : {
      code: selectedRole?.code ?? '',
      name: selectedRole?.name ?? '',
      description: selectedRole?.description ?? '',
      isActive: selectedRole?.isActive ?? true,
      mode,
    })
    setRoleDialogOpen(true)
  }

  async function handleRoleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setSavingRole(true)
    setRoleDialogError('')
    try {
      const payload = {
        code: roleForm.code.trim(), name: roleForm.name.trim(),
        description: roleForm.description.trim() || undefined, isActive: roleForm.isActive,
      }
      const result = roleForm.mode === 'create'
        ? await createRole(token, payload)
        : await updateRole(token, roleForm.code.trim(), payload)
      setRoles((current) => upsertByCode(current, mergeRolePermissions(result, current)))
      toast.success(roleForm.mode === 'create' ? 'Đã tạo role' : 'Đã cập nhật role')
      finishRoleDialog()
    } catch (err) {
      setRoleDialogError(err instanceof Error ? err.message : 'Không lưu được role')
    } finally {
      setSavingRole(false)
    }
  }

  async function updateSelectedRoleActive() {
    if (!token || !selectedRole || selectedRole.isSystem) return
    setUpdatingRoleState(true)
    setWorkspaceError('')
    try {
      const result = await updateRole(token, selectedRole.code, {
        code: selectedRole.code, name: selectedRole.name, description: selectedRole.description, isActive: !selectedRole.isActive,
      })
      setRoles((current) => upsertByCode(current, mergeRolePermissions(result, current)))
      toast.success(result.isActive ? 'Đã kích hoạt role' : 'Đã khóa role')
    } catch (err) {
      setWorkspaceError(err instanceof Error ? err.message : 'Không cập nhật được trạng thái role')
    } finally {
      setUpdatingRoleState(false)
    }
  }

  async function handleRolePermissionsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !rolePermissionCode || !hasPermissionChanges) return
    setSavingPermissions(true)
    setWorkspaceError('')
    const nextCodes = normalizeCodes(selectedPermissionCodes)
    try {
      await replaceRolePermissions(token, rolePermissionCode, nextCodes)
      setRoles((current) => current.map((role) => role.code === rolePermissionCode ? { ...role, permissionCodes: nextCodes } : role))
      setSelectedPermissionCodes(nextCodes)
      toast.success('Đã cập nhật permission cho role')
    } catch (err) {
      setWorkspaceError(err instanceof Error ? err.message : 'Không cập nhật được permission cho role')
    } finally {
      setSavingPermissions(false)
    }
  }

  function handleConfirm(confirmed: boolean) {
    const action = confirmAction
    setConfirmAction(null)
    if (action === 'discard-role-switch') {
      const nextCode = pendingRoleCode
      setPendingRoleCode(null)
      if (confirmed && nextCode) switchRole(nextCode)
      return
    }
    if (action === 'lock-role' && confirmed) void updateSelectedRoleActive()
  }

  return <>
    <div className="page-title rbac-page-title">
      <div><h1>Vai trò & quyền</h1><p>Quản lý vai trò hệ thống và phạm vi quyền mặc định của từng vai trò.</p></div>
      <button className="btn ghost rbac-refresh" type="button" onClick={() => void loadCatalogs()} disabled={isLoading}>
        <RefreshCw aria-hidden="true" />{isLoading ? 'Đang tải...' : 'Tải lại'}
      </button>
    </div>

    <Feedback error={catalogError} />

    <section className="rbac-role-context" aria-labelledby="rbac-role-context-title">
      <div className="rbac-role-context-selector">
        <span id="rbac-role-context-title" className="rbac-section-label">Vai trò đang chọn</span>
        <PopupSelect value={rolePermissionCode} options={roleOptions} onChange={handleRolePermissionChange} ariaLabel="Chọn vai trò để quản lý quyền" disabled={!roles.length || isLoading} className="rbac-role-select" />
      </div>
      <button className="btn primary rbac-create-role" type="button" onClick={() => openRoleDialog('create')}><Plus aria-hidden="true" />Tạo role</button>
      {selectedRole ? <div className="rbac-role-details">
        <div className="rbac-role-identity"><strong>{selectedRole.code} - {selectedRole.name}</strong>{selectedRole.description ? <p>{selectedRole.description}</p> : null}</div>
        <div className="rbac-role-meta"><span className={`rbac-role-status ${selectedRole.isActive ? 'is-active' : 'is-inactive'}`}>{selectedRole.isActive ? 'Đang hoạt động' : 'Đã khóa'}</span><span className="rbac-role-kind" title={selectedRole.isSystem ? 'Role hệ thống không được khóa hoặc kích hoạt thủ công' : undefined}>{selectedRole.isSystem ? 'Vai trò hệ thống' : 'Vai trò tùy chỉnh'}</span><strong>{selectedRole.permissionCodes?.length ?? 0} quyền</strong></div>
        <div className="rbac-role-actions">
          <button className="btn ghost table-btn" type="button" onClick={() => openRoleDialog('update')}>Sửa role</button>
          {selectedRole.isSystem ? null : selectedRole.isActive ? <button className="btn ghost table-btn rbac-lock-action" type="button" disabled={isUpdatingRoleState} onClick={() => setConfirmAction('lock-role')}><Power aria-hidden="true" />Khóa role</button> : <button className="btn ghost table-btn" type="button" disabled={isUpdatingRoleState} onClick={() => void updateSelectedRoleActive()}><Power aria-hidden="true" />Kích hoạt</button>}
        </div>
      </div> : null}
    </section>

    <section className="rbac-workspace" aria-labelledby="rbac-workspace-title">
      <header className="rbac-workspace-head"><div><span className="rbac-section-label">Phạm vi quyền</span><h2 id="rbac-workspace-title">Permission workspace</h2><p>Mở từng module để xem và điều chỉnh quyền đang áp dụng.</p></div>{selectedRole ? <span className="rbac-clean-summary">{hasPermissionChanges ? `${permissionChangeCount} thay đổi chưa lưu` : `${selectedRolePermissionCodes.length} quyền được gán`}</span> : null}</header>
      <Feedback error={workspaceError} />
      <form onSubmit={handleRolePermissionsSubmit}>
        {permissionGroups.length ? <PermissionModuleTable groups={permissionGroups} selectedPermissionSet={selectedPermissionSet} onTogglePermission={togglePermission} onSetGroup={setGroupPermissions} /> : <EmptyState title="Chưa có permission" description="Permission được định nghĩa ở backend và seed dữ liệu hệ thống." />}
        {hasPermissionChanges ? <div className="rbac-dirty-bar" role="status" aria-live="polite"><strong>{permissionChangeCount} thay đổi chưa lưu</strong><div><button className="btn ghost" type="button" onClick={restoreSelectedRolePermissions} disabled={isSavingPermissions}><RotateCcw aria-hidden="true" />Hoàn tác</button><button className="btn primary" type="submit" disabled={!rolePermissionCode || isSavingPermissions}><Save aria-hidden="true" />{isSavingPermissions ? 'Đang lưu...' : 'Lưu thay đổi'}</button></div></div> : null}
      </form>
    </section>

    {isRoleDialogOpen ? <dialog ref={roleDialogRef} className="rbac-role-dialog" aria-labelledby="role-dialog-title" aria-describedby="role-dialog-description" onCancel={(event) => { event.preventDefault(); closeRoleDialog() }} onClick={(event) => { if (event.target === event.currentTarget) closeRoleDialog() }}>
      <form className="rbac-role-dialog-shell" onSubmit={handleRoleSubmit}>
        <OverlayPortalHost />
        <header><div><h2 id="role-dialog-title">{roleForm.mode === 'create' ? 'Tạo vai trò' : 'Sửa vai trò'}</h2><p id="role-dialog-description">Thiết lập thông tin và trạng thái hoạt động của vai trò.</p></div><button className="btn ghost rbac-dialog-close" type="button" aria-label="Đóng cửa sổ role" onClick={closeRoleDialog} disabled={isSavingRole}><X aria-hidden="true" /></button></header>
        <div className="rbac-role-dialog-body"><Feedback error={roleDialogError} /><label className="field"><span>Role code</span><input className="input" data-role-dialog-initial value={roleForm.code} onChange={(event) => setRoleForm((form) => ({ ...form, code: event.target.value.toUpperCase() }))} disabled={roleForm.mode === 'update' || isSavingRole} required /></label><label className="field"><span>Tên role</span><input className="input" value={roleForm.name} onChange={(event) => setRoleForm((form) => ({ ...form, name: event.target.value }))} disabled={isSavingRole} required /></label><label className="field"><span>Mô tả</span><textarea className="textarea" value={roleForm.description} onChange={(event) => setRoleForm((form) => ({ ...form, description: event.target.value }))} disabled={isSavingRole} /></label><label className="check-row"><input type="checkbox" checked={roleForm.isActive} disabled={editingSystemRole || isSavingRole} onChange={(event) => setRoleForm((form) => ({ ...form, isActive: event.target.checked }))} />Đang hoạt động</label>{editingSystemRole ? <p className="rbac-field-note">Trạng thái role hệ thống được bảo vệ.</p> : null}</div>
        <footer><button className="btn ghost" type="button" onClick={closeRoleDialog} disabled={isSavingRole}>Hủy</button><button className="btn primary" type="submit" disabled={isSavingRole}>{roleForm.mode === 'create' ? <Plus aria-hidden="true" /> : <Save aria-hidden="true" />}{isSavingRole ? 'Đang lưu...' : roleForm.mode === 'create' ? 'Tạo vai trò' : 'Lưu thay đổi'}</button></footer>
      </form>
    </dialog> : null}

    {confirmAction === 'discard-role-switch' && selectedRole ? <ConfirmDialog title="Bỏ thay đổi chưa lưu?" description={`Các thay đổi quyền của ${selectedRole.code} chưa được lưu.`} cancelLabel="Tiếp tục chỉnh sửa" confirmLabel="Bỏ thay đổi" onClose={handleConfirm} /> : null}
    {confirmAction === 'lock-role' && selectedRole ? <ConfirmDialog title="Khóa vai trò?" description={`Vai trò “${selectedRole.code}” sẽ bị vô hiệu hóa. Người dùng đang sở hữu vai trò này có thể không đăng nhập được.`} cancelLabel="Hủy" confirmLabel="Khóa vai trò" destructive onClose={handleConfirm} /> : null}
  </>
}

function PermissionModuleTable({ groups, selectedPermissionSet, onTogglePermission, onSetGroup }: { groups: PermissionGroup[]; selectedPermissionSet: Set<string>; onTogglePermission: (permissionCode: string) => void; onSetGroup: (group: PermissionGroup, checked: boolean) => void }) {
  const [expandedModule, setExpandedModule] = useState<string | null>(null)
  return <div className="rbac-matrix-wrap"><table className="rbac-matrix"><thead><tr><th>Module</th><th>Quyền</th><th>Phạm vi</th><th><span className="sr-only">Thao tác</span></th></tr></thead><tbody>{groups.map((group) => {
    const selectedCount = group.permissions.filter((permission) => selectedPermissionSet.has(permission.code)).length
    const allSelected = selectedCount === group.permissions.length
    const isExpanded = expandedModule === group.module
    return <Fragment key={group.module}><tr className={isExpanded ? 'rbac-module-row is-expanded' : 'rbac-module-row'}><td><strong>{group.module}</strong></td><td><span>{selectedCount} / {group.permissions.length}</span></td><td>{describeModule(group)}</td><td><button className="rbac-module-toggle" type="button" onClick={() => setExpandedModule(isExpanded ? null : group.module)} aria-expanded={isExpanded} aria-label={`${isExpanded ? 'Thu gọn' : 'Mở'} quyền module ${group.module}`}><ChevronRight aria-hidden="true" /></button></td></tr>{isExpanded ? <tr className="rbac-module-editor-row"><td colSpan={4}><section className="rbac-module-editor" aria-label={`Quyền của module ${group.module}`}><header><div><strong>{group.module}</strong><span>{selectedCount} / {group.permissions.length} quyền</span></div><div><button className="rbac-text-action" type="button" onClick={() => onSetGroup(group, true)} disabled={allSelected}>Chọn tất cả</button><button className="rbac-text-action" type="button" onClick={() => onSetGroup(group, false)} disabled={!selectedCount}>Bỏ chọn tất cả</button></div></header><div className="rbac-permission-list">{group.permissions.map((permission) => <label className="rbac-permission-row" key={permission.code}><input type="checkbox" checked={selectedPermissionSet.has(permission.code)} onChange={() => onTogglePermission(permission.code)} /><span><strong>{permission.name}</strong><small>{permission.code}</small>{permission.description ? <em>{permission.description}</em> : null}</span></label>)}</div></section></td></tr> : null}</Fragment>
  })}</tbody></table></div>
}

function groupPermissions(permissions: Permission[]): PermissionGroup[] { const groups = new Map<string, Permission[]>(); for (const permission of permissions) { const module = (permission.module || permission.code.split('_')[0] || 'SYSTEM').toUpperCase(); groups.set(module, [...(groups.get(module) ?? []), permission]) } return Array.from(groups.entries()).sort(([left], [right]) => left.localeCompare(right)).map(([module, items]) => ({ module, permissions: items.sort((left, right) => left.code.localeCompare(right.code)) })) }
function describeModule(group: PermissionGroup) { return group.permissions.slice(0, 2).map((permission) => permission.name).join(', ') }
function normalizeCodes(codes: Iterable<string>) { return Array.from(new Set(codes)).sort() }
function sameCodeSet(left: string[], right: string[]) { const normalizedLeft = normalizeCodes(left); const normalizedRight = normalizeCodes(right); return normalizedLeft.length === normalizedRight.length && normalizedLeft.every((code, index) => code === normalizedRight[index]) }
function differenceCount(left: string[], right: string[]) { const leftSet = new Set(left); const rightSet = new Set(right); return [...leftSet].filter((code) => !rightSet.has(code)).length + [...rightSet].filter((code) => !leftSet.has(code)).length }
function mergeRolePermissions(role: Role, currentRoles: Role[]): Role { return { ...role, permissionCodes: role.permissionCodes ?? currentRoles.find((current) => current.code === role.code)?.permissionCodes ?? [] } }
function upsertByCode<T extends { code: string }>(items: T[], item: T) { return items.some((current) => current.code === item.code) ? items.map((current) => current.code === item.code ? item : current) : [...items, item] }
