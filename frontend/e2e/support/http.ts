import { expect, type Response } from '@playwright/test'

export function apiPathEndsWith(response: Response, suffix: string, method = 'GET') {
  const url = new URL(response.url())
  return response.request().method() === method && url.pathname.endsWith(suffix)
}

export function apiPathMatches(response: Response, pattern: RegExp, method = 'GET') {
  const url = new URL(response.url())
  return response.request().method() === method && pattern.test(url.pathname)
}

export function isExternalDependencyUnavailable(response: Response) {
  return [502, 503, 504].includes(response.status())
}

export async function expectOk(response: Response, operation: string) {
  const body = response.ok() ? '' : (await response.text()).slice(0, 500)
  expect(response.ok(), `${operation} failed with HTTP ${response.status()}${body ? `: ${body}` : ''}`).toBeTruthy()
}
