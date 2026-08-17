import { expect, test, type Page, type Response } from '@playwright/test'
import {
  authToken,
  hasAdminCredentials,
  hasMemberCredentials,
  loginAsAdmin,
  loginAsMember,
} from './support/auth'
import { env } from './support/env'

type ProjectFixture = {
  id: number
  name: string
}

test.describe('final ADMIN dependency journey', () => {
  test.skip(
    !hasAdminCredentials(),
    'Set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD for ADMIN browser tests.',
  )

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('smokes account, RBAC, member, project, document, task, evaluation, post, event and profile surfaces', async ({ page }) => {
    await test.step('ADMIN account management is reachable and its APIs succeed', async () => {
      const rolesResponsePromise = waitForApiGet(page, /\/admin\/roles$/)
      const permissionsResponsePromise = waitForApiGet(page, /\/admin\/permissions$/)
      const accountsResponsePromise = waitForApiGet(page, /\/admin\/accounts$/)

      await page.goto('/admin/accounts')

      await expectOk(await rolesResponsePromise, 'GET /admin/roles')
      await expectOk(await permissionsResponsePromise, 'GET /admin/permissions')
      await expectOk(await accountsResponsePromise, 'GET /admin/accounts')

      await expect(page.getByText('Admin Workspace', { exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { level: 1, name: 'Quản trị tài khoản' })).toBeVisible()
    })

    await test.step('ADMIN RBAC catalogue is reachable and its APIs succeed', async () => {
      const rolesResponsePromise = waitForApiGet(page, /\/admin\/roles$/)
      const permissionsResponsePromise = waitForApiGet(page, /\/admin\/permissions$/)

      await page.goto('/admin/rbac')

      await expectOk(await rolesResponsePromise, 'GET /admin/roles')
      await expectOk(await permissionsResponsePromise, 'GET /admin/permissions')
      await expect(page.getByRole('heading', { level: 1, name: 'Vai trò & quyền' })).toBeVisible()
      await expect(page.getByRole('heading', { level: 2, name: 'Gán quyền cho vai trò' })).toBeVisible()
    })

    await test.step('ADMIN member profile catalogue is reachable', async () => {
      const membersResponsePromise = waitForApiGet(page, /\/admin\/members$/)

      await page.goto('/admin/members')

      await expectOk(await membersResponsePromise, 'GET /admin/members')
      await expect(page.getByRole('heading', { level: 1, name: 'Hồ sơ thành viên' })).toBeVisible()
    })

    let project: ProjectFixture

    await test.step('ADMIN project catalogue is reachable', async () => {
      const projectsResponsePromise = waitForApiGet(page, /\/projects$/)

      await page.goto('/admin/projects')

      const projectsResponse = await projectsResponsePromise
      await expectOk(projectsResponse, 'GET /projects')

      const projects = await projectsResponse.json() as ProjectFixture[]
      expect(projects.length, 'A5 requires at least one readable project fixture.').toBeGreaterThan(0)

      project = projects[0]

      await expect(page.getByRole('heading', { level: 1, name: 'Quản lý dự án' })).toBeVisible()
    })

    await test.step('ADMIN project document tab loads document data', async () => {
      const projectsResponsePromise = waitForApiGet(page, /\/projects$/)
      const documentsResponsePromise = waitForApiGet(
        page,
        new RegExp(`/projects/${project.id}/documents$`),
      )

      await page.goto(`/admin/projects?projectId=${project.id}&tab=documents`)

      await expectOk(await projectsResponsePromise, 'GET /projects')
      await expectOk(
        await documentsResponsePromise,
        `GET /projects/${project.id}/documents`,
      )

      await expect(page.getByRole('heading', { level: 2, name: 'Tài liệu dự án' })).toBeVisible()
    })

    await test.step('ADMIN task surface loads project tasks', async () => {
      const projectsResponsePromise = waitForApiGet(page, /\/projects$/)
      const tasksResponsePromise = waitForApiGet(page, /\/projects\/\d+\/tasks$/)

      await page.goto('/admin/tasks')

      await expectOk(await projectsResponsePromise, 'GET /projects')
      await expectOk(await tasksResponsePromise, 'GET /projects/{id}/tasks')

      await expect(
        page.getByRole('heading', { level: 1, name: 'Quản lý Nhiệm vụ & Đánh giá (D4)' }),
      ).toBeVisible()
      await expect(page.getByRole('button', { name: 'Nhiệm vụ', exact: true })).toBeVisible()
    })

    await test.step('ADMIN evaluation surface loads criteria and active members', async () => {
      const criteriaResponsePromise = waitForApiGet(
        page,
        /\/projects\/\d+\/evaluation-criteria$/,
      )
      const membersResponsePromise = waitForApiGet(
        page,
        /\/projects\/\d+\/members$/,
      )

      await page.getByRole('button', { name: 'Đánh giá thành viên' }).click()

      await expectOk(
        await criteriaResponsePromise,
        'GET /projects/{id}/evaluation-criteria',
      )
      await expectOk(
        await membersResponsePromise,
        'GET /projects/{id}/members',
      )

      await expect(
        page.getByRole('heading', { level: 2, name: 'Đánh giá thành viên trong dự án' }),
      ).toBeVisible()
    })

    await test.step('ADMIN post review queue loads review data', async () => {
      const postsResponsePromise = waitForApiGet(page, /\/posts\/review-queue$/)

      await page.goto('/posts/review-queue')

      await expectOk(await postsResponsePromise, 'GET post review queue')
      await expect(
        page.getByRole('heading', { level: 1, name: 'Hàng chờ duyệt bài' }),
      ).toBeVisible()
    })

    await test.step('ADMIN event management loads event data', async () => {
      const projectsResponsePromise = waitForApiGet(page, /\/projects$/)
      const eventsResponsePromise = waitForApiGet(page, /\/events$/)

      await page.goto('/admin/events')

      await expectOk(await projectsResponsePromise, 'GET /projects')
      await expectOk(await eventsResponsePromise, 'GET /events')

      await expect(
        page.getByRole('heading', { level: 1, name: 'Quản lý sự kiện' }),
      ).toBeVisible()
    })

    await test.step('ADMIN own profile is reachable', async () => {
      const profileResponsePromise = waitForApiGet(page, /\/me\/profile$/)

      await page.goto('/profile')

      await expectOk(await profileResponsePromise, 'GET /me/profile')
      await expect(
        page.getByRole('heading', { level: 1, name: 'Hồ sơ của tôi' }),
      ).toBeVisible()
    })
  })
})

test.describe('ADMIN authorization boundaries', () => {
  test('anonymous direct URL to ADMIN account management is redirected to login', async ({ page }) => {
    await page.goto('/admin/accounts')

    await expect(page).toHaveURL(/\/login$/)
    await expect(
      page.getByRole('heading', { level: 1, name: 'Đăng nhập' }),
    ).toBeVisible()
  })

  test('MEMBER cannot open ADMIN-only account management or call its API', async ({ page, request }) => {
    test.skip(
      !hasMemberCredentials(),
      'Set E2E_MEMBER_EMAIL and E2E_MEMBER_PASSWORD to verify ADMIN forbidden boundaries.',
    )

    await loginAsMember(page)
    const token = await authToken(page)

    await page.goto('/admin/accounts')

    await expect(page).toHaveURL(/\/profile$/)
    await expect(
      page.getByRole('heading', { level: 1, name: 'Hồ sơ của tôi' }),
    ).toBeVisible()

    const response = await request.get(
      `${env.apiBaseURL}/admin/accounts?page=0&size=1`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    )

    expect(
      [401, 403],
      `MEMBER ADMIN-only API returned unexpected HTTP ${response.status()}.`,
    ).toContain(response.status())
  })
})

function waitForApiGet(page: Page, relativePathPattern: RegExp) {
  return page.waitForResponse((response) => {
    if (response.request().method() !== 'GET') return false

    const relativePath = apiRelativePath(response)
    return relativePath !== null && relativePathPattern.test(relativePath)
  })
}

function apiRelativePath(response: Response) {
  const requestUrl = new URL(response.url())
  const apiUrl = new URL(env.apiBaseURL)

  const apiPrefix = apiUrl.pathname.replace(/\/+$/, '')
  if (!requestUrl.pathname.startsWith(`${apiPrefix}/`)) return null

  return requestUrl.pathname.slice(apiPrefix.length)
}

async function expectOk(response: Response, label: string) {
  expect(
    response.ok(),
    `${label} failed with HTTP ${response.status()}.`,
  ).toBeTruthy()
}
