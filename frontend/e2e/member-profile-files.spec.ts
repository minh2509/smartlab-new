import { expect, test, type APIRequestContext } from '@playwright/test'
import type { Readable } from 'node:stream'
import { authToken, hasMemberCredentials, loginAsMember } from './support/auth'
import { env } from './support/env'
import { apiPathEndsWith, expectOk, isExternalDependencyUnavailable } from './support/http'

type MemberProfileSnapshot = {
  phone?: string
  publicEmail?: string
  bio?: string
  avatar?: { id: number }
  researchFields: Array<{ id: number; name: string }>
}

test.describe('MEMBER profile and file access', () => {
  test.skip(!hasMemberCredentials(), 'Set E2E_MEMBER_EMAIL and E2E_MEMBER_PASSWORD for MEMBER tests.')

  test.beforeEach(async ({ page }) => {
    await loginAsMember(page)
  })

  test('updates own profile and research fields, then restores the fixture', async ({ page }) => {
    await page.goto('/profile')

    const bio = page.getByLabel('Giới thiệu')
    const originalBio = await bio.inputValue()
    const marker = `Member E2E ${Date.now()}`
    const configuredFieldName = env.member.researchFieldName
    const fieldOption = configuredFieldName
      ? page.locator('.field-options .field-option').filter({
          has: page.locator('strong').filter({
            hasText: new RegExp(`^${escapeRegex(configuredFieldName)}$`),
          }),
        })
      : page.locator('.field-options .field-option').first()

    if (configuredFieldName) {
      await expect(
        fieldOption,
        `Expected exactly one research field option named "${configuredFieldName}".`,
      ).toHaveCount(1)
    }

    const fieldCheckbox = fieldOption.locator('input[type="checkbox"]')

    await expect(fieldCheckbox, 'The fixture must expose at least one active research field.').toBeVisible()
    const originallyChecked = await fieldCheckbox.isChecked()

    try {
      await bio.fill(marker)
      await fieldCheckbox.setChecked(!originallyChecked)

      const saveResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/me/profile', 'PATCH'))
      await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
      const saveResponse = await saveResponsePromise
      await expectOk(saveResponse, 'PATCH /me/profile')

      await expect(page.getByText('Đã lưu hồ sơ thành viên.')).toBeVisible()
      await expect(bio).toHaveValue(marker)
      if (originallyChecked) {
        await expect(fieldCheckbox).not.toBeChecked()
      } else {
        await expect(fieldCheckbox).toBeChecked()
      }
    } finally {
      await bio.fill(originalBio)
      await fieldCheckbox.setChecked(originallyChecked)
      const restoreResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/me/profile', 'PATCH'))
      await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
      await expectOk(await restoreResponsePromise, 'restore PATCH /me/profile')
    }
  })

  test('uploads an avatar and restores the original avatar', async ({ page, request }) => {
    await page.goto('/profile')

    const token = await authToken(page)
    const original = await getMyProfile(request, token)
    let uploadedFileId: number | undefined

    try {
      const uploadResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/files/upload', 'POST'))
      const profileResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/me/profile', 'PATCH'))
      await page.getByTitle('Đổi ảnh đại diện').locator('input[type="file"]').setInputFiles({
        name: `member-avatar-${Date.now()}.png`,
        mimeType: 'image/png',
        buffer: onePixelPng(),
      })
      const uploadResponse = await uploadResponsePromise
      if (!uploadResponse.ok()) {
        void profileResponsePromise.catch(() => undefined)
      }
      test.skip(
        isExternalDependencyUnavailable(uploadResponse),
        `File storage is unavailable (HTTP ${uploadResponse.status()}).`,
      )
      await expectOk(uploadResponse, 'avatar POST /files/upload')
      uploadedFileId = ((await uploadResponse.json()) as { id: number }).id

      const profileResponse = await profileResponsePromise
      await expectOk(profileResponse, 'avatar PATCH /me/profile')
      await expect(page.getByText('Đã cập nhật ảnh đại diện.')).toBeVisible()
      await expect(page.getByRole('img', { name: 'Ảnh đại diện' })).toHaveAttribute('src', /^blob:/)
    } finally {
      if (uploadedFileId) {
        await restoreProfile(request, token, original)
        const cleanup = await request.delete(`${env.apiBaseURL}/files/${uploadedFileId}`, {
          headers: bearer(token),
        })
        expect([204, 404], 'Uploaded avatar cleanup failed.').toContain(cleanup.status())
      }
    }
  })

  test('uploads, downloads and deletes an own private file', async ({ page, request }) => {
    const token = await authToken(page)
    const fileName = `member-e2e-${Date.now()}.txt`
    const fileContents = `Smart Lab MEMBER file ${Date.now()}\n`
    let uploadedFileId: number | undefined
    let deleted = false

    await page.goto('/files')
    await expect(page.getByRole('heading', { level: 1, name: 'Tệp của tôi' })).toBeVisible()

    try {
      await page.locator('input[type="file"]').setInputFiles({
        name: fileName,
        mimeType: 'text/plain',
        buffer: Buffer.from(fileContents, 'utf8'),
      })
      await page.getByLabel('Phạm vi truy cập').selectOption('PRIVATE')
      await page.getByLabel('Mô tả').fill('Playwright MEMBER private-file verification')

      const uploadResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/files/upload', 'POST'))
      await page.getByRole('button', { name: 'Tải file lên' }).click()
      const uploadResponse = await uploadResponsePromise
      test.skip(
        isExternalDependencyUnavailable(uploadResponse),
        `File storage is unavailable (HTTP ${uploadResponse.status()}).`,
      )
      await expectOk(uploadResponse, 'file POST /files/upload')
      uploadedFileId = ((await uploadResponse.json()) as { id: number }).id

      const row = page.getByRole('article').filter({ hasText: fileName })
      await expect(row).toBeVisible()

      const listResponsePromise = page.waitForResponse((response) => apiPathEndsWith(response, '/me/files', 'GET'))
      await page.reload()
      const listResponse = await listResponsePromise
      await expectOk(listResponse, 'GET /me/files')
      expect(JSON.stringify(await listResponse.json())).toContain(fileName)
      await expect(page.getByRole('article').filter({ hasText: fileName })).toBeVisible()

      const anonymousDownload = await request.get(`${env.apiBaseURL}/files/${uploadedFileId}`)
      expect([401, 403], 'A PRIVATE file must reject an anonymous download.').toContain(anonymousDownload.status())

      const downloadPromise = page.waitForEvent('download')
      await row.getByTitle('Tải xuống').click()
      const download = await downloadPromise
      expect(download.suggestedFilename()).toBe(fileName)
      const stream = await download.createReadStream()
      expect((await readStream(stream)).toString('utf8')).toBe(fileContents)

      page.once('dialog', (dialog) => dialog.accept())
      const deleteResponsePromise = page.waitForResponse((response) =>
        apiPathEndsWith(response, `/files/${uploadedFileId}`, 'DELETE'))
      await row.getByTitle('Xóa file').click()
      await expectOk(await deleteResponsePromise, `DELETE /files/${uploadedFileId}`)
      deleted = true
      await expect(row).toHaveCount(0)
    } finally {
      if (uploadedFileId && !deleted) {
        await request.delete(`${env.apiBaseURL}/files/${uploadedFileId}`, { headers: bearer(token) })
      }
    }
  })
})

async function getMyProfile(request: APIRequestContext, token: string) {
  const response = await request.get(`${env.apiBaseURL}/me/profile`, { headers: bearer(token) })
  expect(response.ok(), `GET /me/profile failed with HTTP ${response.status()}.`).toBeTruthy()
  return await response.json() as MemberProfileSnapshot
}

async function restoreProfile(request: APIRequestContext, token: string, profile: MemberProfileSnapshot) {
  const response = await request.patch(`${env.apiBaseURL}/me/profile`, {
    headers: bearer(token),
    data: {
      phone: profile.phone ?? '',
      publicEmail: profile.publicEmail ?? '',
      bio: profile.bio ?? '',
      researchFieldIds: profile.researchFields.map((field) => field.id),
      ...(profile.avatar ? { avatarFileId: profile.avatar.id } : { removeAvatar: true }),
    },
  })
  expect(response.ok(), `Profile cleanup failed with HTTP ${response.status()}.`).toBeTruthy()
}

function bearer(token: string) {
  return { authorization: `Bearer ${token}` }
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function onePixelPng() {
  return Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
}

async function readStream(stream: Readable) {
  const chunks: Buffer[] = []
  for await (const chunk of stream) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk as Uint8Array))
  return Buffer.concat(chunks)
}
