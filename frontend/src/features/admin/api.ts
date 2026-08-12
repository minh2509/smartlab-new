import { apiClient, toQuery } from '../../lib/apiClient'
import type { AccountResponse, InvitationResponse, PaginatedResponse, Permission, Role } from '../../shared/types/api'

export type ProvisionAccountPayload = {
  name: string
  email: string
  roleCodes?: string[]
}

export type RolePayload = {
  code: string
  name: string
  description?: string
  isActive?: boolean
}

export type PermissionPayload = {
  code: string
  name: string
  module?: string
  description?: string
  isActive?: boolean
}

export function listRoles(token: string) {
  return apiClient<Role[]>('/admin/roles', { token })
}

export function listAccounts(token: string, page = 0, size = 10) {
  return apiClient<PaginatedResponse<AccountResponse>>(`/admin/accounts${toQuery({ page, size })}`, { token })
}

export function createRole(token: string, payload: RolePayload) {
  return apiClient<Role>('/admin/roles', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateRole(token: string, code: string, payload: RolePayload) {
  return apiClient<Role>(`/admin/roles/${encodeURIComponent(code)}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(payload),
  })
}

export function replaceRolePermissions(token: string, code: string, permissionCodes: string[]) {
  return apiClient<Role>(`/admin/roles/${encodeURIComponent(code)}/permissions`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ permissionCodes }),
  })
}

export function listPermissions(token: string) {
  return apiClient<Permission[]>('/admin/permissions', { token })
}

export function createPermission(token: string, payload: PermissionPayload) {
  return apiClient<Permission>('/admin/permissions', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updatePermission(token: string, code: string, payload: PermissionPayload) {
  return apiClient<Permission>(`/admin/permissions/${encodeURIComponent(code)}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(payload),
  })
}

export function provisionAccount(token: string, payload: ProvisionAccountPayload) {
  return apiClient<InvitationResponse>('/admin/accounts', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function resendInvitation(token: string, email: string) {
  return apiClient<InvitationResponse>(`/admin/accounts/invitations/resend${toQuery({ email })}`, {
    method: 'POST',
    token,
  })
}

export function updateAccountRoles(token: string, userId: string, roleCodes: string[]) {
  return apiClient<AccountResponse>(`/admin/accounts/${encodeURIComponent(userId)}/roles`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ roleCodes }),
  })
}

export function setAccountActive(token: string, userId: string, active: boolean) {
  return apiClient<AccountResponse>(`/admin/accounts/${encodeURIComponent(userId)}/active${toQuery({ active })}`, {
    method: 'PATCH',
    token,
  })
}

export function setUserPermissionOverride(
  token: string,
  userId: string,
  permissionCode: string,
  effect: 'GRANT' | 'DENY',
) {
  return apiClient<AccountResponse>(
    `/admin/accounts/${encodeURIComponent(userId)}/permissions/${encodeURIComponent(permissionCode)}`,
    {
      method: 'PUT',
      token,
      body: JSON.stringify({ effect }),
    },
  )
}

export function removeUserPermissionOverride(token: string, userId: string, permissionCode: string) {
  return apiClient<AccountResponse>(
    `/admin/accounts/${encodeURIComponent(userId)}/permissions/${encodeURIComponent(permissionCode)}`,
    {
      method: 'DELETE',
      token,
    },
  )
}
