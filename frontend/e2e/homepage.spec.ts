import { expect, test } from '@playwright/test'

test.use({ video: 'off' })

const MOCK_RESEARCH_FIELDS_3 = [
  { id: 1, code: 'AI', name: 'Trí tuệ nhân tạo' },
  { id: 2, code: 'ROBOTICS', name: 'Hệ thống thông minh & Robotics' },
  { id: 3, code: 'SE', name: 'Kỹ thuật phần mềm & Nền tảng số' },
]

const MOCK_RESEARCH_FIELDS_5 = [
  { id: 1, code: 'AI', name: 'Trí tuệ nhân tạo' },
  { id: 2, code: 'ROBOTICS', name: 'Hệ thống thông minh & Robotics' },
  { id: 3, code: 'SE', name: 'Kỹ thuật phần mềm & Nền tảng số' },
  { id: 4, code: 'IOT', name: 'Internet vạn vật & Hệ thống nhúng' },
  { id: 5, code: 'CYBER', name: 'An toàn thông tin & Mạng nâng cao' },
]

const MOCK_RESEARCH_FIELDS_8 = [
  { id: 1, code: 'AI', name: 'Trí tuệ nhân tạo' },
  { id: 2, code: 'ROBOTICS', name: 'Hệ thống thông minh & Robotics' },
  { id: 3, code: 'SE', name: 'Kỹ thuật phần mềm & Nền tảng số' },
  { id: 4, code: 'IOT', name: 'Internet vạn vật & Hệ thống nhúng' },
  { id: 5, code: 'CYBER', name: 'An toàn thông tin & Mạng nâng cao' },
  { id: 6, code: 'DATA', name: 'Khoa học dữ liệu & Trí tuệ tính toán' },
  { id: 7, code: 'BIO', name: 'Tin sinh học & Y tế số' },
  { id: 8, code: 'QUANTUM', name: 'Điện toán lượng tử ứng dụng' },
]

const MOCK_ACHIEVEMENT_YEARS = [
  { year: 2026, count: 5 },
  { year: 2025, count: 8 },
]

const MOCK_ACHIEVEMENTS_2026 = {
  items: [
    {
      id: 301,
      title: 'Giải Nhất Nghiên cứu Khoa học Sinh viên Toàn quốc 2026',
      achievementType: 'AWARD',
      achievementDate: '2026-03-15',
      achievementYear: 2026,
      summary: 'Đề tài ứng dụng thị giác máy tính và học sâu trong giám sát tự động môi trường vi khí hậu.',
      evidenceUrl: 'https://example.com/evidence-301',
      relatedProject: { id: 101, name: 'Hệ thống SmartLab AI Core' },
    },
    {
      id: 302,
      title: 'Bài báo xuất bản trên Tạp chí Khoa học & Công nghệ Quốc tế IEEE',
      achievementType: 'RESEARCH_RESULT',
      achievementDate: '2026-02-20',
      achievementYear: 2026,
      summary: 'Nghiên cứu về giải thuật điều khiển phân tán thời gian thực cho robot bầy đàn đa tác tử.',
      evidenceUrl: null,
      relatedProject: { id: 102, name: 'Robot Tự Hành Trinh Sát' },
    },
  ],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 4,
}

const MOCK_RECRUITING_PROJECTS = {
  items: [
    {
      id: 101,
      name: 'Hệ thống SmartLab AI Core',
      code: 'PRJ-001',
      projectType: 'APPLICATION',
      publicStatus: 'RECRUITING',
      researchFields: [{ id: 1, name: 'Trí tuệ nhân tạo' }],
    },
    {
      id: 102,
      name: 'Robot Tự Hành Trinh Sát',
      code: 'PRJ-002',
      projectType: 'RESEARCH',
      publicStatus: 'RECRUITING',
      researchFields: [{ id: 2, name: 'Robotics' }],
    },
    {
      id: 103,
      name: 'Nền tảng Thị giác Máy tính',
      code: 'PRJ-003',
      projectType: 'APPLICATION',
      publicStatus: 'RECRUITING',
      researchFields: [{ id: 1, name: 'Trí tuệ nhân tạo' }],
    },
  ],
  totalElements: 3,
  totalPages: 1,
  page: 0,
  size: 2,
}

const MOCK_GALLERY = {
  items: [
    {
      id: 401,
      title: 'Hội thảo Khoa học Thường niên SmartLab 2026',
      caption: 'Báo cáo chuyên đề các nhóm nghiên cứu AI và Robotics',
      fileId: 501,
      category: 'EVENT',
    },
    {
      id: 402,
      title: 'Không gian Làm việc & Thử nghiệm Phần cứng',
      caption: 'Khu vực lắp ráp và đo đạc thông số robot',
      fileId: 502,
      category: 'ACTIVITY',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 8,
}

test.describe('SmartLab Public Homepage UX/UI Completion', () => {
  test.beforeEach(async ({ page }) => {
    // Default API mocks
    await page.route('**/api/v1.0/research-fields*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_RESEARCH_FIELDS_3),
      })
    })

    await page.route('**/api/v1.0/achievements/years*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_ACHIEVEMENT_YEARS),
      })
    })

    await page.route(/\/api\/v1\.0\/achievements(?:\?.*)?$/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_ACHIEVEMENTS_2026),
      })
    })

    await page.route('**/api/v1.0/projects/public/recruiting*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_RECRUITING_PROJECTS),
      })
    })

    await page.route('**/api/v1.0/gallery/public*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_GALLERY),
      })
    })

    const MOCK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300"><rect width="400" height="300" fill="#1e293b"/><text x="50%" y="50%" fill="#94a3b8" font-family="system-ui" font-size="16" text-anchor="middle" dominant-baseline="middle">SmartLab Culture Image</text></svg>`
    await page.route('**/api/v1.0/files/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'image/svg+xml',
        body: MOCK_SVG,
      })
    })
  })

  test('homepage renders sections in logical editorial hierarchy', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    // 1. Hero
    await expect(page.locator('.landing-hero')).toBeVisible()

    // 2. Achievements
    const achievementsSection = page.locator('#thanh-tuu')
    await expect(achievementsSection).toBeVisible()
    await expect(achievementsSection.locator('h2')).toHaveText('Kết quả & Thành tựu')

    // 3. Lab Life & Culture
    const cultureSection = page.locator('#van-hoa-doi-song')
    await expect(cultureSection).toBeVisible()
    await expect(cultureSection.locator('h2')).toHaveText('Văn hoá & Đời sống Lab')

    // 4. Research Fields
    const researchSection = page.locator('#research-fields')
    await expect(researchSection).toBeVisible()
    await expect(researchSection.locator('h2')).toHaveText('Định hướng nghiên cứu')

    // 5. Recruiting Spotlight
    const recruitSection = page.locator('#du-an-tuyen-dung')
    await expect(recruitSection).toBeVisible()
    await expect(recruitSection.locator('h2')).toHaveText('Gia nhập các nhóm nghiên cứu tại Smart Lab')

    // 6. Final CTA
    await expect(page.locator('.landing-final-cta')).toBeVisible()
  })

  test('achievements feature clear affordance to detail /thanh-tuu/:id', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    // Check achievement title is a link targeting /thanh-tuu/:id
    const titleLink = page.locator('.landing-achievement-title-link').first()
    await expect(titleLink).toBeVisible()
    await expect(titleLink).toHaveAttribute('href', /\/thanh-tuu\/\d+/)

    // Check detail action button targeting /thanh-tuu/:id
    const detailBtn = page.locator('.landing-achievement-detail-btn').first()
    await expect(detailBtn).toBeVisible()
    await expect(detailBtn).toHaveAttribute('href', /\/thanh-tuu\/\d+/)
    await expect(detailBtn).toContainText('Chi tiết')
  })

  test('research fields have direct detail navigation to /linh-vuc/:code', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    // Check card cover is a link to /linh-vuc/AI
    const coverLink = page.locator('.landing-research-card-cover').first()
    await expect(coverLink).toBeVisible()
    await expect(coverLink).toHaveAttribute('href', '/linh-vuc/AI')

    // Check card title is a link to /linh-vuc/AI
    const cardTitleLink = page.locator('.landing-research-title-link').first()
    await expect(cardTitleLink).toBeVisible()
    await expect(cardTitleLink).toHaveAttribute('href', '/linh-vuc/AI')

    // Check action button is a link to /linh-vuc/AI
    const actionBtn = page.locator('.landing-research-card-btn').first()
    await expect(actionBtn).toBeVisible()
    await expect(actionBtn).toHaveAttribute('href', '/linh-vuc/AI')
  })

  test('recruiting spotlight shows curated preview with max 2 items', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    const previewCards = page.locator('.landing-recruit-preview-card')
    // Must be at most 2 items even if API returned more
    await expect(previewCards).toHaveCount(2)

    // Check first card links to /du-an/:id
    await expect(previewCards.first()).toHaveAttribute('href', /\/du-an\/\d+/)
  })

  test('culture section renders continuous infinite image strip with multiple visible images and CTA', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    const cultureSection = page.locator('#van-hoa-doi-song')
    await expect(cultureSection).toBeVisible()

    // 1. CTA link in section header
    const ctaLink = cultureSection.locator('.landing-section-cta')
    await expect(ctaLink).toBeVisible()
    await expect(ctaLink).toHaveAttribute('href', '/thu-vien-anh')
    await expect(ctaLink).toContainText('Xem toàn bộ thư viện ảnh')

    // 2. Continuous loop reel & track exists
    const reel = cultureSection.locator('.landing-culture-reel')
    await expect(reel).toBeVisible()

    const track = reel.locator('.landing-culture-track')
    await expect(track).toBeVisible()

    // Primary track group and duplicate track group exist
    const primaryGroup = track.locator('.landing-culture-track-group').first()
    const duplicateGroup = track.locator('.landing-culture-track-group[data-duplicate="true"]')
    await expect(primaryGroup).toBeVisible()
    await expect(duplicateGroup).toBeAttached()
    await expect(duplicateGroup).toHaveAttribute('aria-hidden', 'true')

    // 3. Multiple images visible in viewport simultaneously on desktop
    const cards = reel.locator('.landing-culture-card')
    const totalCards = await cards.count()
    expect(totalCards).toBeGreaterThanOrEqual(4)

    // Check visible cards in desktop container
    const reelBox = await reel.boundingBox()
    expect(reelBox).toBeTruthy()
    if (reelBox) {
      let visibleCount = 0
      for (let i = 0; i < totalCards; i++) {
        const box = await cards.nth(i).boundingBox()
        if (box && box.x + box.width > reelBox.x && box.x < reelBox.x + reelBox.width) {
          visibleCount++
        }
      }
      // Desktop must show ~3-4 images simultaneously
      expect(visibleCount).toBeGreaterThanOrEqual(3)
    }

    // 4. Card affordance & alt metadata
    const firstCard = cards.first()
    await expect(firstCard).toHaveAttribute('href', '/thu-vien-anh')
    const firstImg = firstCard.locator('img')
    await expect(firstImg).toHaveAttribute('alt', /.+/)
  })

  test('culture reel respects prefers-reduced-motion by disabling animation and hiding duplicate track', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    const reel = page.locator('.landing-culture-reel')
    await expect(reel).toBeVisible()

    // Duplicate track must be hidden under reduced motion
    const duplicateGroup = page.locator('.landing-culture-track-group[data-duplicate="true"]')
    await expect(duplicateGroup).toBeHidden()

    // Track animation must be disabled
    const track = page.locator('.landing-culture-track')
    const animName = await track.evaluate((el) => window.getComputedStyle(el).animationName)
    expect(animName).toBe('none')

    // Reel must be scrollable
    const overflowX = await reel.evaluate((el) => window.getComputedStyle(el).overflowX)
    expect(overflowX).toBe('auto')
  })

  test('research matrix extensibility with 5 fields and 8 fields', async ({ page }) => {
    // 1. Test with 5 fields
    await page.route('**/api/v1.0/research-fields*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_RESEARCH_FIELDS_5),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    const cards5 = page.locator('.landing-research-card')
    await expect(cards5).toHaveCount(5)

    // Fallback UI should render for fields without pre-bundled image (e.g. IOT, CYBER)
    const fallbackBox = page.locator('.landing-research-card-fallback').first()
    await expect(fallbackBox).toBeVisible()

    // 2. Test with 8 fields
    await page.route('**/api/v1.0/research-fields*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_RESEARCH_FIELDS_8),
      })
    })

    await page.goto('/')
    const cards8 = page.locator('.landing-research-card')
    await expect(cards8).toHaveCount(8)
  })

  test('empty recruiting projects renders clean empty state without broken UI', async ({ page }) => {
    await page.route('**/api/v1.0/projects/public/recruiting*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], totalElements: 0, totalPages: 0, page: 0, size: 2 }),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    const emptyState = page.locator('.landing-recruit-spotlight-preview .landing-empty-state')
    await expect(emptyState).toBeVisible()
    await expect(emptyState).toContainText('Hiện tại các nhóm nghiên cứu đang hoàn thiện đợt tuyển.')
  })

  test('responsive verification across 1440, 1024, 768, 375 with zero horizontal overflow and screenshot captures', async ({ page }) => {
    const viewports = [
      { name: 'desktop-1440', width: 1440, height: 900 },
      { name: 'laptop-1024', width: 1024, height: 768 },
      { name: 'tablet-768', width: 768, height: 1024 },
      { name: 'mobile-375', width: 375, height: 812 },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/')
      await page.waitForSelector('.landing-research-card')

      // Verify zero horizontal overflow
      const overflow = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth + 1
      })
      expect(overflow, `Horizontal overflow detected at ${vp.name}`).toBe(false)

      // Capture full-page screenshot for visual QA
      await page.screenshot({
        path: `e2e/.artifacts/screenshots/${vp.name}-homepage-full.png`,
        fullPage: true,
      })
    }

    // Capture focused section screenshots at desktop 1440
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    await page.waitForSelector('.landing-research-card')

    await page.locator('#thanh-tuu').screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-section-achievements.png',
    })

    await page.locator('#van-hoa-doi-song').screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-section-culture.png',
    })

    // Capture culture section at tablet 768 and mobile 375
    await page.setViewportSize({ width: 768, height: 1024 })
    await page.goto('/')
    await page.waitForSelector('.landing-culture-reel')
    await page.locator('#van-hoa-doi-song').screenshot({
      path: 'e2e/.artifacts/screenshots/tablet-768-section-culture.png',
    })

    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto('/')
    await page.waitForSelector('.landing-culture-reel')
    await page.locator('#van-hoa-doi-song').screenshot({
      path: 'e2e/.artifacts/screenshots/mobile-375-section-culture.png',
    })

    // Reset to desktop 1440 for remaining sections
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    await page.waitForSelector('.landing-research-card')

    await page.locator('#research-fields').screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-section-research-fields.png',
    })

    await page.locator('#du-an-tuyen-dung').screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-section-recruiting-spotlight.png',
    })
  })
})
