import type { FullConfig } from '@playwright/test'

export default async function globalSetup(_config: FullConfig) {
  const resetUrl = process.env.E2E_FIXTURE_RESET_URL?.trim()
  if (!resetUrl) return

  const fixtureToken = process.env.E2E_FIXTURE_TOKEN?.trim()
  const response = await fetch(resetUrl, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(fixtureToken ? { authorization: `Bearer ${fixtureToken}` } : {}),
    },
    body: JSON.stringify({ suite: 'smartlab-member-e2e' }),
  })

  if (!response.ok) {
    const detail = (await response.text()).slice(0, 500)
    throw new Error(`E2E fixture reset failed (${response.status}): ${detail}`)
  }
}
