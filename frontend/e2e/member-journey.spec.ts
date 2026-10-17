import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { authToken, hasMemberCredentials, loginAsMember } from './support/auth'
import { env, missing } from './support/env'
import { apiPathEndsWith, apiPathMatches, expectOk, isExternalDependencyUnavailable } from './support/http'

test.describe('final MEMBER dependency journey', () => {
  test.skip(!hasMemberCredentials(), 'Set E2E_MEMBER_EMAIL and E2E_MEMBER_PASSWORD for MEMBER tests.')

  test.beforeEach(async ({ page }) => {
    await loginAsMember(page)
  })

  test('sees project membership, assigned task, evaluation, reviewed post and notification', async ({ page, request }) => {
    const missingFixtures = missing({
      E2E_MEMBER_PROJECT_ID: env.journey.projectId,
      E2E_MEMBER_PROJECT_NAME: env.journey.projectName,
      E2E_MEMBER_TASK_TITLE: env.journey.taskTitle,
      E2E_MEMBER_EVALUATION_MARKER: env.journey.evaluationMarker,
      E2E_MEMBER_REVIEWED_POST_TITLE: env.journey.reviewedPostTitle,
      E2E_MEMBER_NOTIFICATION_MARKER: env.journey.notificationMarker,
    })
    test.skip(missingFixtures.length > 0, `Missing seeded journey fixtures: ${missingFixtures.join(', ')}`)

    await test.step('C: membership is visible in the assigned project', async () => {
      await assertProjectMembership(
        page,
        env.journey.projectId as number,
        env.journey.projectName as string,
        env.member.email as string,
      )
    })

    await test.step('D: assigned task is visible to the member', async () => {
      await openAssignedTask(page, env.journey.projectId as number, env.journey.taskTitle as string)
      await expect(page.getByRole('heading', { name: new RegExp(escapeRegex(env.journey.taskTitle as string), 'i') })).toBeVisible()
    })

    await test.step('D: evaluation is visible in My evaluations', async () => {
      const responsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/me/evaluations'))
      await page.goto('/my-evaluations')
      const response = await responsePromise
      await expectOk(response, 'GET /me/evaluations')
      expect(JSON.stringify(await response.json())).toContain(env.journey.evaluationMarker as string)
      await expect(page.getByText(env.journey.evaluationMarker as string, { exact: false }).first()).toBeVisible()
    })

    await test.step('E: reviewed post and its final status are visible to the author', async () => {
      const responsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/posts/mine'))
      await page.goto('/my-posts')
      const response = await responsePromise
      await expectOk(response, 'GET /posts/mine')
      const posts = await response.json() as Array<{ title: string; status: string }>
      expect(posts.some((post) => post.title === env.journey.reviewedPostTitle && post.status === 'PUBLISHED')).toBeTruthy()

      const postCard = page.getByRole('article').filter({ hasText: env.journey.reviewedPostTitle as string })
      await expect(page.getByRole('article').first()).toBeVisible()
      for (let remaining = posts.length; remaining > 0 && await postCard.count() === 0; remaining--) {
        await page.getByRole('navigation', { name: 'Phân trang' }).getByRole('button', { name: 'Trang sau' }).click()
      }
      await expect(postCard).toBeVisible()
      await expect(postCard).toContainText(env.journey.reviewedPostStatus)
    })

    await test.step('E: resulting notification is visible in the member bell', async () => {
      const token = await authToken(page)
      await assertNotificationApi(request, token, env.journey.notificationMarker as string)

      await page.getByRole('button', { name: /^Thông báo(?:,|$)/ }).click()
      const dialog = page.getByRole('dialog', { name: 'Thông báo' })
      await expect(dialog).toBeVisible()
      await expect(dialog.getByText(env.journey.notificationMarker as string, { exact: false }).first()).toBeVisible()
    })
  })

  test('submits an assigned task result', async ({ page }) => {
    test.skip(
      !env.journey.mutate,
      'Set E2E_RUN_MUTATING_JOURNEY=true only when the task fixture is reset before this run.',
    )
    const missingFixtures = missing({
      E2E_MEMBER_PROJECT_ID: env.journey.projectId,
      E2E_MEMBER_PROJECT_NAME: env.journey.projectName,
      E2E_MEMBER_TASK_ID: env.journey.taskId,
      E2E_MEMBER_TASK_TITLE: env.journey.taskTitle,
    })
    test.skip(missingFixtures.length > 0, `Missing mutable journey fixtures: ${missingFixtures.join(', ')}`)

    const taskId = env.journey.taskId as number
    await openAssignedTask(page, env.journey.projectId as number, env.journey.taskTitle as string)
    await page.getByRole('button', { name: 'Nộp kết quả', exact: true }).first().click()

    const modal = page.getByRole('dialog', { name: 'Nộp kết quả' })
    await modal.locator('input[type="file"]').setInputFiles({
      name: `task-${taskId}-result.txt`,
      mimeType: 'text/plain',
      buffer: Buffer.from(`Playwright result for task ${taskId}\n`, 'utf8'),
    })
    await modal.getByLabel('Giải thích hỗ trợ').fill('Submitted by the MEMBER browser E2E journey.')

    const submitResponsePromise = page.waitForResponse((response) =>
      apiPathEndsWith(response, `/tasks/${taskId}/submit/upload`, 'POST'))
    await modal.getByRole('button', { name: 'Nộp kết quả' }).click()
    const submitResponse = await submitResponsePromise
    test.skip(
      isExternalDependencyUnavailable(submitResponse),
      `File storage is unavailable (HTTP ${submitResponse.status()}); task submission was not verified.`,
    )
    await expectOk(submitResponse, `POST /tasks/${taskId}/submit/upload`)
    expect((await submitResponse.json() as { status: string }).status).toBe('REVIEW')
    await expect(page.getByText('Đã nộp kết quả')).toBeVisible()
  })

  test('creates a project post and sends it for review', async ({ page }) => {
    const missingFixtures = missing({ E2E_MEMBER_PROJECT_ID: env.journey.projectId })
    test.skip(missingFixtures.length > 0, `Missing project fixture: ${missingFixtures.join(', ')}`)
    const projectId = env.journey.projectId as number
    const title = `MEMBER E2E review ${Date.now()}`

    await page.goto('/posts/new')
    await expect(page.getByRole('heading', { level: 1, name: 'Tạo bài viết' })).toBeVisible()
    await page.getByRole('textbox', { name: 'Tiêu đề' }).fill(title)
    await page.getByLabel('Tóm tắt').fill('Cross-domain MEMBER journey verification.')
    await page.locator('#post-content[contenteditable="true"]').fill('This project post is ready for reviewer validation.')
    await page.getByLabel('Phạm vi hiển thị').selectOption('PROJECT')
    await page.locator('#post-project').selectOption(String(projectId))
    const category = await page.locator('#post-category option:not([value=""])').first().getAttribute('value')
    expect(category, 'Submitting a post requires an existing content category.').toBeTruthy()
    await page.locator('#post-category').selectOption(category as string)

    const createResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/posts', 'POST'))
    await page.getByRole('button', { name: 'Lưu nháp' }).click()
    const createResponse = await createResponsePromise
    await expectOk(createResponse, 'POST /posts')
    const created = await createResponse.json() as { id: number; slug: string; status: string }
    expect(created.status).toBe('DRAFT')
    await expect(page).toHaveURL(new RegExp(`/posts/${escapeRegex(created.slug)}$`))
    await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible()

    const submitResponsePromise = page.waitForResponse((response) =>
      apiPathEndsWith(response, `/posts/${created.id}/submit`, 'POST'))
    await page.getByRole('button', { name: 'Gửi duyệt' }).click()
    const submitResponse = await submitResponsePromise
    await expectOk(submitResponse, `POST /posts/${created.id}/submit`)
    expect((await submitResponse.json() as { status: string }).status).toBe('PENDING_REVIEW')
    await expect(page.locator('.post-page-status-visibility')).toContainText('Chờ duyệt')
  })
})

async function assertProjectMembership(page: Page, projectId: number, projectName: string, memberEmail: string) {
  const membersResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return apiPathMatches(response, new RegExp(`/projects/${projectId}/members$`))
      && url.searchParams.get('status') === 'ACTIVE'
  })

  await page.goto('/admin/projects')
  await page.getByLabel('Tìm dự án theo tên hoặc mã').fill(projectName)
  const openButton = page.locator('.project-management-row-actions').getByRole('button', {
    name: `Chi tiết dự án ${projectName}`,
  })
  await expect(openButton).toBeVisible()
  await openButton.click()

  const membersResponse = await membersResponsePromise
  await expectOk(membersResponse, `GET /projects/${projectId}/members`)
  expect(JSON.stringify(await membersResponse.json())).toContain(memberEmail)

  await page.getByRole('tab', { name: 'Thành viên' }).click()
  const memberPanel = page.getByRole('tabpanel', { name: 'Thành viên' })
  await expect(memberPanel.getByText(memberEmail, { exact: true })).toBeVisible()
}

async function openAssignedTask(page: Page, projectId: number, taskTitle: string) {
  const initialTasksPromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === 'GET'
      && /\/projects\/\d+\/tasks$/.test(url.pathname)
  })

  await page.goto('/admin/tasks')
  await expect(page.getByRole('heading', { level: 1, name: /Nhiệm vụ.*Đánh giá/i })).toBeVisible()
  const initialTasks = await initialTasksPromise
  await expectOk(initialTasks, 'initial MEMBER project tasks')
  if (!new URL(initialTasks.url()).pathname.endsWith(`/projects/${projectId}/tasks`)) {
    const tasksResponsePromise = page.waitForResponse((response) =>
      apiPathEndsWith(response, `/projects/${projectId}/tasks`))
    await page.getByRole('button', { name: 'Chọn dự án' }).click()
    await page.getByRole('listbox', { name: 'Chọn dự án' })
      .getByRole('option', { name: new RegExp(escapeRegex(env.journey.projectName as string), 'i') }).click()
    await expectOk(await tasksResponsePromise, `GET /projects/${projectId}/tasks`)
  }
  await page.getByRole('button', { name: 'Bảng' }).click()
  const row = page.getByRole('row').filter({ hasText: taskTitle })
  await expect(row).toBeVisible()
  const detailResponsePromise = page.waitForResponse((response) =>
    apiPathMatches(response, /\/tasks\/\d+$/, 'GET'))
  await row.getByRole('button', { name: 'Chi tiết' }).click()
  await expectOk(await detailResponsePromise, 'GET /tasks/{id}')
  await expect(page.getByText(taskTitle, { exact: true }).last()).toBeVisible()
}

async function assertNotificationApi(request: APIRequestContext, token: string, marker: string) {
  const response = await request.get(`${env.apiBaseURL}/me/notifications`, {
    headers: { authorization: `Bearer ${token}` },
  })
  expect(response.ok(), `GET /me/notifications failed with HTTP ${response.status()}.`).toBeTruthy()
  expect(JSON.stringify(await response.json())).toContain(marker)
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
