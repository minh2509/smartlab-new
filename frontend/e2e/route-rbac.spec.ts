import { expect, test, type Page } from '@playwright/test'

type TestProfile = {
  userId: string
  name: string
  email: string
  roles: string[]
  permissions: string[]
}

const sharedPermissions = [
  'PROFILE_READ',
  'PROFILE_UPDATE',
  'DASHBOARD_READ',
  'PROJECT_READ',
  'TASK_READ',
  'FILE_UPLOAD',
  'FILE_DELETE',
  'notifications.read_own',
  'notifications.mark_read_own',
]

test.describe('frontend route RBAC matrix', () => {
  test('MEMBER sees member workspace only and direct ADMIN URLs stay forbidden', async ({ page }) => {
    await installProfile(page, profile('MEMBER', [...sharedPermissions, 'posts.submit']))

    await page.goto('/admin/accounts')
    await expectForbidden(page, '/admin/accounts')
    await expect(page.getByRole('link', { name: 'Tài khoản của tôi' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Tệp của tôi' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Dự án' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Sự kiện' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Nhiệm vụ & Đánh giá' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Quản trị tài khoản' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Vai trò & quyền' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Hồ sơ thành viên' })).toHaveCount(0)

    for (const path of ['/admin/rbac', '/admin/members', '/admin/research-fields', '/admin/articles']) {
      await page.goto(path)
      await expectForbidden(page, path)
    }
  })

  test('LEADER receives work permissions but cannot cross ADMIN boundaries', async ({ page }) => {
    await installProfile(page, profile('LEADER', [
      ...sharedPermissions,
      'PROJECT_MANAGE',
      'TASK_MANAGE',
      'posts.submit',
    ]))

    await page.goto('/admin/rbac')
    await expectForbidden(page, '/admin/rbac')
    await expect(page.getByText('Leader Workspace')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Dự án' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Sự kiện' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Quản trị tài khoản' })).toHaveCount(0)
  })

  test('ADMIN with the complete permission set reaches account administration', async ({ page }) => {
    await installProfile(page, profile('ADMIN', [
      ...sharedPermissions,
      'USER_MANAGE',
      'ROLE_MANAGE',
      'PERMISSION_MANAGE',
      'PROJECT_MANAGE',
      'TASK_MANAGE',
      'MEMBER_MANAGE',
      'RESEARCH_FIELD_MANAGE',
    ]))

    await page.goto('/admin/accounts')
    await expect(page).toHaveURL(/\/admin\/accounts$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Quản trị tài khoản' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Quản trị tài khoản' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Vai trò & quyền' })).toBeVisible()
  })

  test('permission override removal hides and blocks the matching route', async ({ page }) => {
    await installProfile(page, profile('MEMBER', sharedPermissions.filter((item) => item !== 'PROJECT_READ')))

    await page.goto('/admin/projects')
    await expectForbidden(page, '/admin/projects')
    // LAB/PUBLIC events remain readable; project event access is scoped by the API.
    await expect(page.getByRole('link', { name: 'Sự kiện' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Dự án' })).toHaveCount(0)
  })

  test('denied PROFILE_READ shows a stable forbidden page without redirecting in a loop', async ({ page }, testInfo) => {
    await installProfile(page, profile('MEMBER', sharedPermissions.filter((item) => item !== 'PROFILE_READ')))

    await page.goto('/profile')
    await expectForbidden(page, '/profile')
    await expect(page.getByRole('link', { name: 'Tài khoản của tôi' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Về trang chủ' })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('forbidden-desktop.png'), fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('heading', { name: 'Bạn không có quyền mở trang này' })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('forbidden-mobile.png'), fullPage: true })
  })

  test('anonymous users must sign in before opening their posts', async ({ page }) => {
    await page.goto('/my-posts')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible()
  })

  for (const canMutate of [false, true]) {
    test(`notification mutation controls follow effective authority: ${canMutate}`, async ({ page }) => {
      await installProfile(page, profile('MEMBER', sharedPermissions.filter((item) =>
        canMutate || item !== 'notifications.mark_read_own')))
      await page.route('**/api/v1.0/me/notifications', (route) => route.fulfill({ json: [{
        id: 17, actorUserId: null, actorName: null, type: 'TASK_ASSIGNED',
        message: 'Thông báo kiểm tra quyền', relatedType: null, relatedId: null,
        targetUrl: null, isRead: false, createdAt: '2026-09-22T01:00:00Z',
      }] }))
      await page.goto('/su-kien')
      await page.getByRole('button', { name: 'Thông báo, 1 chưa đọc', exact: true }).click()
      await expect(page.getByText('Thông báo kiểm tra quyền', { exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: 'Xóa thông báo: Thông báo kiểm tra quyền' }))
        .toHaveCount(canMutate ? 1 : 0)
      await expect(page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' }))
        .toHaveCount(canMutate ? 1 : 0)
    })
  }
})

async function installProfile(page: Page, value: TestProfile) {
  await page.addInitScript(() => {
    localStorage.setItem('smartlab.token', 'e2e-route-rbac-token')
    localStorage.setItem('smartlab.sessionId', 'e2e-route-rbac-session')
  })
  await page.route('**/api/v1.0/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/profile')) {
      await route.fulfill({ json: { ...value, isActive: true, isAccountVerified: true, permissionOverrides: [] } })
      return
    }
    if (path.endsWith('/admin/roles') || path.endsWith('/admin/permissions')) {
      await route.fulfill({ json: [] })
      return
    }
    if (path.endsWith('/admin/accounts')) {
      await route.fulfill({ json: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true } })
      return
    }
    await route.fulfill({ json: [] })
  })
}

function profile(role: 'ADMIN' | 'LEADER' | 'MEMBER', permissions: string[]): TestProfile {
  return {
    userId: `${role.toLowerCase()}-user`,
    name: `${role} Test`,
    email: `${role.toLowerCase()}@smartlab.test`,
    roles: [role],
    permissions,
  }
}

async function expectForbidden(page: Page, path: string) {
  await expect(page).toHaveURL(new RegExp(`${path.replaceAll('/', '\\/')}$`))
  await expect(page.getByRole('heading', { level: 1, name: 'Bạn không có quyền mở trang này' })).toBeVisible()
}
