import { expect, test } from '@playwright/test'

test.use({ video: 'off' })

test.describe('public gallery photo library (/thu-vien-anh)', () => {
  test.beforeEach(async ({ page }) => {
    // Mock gallery years
    await page.route('**/api/v1.0/gallery/public/years', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([2026, 2025, 2024, 2023]),
      })
    })

    // Mock public projects
    await page.route('**/api/v1.0/projects/public*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: 101, name: 'Hệ thống SmartLab AI Core' },
            { id: 102, name: 'Robot Tự Hành Trinh Sát' },
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 48,
        }),
      })
    })

    await page.route('**/api/v1.0/projects*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: 101, name: 'Hệ thống SmartLab AI Core' },
            { id: 102, name: 'Robot Tự Hành Trinh Sát' },
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 48,
        }),
      })
    })

    // Mock gallery items
    await page.route('**/api/v1.0/gallery/public*', async (route) => {
      const url = new URL(route.request().url())
      const query = url.searchParams.get('q')
      const category = url.searchParams.get('category')
      const year = url.searchParams.get('year')

      if (query === 'none') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            items: [],
            totalElements: 0,
            totalPages: 0,
            page: 0,
            size: 24,
          }),
        })
        return
      }

      const mockItems = [
        {
          id: 1,
          title: 'Hội thảo Trí tuệ Nhân tạo Biên 2026',
          caption: 'Nhóm nghiên cứu trình diễn mô hình Edge AI tối ưu hoá năng lượng trên vi điều khiển nhúng.',
          altText: 'Hội thảo Edge AI 2026',
          category: 'WORKSHOP',
          projectId: 101,
          projectCode: 'PRJ-AI-01',
          projectName: 'Hệ thống SmartLab AI Core',
          eventId: 201,
          eventTitle: 'Workshop Khoa học Mùa xuân',
          fileId: 901,
          originalFileName: 'workshop-edge-ai.webp',
          mimeType: 'image/webp',
          sizeBytes: 1540000,
          capturedAt: '2026-03-20T10:00:00Z',
          publishedAt: '2026-03-21T08:00:00Z',
          isFeatured: true,
        },
        {
          id: 2,
          title: 'Thử nghiệm Robot Tự Hành Trong Phòng Lab',
          caption: 'Thử nghiệm thuật toán định vị SLAM trong không gian đa vật cản.',
          altText: 'Thử nghiệm Robot SLAM',
          category: 'PROJECT_DEMO',
          projectId: 102,
          projectCode: 'PRJ-ROBOT-02',
          projectName: 'Robot Tự Hành Trinh Sát',
          eventId: null,
          eventTitle: null,
          fileId: 902,
          originalFileName: 'robot-slam-demo.webp',
          mimeType: 'image/webp',
          sizeBytes: 2180000,
          capturedAt: '2026-02-15T14:30:00Z',
          publishedAt: '2026-02-16T09:00:00Z',
          isFeatured: false,
        },
        {
          id: 3,
          title: 'Buổi Sinh hoạt Khoa học và Chia sẻ Công nghệ',
          caption: 'Các thành viên lab trao đổi tiến độ bài báo và tài liệu kỹ thuật.',
          altText: 'Sinh hoạt Lab',
          category: 'LAB_ACTIVITY',
          projectId: null,
          projectCode: null,
          projectName: null,
          eventId: null,
          eventTitle: null,
          fileId: 903,
          originalFileName: 'lab-culture-meeting.webp',
          mimeType: 'image/webp',
          sizeBytes: 980000,
          capturedAt: '2026-01-10T16:00:00Z',
          publishedAt: '2026-01-11T10:00:00Z',
          isFeatured: false,
        },
      ]

      let filtered = mockItems
      if (category && category !== 'ALL') {
        filtered = filtered.filter((item) => item.category === category)
      }
      if (year) {
        filtered = filtered.filter((item) => item.capturedAt?.startsWith(year))
      }
      if (query) {
        filtered = filtered.filter((item) =>
          item.title.toLowerCase().includes(query.toLowerCase()) ||
          (item.caption && item.caption.toLowerCase().includes(query.toLowerCase())),
        )
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: filtered,
          totalElements: filtered.length,
          totalPages: 1,
          page: 0,
          size: 24,
        }),
      })
    })
  })

  test('renders editorial exhibition header, category tabs, and hero spotlight', async ({ page }) => {
    await page.goto('/thu-vien-anh')

    // Page title and editorial kicker
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện Hình ảnh & Đời sống Lab' })).toBeVisible()
    await expect(page.getByText('Ký ức & Tư liệu')).toBeVisible()

    // Level 1 category tabs
    const categoryNav = page.getByRole('tablist')
    await expect(categoryNav).toBeVisible()
    await expect(categoryNav.getByRole('tab', { name: 'Tất cả' })).toHaveClass(/is-active/)
    await expect(categoryNav.getByRole('tab', { name: 'Workshop' })).toBeVisible()
    await expect(categoryNav.getByRole('tab', { name: 'Demo dự án' })).toBeVisible()
    await expect(categoryNav.getByRole('tab', { name: 'Đời sống Lab' })).toBeVisible()

    // Results count bar
    await expect(page.getByText('3 ảnh tư liệu')).toBeVisible()
    await expect(page.getByText('24 ảnh / trang')).toBeVisible()

    // Hero Spotlight is rendered
    const spotlight = page.locator('.gallery-hero-spotlight')
    await expect(spotlight).toBeVisible()
    await expect(spotlight.getByRole('heading', { level: 2, name: 'Hội thảo Trí tuệ Nhân tạo Biên 2026' })).toBeVisible()
    await expect(spotlight.getByText('Tiêu điểm')).toBeVisible()

    // Supporting mosaic cards rendered (2 items in mosaic, 1 in spotlight)
    const mosaicCards = page.locator('.gallery-mosaic-card')
    await expect(mosaicCards).toHaveCount(2)

    // Copy check: no em-dashes and no "folder" or "thư mục"
    const pageText = await page.innerText('body')
    expect(pageText).not.toContain('—')
    expect(pageText).not.toContain('–')
    expect(pageText.toLowerCase()).not.toContain('thư mục')
    expect(pageText.toLowerCase()).not.toContain('folder')
  })

  test('category tab navigation filters items and updates URL', async ({ page }) => {
    await page.goto('/thu-vien-anh')

    // Click Workshop tab
    await page.getByRole('tab', { name: 'Workshop' }).click()
    await expect(page).toHaveURL(/category=WORKSHOP/)

    // Workshop card rendered in spotlight
    await expect(page.locator('.gallery-hero-spotlight')).toBeVisible()
    await expect(page.locator('.gallery-hero-spotlight').getByRole('heading', { level: 2, name: 'Hội thảo Trí tuệ Nhân tạo Biên 2026' })).toBeVisible()

    // Active chip rendered
    await expect(page.locator('.gallery-filter-pill', { hasText: 'Workshop' })).toBeVisible()

    // Click chip remove button to reset category
    await page.locator('.gallery-filter-pill', { hasText: 'Workshop' }).getByRole('button').click()
    await expect(page).not.toHaveURL(/category=/)
    await expect(page.getByText('3 ảnh tư liệu')).toBeVisible()
  })

  test('contextual filter drawer opens, performs search, and can be cleared', async ({ page }) => {
    await page.goto('/thu-vien-anh')

    // Open filter drawer
    const filterBtn = page.getByRole('button', { name: 'Mở bộ lọc nâng cao' })
    await filterBtn.click()

    const drawer = page.locator('#gallery-contextual-drawer')
    await expect(drawer).toBeVisible()
    await expect(drawer.getByRole('heading', { level: 2, name: 'Bộ lọc thư viện ảnh' })).toBeVisible()

    // Fill search query
    const searchInput = drawer.getByPlaceholder('Tìm theo tên ảnh, sự kiện, dự án...')
    await searchInput.fill('robot')

    // Click apply
    await drawer.getByRole('button', { name: /Xem kết quả/ }).click()
    await expect(drawer).not.toBeVisible()

    await expect(page).toHaveURL(/q=robot/)
    await expect(page.locator('.gallery-filter-pill', { hasText: '"robot"' })).toBeVisible()

    // Click "Xóa tất cả" to clear
    await page.getByRole('button', { name: 'Xóa tất cả' }).click()
    await expect(page).not.toHaveURL(/q=/)
  })

  test('lightbox modal opens with rich story, navigates photos and keyboard controls', async ({ page }) => {
    await page.goto('/thu-vien-anh')

    // Click hero spotlight to open lightbox
    const spotlightBtn = page.locator('.gallery-spotlight-trigger')
    await spotlightBtn.click()

    // Lightbox dialog appears
    const lightbox = page.locator('.gallery-lightbox-exhibition-dialog')
    await expect(lightbox).toBeVisible()
    await expect(lightbox.getByRole('heading', { level: 2, name: 'Hội thảo Trí tuệ Nhân tạo Biên 2026' })).toBeVisible()
    await expect(lightbox.getByText('Dự án:')).toBeVisible()
    await expect(lightbox.getByText('Hệ thống SmartLab AI Core')).toBeVisible()
    await expect(lightbox.getByText('Sự kiện:')).toBeVisible()
    await expect(lightbox.getByText('Workshop Khoa học Mùa xuân')).toBeVisible()
    await expect(lightbox.getByRole('link', { name: 'Tải ảnh gốc' })).toBeVisible()

    // Verify NO technical metadata slop is displayed
    const lightboxText = await lightbox.innerText()
    expect(lightboxText).not.toContain('image/webp')
    expect(lightboxText).not.toContain('Thông số tệp')

    // Capture screenshot of lightbox modal for visual verification
    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/gallery-lightbox-modal.png',
    })

    // Counter shows 1 / 3
    await expect(page.locator('.gallery-lightbox-nav-counter')).toContainText('1 / 3')

    // Next photo via ArrowRight key
    await page.keyboard.press('ArrowRight')
    await expect(page.locator('.gallery-lightbox-nav-counter')).toContainText('2 / 3')
    await expect(lightbox.getByRole('heading', { level: 2, name: 'Thử nghiệm Robot Tự Hành Trong Phòng Lab' })).toBeVisible()

    // Previous photo via "Trước" button
    await page.getByRole('button', { name: 'Xem ảnh trước' }).click()
    await expect(page.locator('.gallery-lightbox-nav-counter')).toContainText('1 / 3')

    // Close via Escape key
    await page.keyboard.press('Escape')
    await expect(lightbox).not.toBeVisible()
  })

  test('empty state renders when no items match and reset button works', async ({ page }) => {
    await page.goto('/thu-vien-anh?q=none')

    await expect(page.getByText('Không tìm thấy hình ảnh phù hợp')).toBeVisible()
    await expect(page.getByText('Thử thay đổi từ khóa, danh mục hoặc đặt lại bộ lọc')).toBeVisible()

    // Click "Đặt lại bộ lọc"
    await page.getByRole('button', { name: 'Đặt lại bộ lọc' }).click()
    await expect(page.getByText('3 ảnh tư liệu')).toBeVisible()
  })

  test('responsive layout verification across viewports with zero horizontal overflow', async ({ page }) => {
    const viewports = [
      { name: 'desktop-1440', width: 1440, height: 900 },
      { name: 'laptop-1024', width: 1024, height: 768 },
      { name: 'tablet-768', width: 768, height: 1024 },
      { name: 'mobile-375', width: 375, height: 812 },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/thu-vien-anh')
      await page.waitForLoadState('domcontentloaded')

      // Check zero horizontal overflow
      const overflow = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth + 1
      })
      expect(overflow, `Horizontal overflow detected at ${vp.name}`).toBe(false)

      // Take screenshot for visual QA evidence
      await page.screenshot({
        path: `e2e/.artifacts/screenshots/gallery-${vp.name}.png`,
        fullPage: true,
      })
    }
  })
})
