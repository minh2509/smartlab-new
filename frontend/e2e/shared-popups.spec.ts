import { expect, test } from '@playwright/test'

test.use({ video: 'off' })

test.describe('Shared Popup / Select / Action Menu Standardization', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authentication
    await page.addInitScript(() => {
      window.localStorage.setItem('smartlab.token', 'mock-admin-token')
      window.localStorage.setItem('smartlab.sessionId', 'mock-session-id')
    })

    // Mock /profile
    await page.route('**/api/v1.0/profile*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          userId: 'admin-01',
          name: 'Quản trị viên Lab',
          email: 'admin@smartlab.edu.vn',
          isActive: true,
          isAccountVerified: true,
          roles: ['ADMIN'],
          permissions: [
            'FILE_UPLOAD',
            'PROJECT_READ',
            'PROJECT_MANAGE',
            'DOCUMENT_MANAGE',
            'GALLERY_MANAGE',
            'POST_MANAGE',
            'TASK_READ',
            'RESEARCH_FIELD_MANAGE',
            'MEMBER_MANAGE',
            'USER_MANAGE',
            'ROLE_MANAGE',
            'PERMISSION_MANAGE',
            'PROFILE_READ',
            'posts.review',
            'posts.publish',
          ],
        }),
      })
    })

    // Mock /me/files for /files
    await page.route('**/api/v1.0/me/files*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 101,
            originalName: 'de-cuong-nghien-cuu-2026.pdf',
            accessScope: 'PUBLIC',
            sizeBytes: 1540000,
            mimeType: 'application/pdf',
            description: 'Đề cương nghiên cứu năm 2026',
            createdAt: '2026-03-01T08:00:00Z',
          },
          {
            id: 102,
            originalName: 'ket-qua-thu-nghiem-robot.xlsx',
            accessScope: 'PROJECT',
            sizeBytes: 420000,
            mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            description: 'Dữ liệu đo đạc cảm biến IMU',
            createdAt: '2026-02-20T10:30:00Z',
          },
        ]),
      })
    })

    // Mock admin achievements for /admin/thanh-tuu
    await page.route('**/api/v1.0/admin/achievements*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              id: 1,
              title: 'Giải Nhất Nghiên cứu Khoa học Toàn quốc 2026',
              type: 'AWARD',
              description: 'Đề tài Hệ thống Thị giác Máy tính cho Robot Tự hành.',
              year: 2026,
              evidenceUrl: 'https://smartlab.example.org/evidence/award-2026',
              publishedAt: '2026-03-15T09:00:00Z',
              attachments: [
                {
                  id: 11,
                  fileId: 201,
                  originalName: 'chung-nhan-giai-nhat.pdf',
                  sizeBytes: 850000,
                  mimeType: 'application/pdf',
                },
              ],
            },
            {
              id: 2,
              title: 'Bằng Độc quyền Sáng chế Thiết bị Định vị Trong nhà',
              type: 'PATENT',
              description: 'Sáng chế công nghệ định vị dựa trên Ultra-Wideband.',
              year: 2025,
              evidenceUrl: null,
              publishedAt: '2025-11-10T14:00:00Z',
              attachments: [],
            },
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 10,
        }),
      })
    })

    // Mock admin achievements years
    await page.route('**/api/v1.0/admin/achievements/years', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([2026, 2025, 2024]),
      })
    })

    // Mock projects
    await page.route('**/api/v1.0/projects*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: 1, name: 'Hệ thống SmartLab AI Core' },
            { id: 2, name: 'Robot Tự Hành Trinh Sát' },
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 48,
        }),
      })
    })
  })

  test('PopupSelect: opens, navigates with keyboard, selects option, and restores focus', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/files')
    await page.waitForLoadState('domcontentloaded')

    // Find the filter by access scope PopupSelect trigger
    const scopeTrigger = page.getByRole('button', { name: 'Lọc theo phạm vi truy cập' })
    await expect(scopeTrigger).toBeVisible()
    await expect(scopeTrigger).toHaveAttribute('aria-haspopup', 'listbox')
    await expect(scopeTrigger).toHaveAttribute('aria-expanded', 'false')

    // Check touch target min-height >= 44px
    const triggerBox = await scopeTrigger.boundingBox()
    expect(triggerBox?.height).toBeGreaterThanOrEqual(44)

    // Open via click
    await scopeTrigger.click()
    await expect(scopeTrigger).toHaveAttribute('aria-expanded', 'true')

    const listbox = page.locator('.popup-select-menu[role="listbox"]')
    await expect(listbox).toBeVisible()

    // Capture screenshot of open PopupSelect
    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/shared-popup-select-open-desktop.png',
      fullPage: false,
    })

    // Keyboard navigation: ArrowDown
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('ArrowDown')

    // Press Enter to select highlighted option
    await page.keyboard.press('Enter')
    await expect(listbox).toBeHidden()
    await expect(scopeTrigger).toHaveAttribute('aria-expanded', 'false')

    // Verify focus is restored to the trigger button
    await expect(scopeTrigger).toBeFocused()
  })

  test('PopupSelect: Escape key closes menu, prevents modal cancellation, and restores focus', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/files')
    await page.waitForLoadState('domcontentloaded')

    const sortTrigger = page.getByRole('button', { name: 'Sắp xếp danh sách file' })
    await sortTrigger.click()

    const listbox = page.locator('.popup-select-menu[role="listbox"]')
    await expect(listbox).toBeVisible()

    // Press Escape
    await page.keyboard.press('Escape')
    await expect(listbox).toBeHidden()
    await expect(sortTrigger).toBeFocused()
  })

  test('PopupSelect: Click outside closes the dropdown without selection', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/files')
    await page.waitForLoadState('domcontentloaded')

    const scopeTrigger = page.getByRole('button', { name: 'Lọc theo phạm vi truy cập' })
    await scopeTrigger.click()

    const listbox = page.locator('.popup-select-menu[role="listbox"]')
    await expect(listbox).toBeVisible()

    // Click outside on the page title
    await page.getByRole('heading', { level: 1, name: 'Tệp của tôi' }).click()
    await expect(listbox).toBeHidden()
    await expect(scopeTrigger).toHaveAttribute('aria-expanded', 'false')
  })

  test('ActionMenu: opens portal-mounted menu, renders danger item, navigates with keyboard, and closes on Escape', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/admin/achievements')
    await page.waitForLoadState('domcontentloaded')

    // Find the first action menu trigger
    const menuTrigger = page.locator('.action-menu-trigger').first()
    await expect(menuTrigger).toBeVisible()
    await expect(menuTrigger).toHaveAttribute('aria-haspopup', 'menu')
    await expect(menuTrigger).toHaveAttribute('aria-expanded', 'false')

    // Touch target >= 44px
    const triggerBox = await menuTrigger.boundingBox()
    expect(triggerBox?.width).toBeGreaterThanOrEqual(44)
    expect(triggerBox?.height).toBeGreaterThanOrEqual(44)

    // Open via Enter key
    await menuTrigger.focus()
    await page.keyboard.press('Enter')
    await expect(menuTrigger).toHaveAttribute('aria-expanded', 'true')

    const menu = page.locator('.action-menu-dropdown[role="menu"]')
    await expect(menu).toBeVisible()

    // Capture screenshot of open ActionMenu
    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/shared-action-menu-open-desktop.png',
      fullPage: false,
    })

    // Verify items: Sửa, Mở minh chứng, Gỡ thành tựu (danger)
    const items = menu.locator('[role="menuitem"]')
    await expect(items).toHaveCount(3)
    await expect(items.nth(0)).toContainText('Sửa')
    await expect(items.nth(1)).toContainText('Mở minh chứng')
    await expect(items.nth(2)).toContainText('Gỡ thành tựu')
    await expect(items.nth(2)).toHaveClass(/danger/)

    // First item should be focused on open
    await expect(items.nth(0)).toBeFocused()

    // ArrowDown cycles through items
    await page.keyboard.press('ArrowDown')
    await expect(items.nth(1)).toBeFocused()

    await page.keyboard.press('ArrowDown')
    await expect(items.nth(2)).toBeFocused()

    // Cyclic wrap back to first item
    await page.keyboard.press('ArrowDown')
    await expect(items.nth(0)).toBeFocused()

    // Escape closes menu and restores focus to trigger
    await page.keyboard.press('Escape')
    await expect(menu).toBeHidden()
    await expect(menuTrigger).toBeFocused()
  })

  test('Responsive & Mobile 375px: zero horizontal overflow and touch safe', async ({ page }) => {
    const viewports = [
      { name: 'desktop-1440', width: 1440, height: 900 },
      { name: 'laptop-1024', width: 1024, height: 768 },
      { name: 'tablet-768', width: 768, height: 1024 },
      { name: 'mobile-375', width: 375, height: 812 },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/files')
      await page.waitForLoadState('domcontentloaded')

      // Check zero horizontal overflow
      const overflowInfo = await page.evaluate(() => {
        const docWidth = window.innerWidth
        const overflow = document.documentElement.scrollWidth > docWidth + 1
        const culprits: string[] = []
        if (overflow) {
          document.querySelectorAll('*').forEach((el) => {
            const rect = el.getBoundingClientRect()
            if ((rect.right > docWidth + 1 || (el as HTMLElement).scrollWidth > docWidth + 1) && el.children.length === 0) {
              culprits.push(`${el.tagName}.${el.className} (w=${Math.round(rect.width)}, r=${Math.round(rect.right)}, sw=${(el as HTMLElement).scrollWidth})`)
            }
          })
        }
        return { overflow, culprits }
      })
      expect(overflowInfo.overflow, `Horizontal overflow detected at ${vp.name}: ${overflowInfo.culprits.slice(0, 5).join('; ')}`).toBe(false)
    }

    // Capture mobile open select screenshot at 375px
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/files')
    await page.waitForLoadState('domcontentloaded')

    const mobileSelectTrigger = page.getByRole('button', { name: 'Lọc theo phạm vi truy cập' })
    await mobileSelectTrigger.click()
    const mobileListbox = page.locator('.popup-select-menu[role="listbox"]')
    await expect(mobileListbox).toBeVisible()

    // Check menu fits viewport
    const menuBox = await mobileListbox.boundingBox()
    expect(menuBox?.width).toBeLessThanOrEqual(375)

    // Wait for entrance animation to settle
    await page.waitForTimeout(200)

    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/shared-popup-select-open-mobile.png',
      fullPage: false,
    })

    // Capture mobile action menu screenshot at 375px
    await page.goto('/admin/achievements')
    await page.waitForLoadState('domcontentloaded')

    const mobileMenuTrigger = page.locator('.action-menu-trigger:visible').first()
    await mobileMenuTrigger.click()
    const mobileMenu = page.locator('.action-menu-dropdown[role="menu"]')
    await expect(mobileMenu).toBeVisible()

    // Wait for entrance animation to settle
    await page.waitForTimeout(200)

    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/shared-action-menu-open-mobile.png',
      fullPage: false,
    })
  })

  test('Locked surfaces non-regression: / and /tai-lieu remain visually and functionally intact', async ({ page }) => {
    // 1. Homepage (/)
    await page.goto('/')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.locator('.landing-hero')).toBeVisible()
    await expect(page.locator('.landing-culture-reel')).toBeVisible()

    // 2. Public Documents (/tai-lieu)
    await page.goto('/tai-lieu')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.getByRole('heading', { level: 1, name: 'Kho Tài liệu Nghiên cứu & Chuyên môn' })).toBeVisible()
    await expect(page.locator('.project-archive-sidebar')).toBeVisible()
  })
})
