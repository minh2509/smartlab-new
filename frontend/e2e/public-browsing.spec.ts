import { expect, test, type Page, type Response } from '@playwright/test'
import { env } from './support/env'
import { expectOk } from './support/http'

test.describe('public browsing', () => {
  test('guest can browse the public information architecture without being forced to login', async ({ page }) => {
    const routes = [
      { path: '/', heading: /Nơi sinh viên làm/i },
      { path: '/gioi-thieu', heading: 'Về phòng Smart Lab' },
      { path: '/linh-vuc', heading: 'Lĩnh vực nghiên cứu' },
      { path: '/thanh-vien', heading: 'Thành viên' },
      { path: '/du-an', heading: 'Dự án' },
      { path: '/posts', heading: 'Bảng tin Smart Lab' },
      { path: '/su-kien', heading: 'Sự kiện' },
    ]

    for (const route of routes) {
      await test.step(route.path, async () => {
        await page.goto(route.path)
        await expect(page.getByRole('heading', { level: 1, name: route.heading })).toBeVisible()
        await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
      })
    }
  })

  test('member directory renders the public API fixture', async ({ page }) => {
    test.skip(!env.publicData.memberName, 'Set E2E_PUBLIC_MEMBER_NAME to assert the seeded public member.')
    await expectApiFixture(page, '/thanh-vien', '/members', env.publicData.memberName as string)
  })

  test('research fields render the public API fixture', async ({ page }) => {
    test.skip(
      !env.publicData.researchFieldName,
      'Set E2E_PUBLIC_RESEARCH_FIELD_NAME to assert the seeded public research field.',
    )
    await expectApiFixture(
      page,
      '/linh-vuc',
      '/research-fields',
      env.publicData.researchFieldName as string,
    )
  })

  test('project catalogue renders the public API fixture', async ({ page }) => {
    test.skip(!env.publicData.projectName, 'Set E2E_PUBLIC_PROJECT_NAME to assert the seeded public project.')
    await expectApiFixture(page, '/du-an', '/projects', env.publicData.projectName as string)
  })

  test('post feed renders the public API fixture', async ({ page }) => {
    test.skip(!env.publicData.postTitle, 'Set E2E_PUBLIC_POST_TITLE to assert the seeded public post.')
    await expectApiFixture(page, '/posts', '/posts', env.publicData.postTitle as string)
  })

  test('event listing renders the public API fixture', async ({ page }) => {
    test.skip(!env.publicData.eventTitle, 'Set E2E_PUBLIC_EVENT_TITLE to assert the seeded public event.')
    await expectApiFixture(page, '/su-kien', '/events/public', env.publicData.eventTitle as string)
  })
})

async function expectApiFixture(page: Page, route: string, apiSuffix: string, expectedText: string) {
  const responsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    const apiBase = new URL(env.apiBaseURL)
    return response.request().method() === 'GET'
      && url.origin === apiBase.origin
      && url.pathname.endsWith(apiSuffix)
  })

  await page.goto(route)
  const response = await responsePromise
  await expectOk(response, `GET ${apiSuffix}`)
  expect(response.request().headers().authorization, `${apiSuffix} must be callable anonymously.`).toBeUndefined()
  await expectResponseContains(response, expectedText)
  await expect(page.getByText(expectedText, { exact: false }).first()).toBeVisible()
}

async function expectResponseContains(response: Response, expectedText: string) {
  const body = await response.json() as unknown
  expect(JSON.stringify(body), `API response does not contain fixture text "${expectedText}".`).toContain(expectedText)
}
