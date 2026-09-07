export const accessPolicies = {
  files: ['FILE_UPLOAD'],
  projects: ['PROJECT_READ'],
  tasks: ['TASK_READ'],
  researchFields: ['RESEARCH_FIELD_MANAGE'],
  members: ['MEMBER_MANAGE'],
  accounts: ['USER_MANAGE', 'ROLE_MANAGE', 'PERMISSION_MANAGE'],
  rbac: ['ROLE_MANAGE', 'PERMISSION_MANAGE'],
  content: ['PROJECT_MANAGE'],
} as const

export function hasAllPermissions(
  effectivePermissions: readonly string[] | undefined,
  requiredPermissions: readonly string[],
) {
  return requiredPermissions.every((permission) => effectivePermissions?.includes(permission))
}
