import { expect, type Page } from '@playwright/test'
import { env } from './env'

export function hasMemberCredentials() {
  return Boolean(env.member.email && env.member.password)
}

export async function loginAsMember(page: Page) {
  if (!env.member.email || !env.member.password) {
    throw new Error('E2E_MEMBER_EMAIL and E2E_MEMBER_PASSWORD are required for MEMBER tests.')
  }

  await page.goto('/login')
  await page.getByLabel('Email').fill(env.member.email)
  await page.getByLabel('Mật khẩu').fill(env.member.password)

  const loginResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === 'POST' && url.pathname.endsWith('/login')
  })
  await page.getByRole('button', { name: /^Đăng nhập$/ }).click()
  const loginResponse = await loginResponsePromise

  expect(loginResponse.ok(), await responseMessage(loginResponse)).toBeTruthy()
  await expect(page).toHaveURL(/\/profile(?:\?.*)?$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Hồ sơ của tôi' })).toBeVisible()
}

export async function authToken(page: Page) {
  const token = await page.evaluate(() => localStorage.getItem('smartlab.token'))
  expect(token, 'Login completed without smartlab.token in localStorage.').toBeTruthy()
  return token as string
}

async function responseMessage(response: { status(): number; text(): Promise<string> }) {
  const body = (await response.text()).slice(0, 500)
  return `Login failed with HTTP ${response.status()}${body ? `: ${body}` : ''}`
}
