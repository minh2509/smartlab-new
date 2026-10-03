import { expect, test } from '@playwright/test'

test.use({ video: 'off' })

test.describe('public document archive synchronized with /du-an design', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept API routes to provide deterministic fixtures
    await page.route('**/api/v1.0/documents/public/years', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([2026, 2025, 2024, 2023, 2022, 2021, 2020]),
      })
    })

    await page.route('**/api/v1.0/documents/public/categories*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 1,
            code: 'NGHIEN-CUU',
            name: 'Nghiên cứu & Báo cáo khoa học',
            description: 'Báo cáo tổng kết đề tài, bài báo khoa học đã bình duyệt.',
            displayOrder: 1,
            isActive: true,
            documentCount: 24,
          },
          {
            id: 2,
            code: 'KY-THUAT',
            name: 'Đặc tả & Tài liệu kỹ thuật',
            description: 'Kiến trúc hệ thống, API specification.',
            displayOrder: 2,
            isActive: true,
            documentCount: 18,
          },
          {
            id: 3,
            code: 'QUY-TRINH',
            name: 'Hướng dẫn & Quy trình',
            description: 'Quy chế phòng thí nghiệm và quy trình vận hành nghiên cứu.',
            displayOrder: 3,
            isActive: true,
            documentCount: 12,
          },
          {
            id: 4,
            code: 'AN-PHAM',
            name: 'Ấn phẩm & Bài giảng',
            description: 'Tài liệu seminar, slide tập huấn, giáo trình nội bộ.',
            displayOrder: 4,
            isActive: true,
            documentCount: 9,
          },
        ]),
      })
    })

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

    await page.route('**/api/v1.0/documents/public*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              id: 201,
              title: 'Báo cáo Kiến trúc Vi điện tử và Xử lý Tín hiệu Tự động',
              description: 'Phân tích hiệu năng bộ vi điều khiển nhúng trong môi trường biên.',
              currentFileId: 501,
              originalFileName: 'bao-cao-kien-truc-vi-dien-tu-2026.pdf',
              sizeBytes: 4820000,
              mimeType: 'application/pdf',
              currentVersionNo: 2,
              projectId: 101,
              projectName: 'Hệ thống SmartLab AI Core',
              categoryId: 1,
              categoryCode: 'NGHIEN-CUU',
              categoryName: 'Nghiên cứu & Báo cáo khoa học',
              updatedAt: '2026-03-15T08:30:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 12,
        }),
      })
    })
  })

  test('guest sees synchronized /du-an layout across viewports with zero horizontal overflow', async ({ page }) => {
    const viewports = [
      { name: 'desktop', width: 1440, height: 900 },
      { name: 'desktop-1024', width: 1024, height: 768 },
      { name: 'tablet', width: 768, height: 1024 },
      { name: 'mobile', width: 375, height: 812 },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/tai-lieu')

      // Assert heading
      await expect(page.getByRole('heading', { level: 1, name: /Kho Tài liệu/i })).toBeVisible()

      // Assert left sidebar filter card is present
      await expect(page.locator('.project-sidebar-card')).toBeVisible()
      await expect(page.locator('.project-sidebar-search-input')).toBeVisible()

      // Assert category folder tabs at top of main area
      await expect(page.locator('.project-folder-tabs')).toBeVisible()
      const folderTabs = page.locator('.project-folder-tab')
      await expect(folderTabs.first()).toBeVisible()
      expect(await folderTabs.count()).toBeGreaterThanOrEqual(3)

      // Assert document cards are rendered
      await expect(page.locator('.doc-card')).toBeVisible()
      await expect(page.locator('.doc-download-btn')).toBeVisible()

      // Assert ZERO forbidden words ("folder", "thư mục", em-dash, en-dash)
      const pageText = await page.evaluate(() => document.body.innerText)
      expect(pageText.toLowerCase()).not.toContain('folder')
      expect(pageText.toLowerCase()).not.toContain('thư mục')
      expect(pageText).not.toContain('Niên giám lưu trữ')
      expect(pageText).not.toContain('Nhóm nội dung chuyên môn')
      expect(pageText).not.toContain('—')
      expect(pageText).not.toContain('–')

      // Assert NO unintentional horizontal overflow on non-scroll elements
      const overflowInfo = await page.evaluate(() => {
        const pageEl = document.querySelector('.public-docs-page')
        if (!pageEl) return [{ tag: 'MISSING', className: 'public-docs-page', id: '', scrollWidth: 0, clientWidth: 0 }]
        const elements = [pageEl, ...pageEl.querySelectorAll('*')]
        const list = []
        const maxAllowed = document.documentElement.clientWidth + 1
        for (const el of elements) {
          const style = window.getComputedStyle(el)
          const isScrollContainer = style.overflowX === 'auto' || style.overflowX === 'scroll'
          if (!isScrollContainer && el.scrollWidth > maxAllowed) {
            list.push({
              tag: el.tagName,
              className: typeof el.className === 'string' ? el.className : '',
              id: el.id,
              scrollWidth: el.scrollWidth,
              clientWidth: el.clientWidth,
            })
          }
        }
        return list
      })
      if (overflowInfo.length > 0) {
        console.log(`Overflow on ${vp.name}:`, JSON.stringify(overflowInfo, null, 2))
      }
      expect(overflowInfo.length, `Unintentional horizontal overflow detected in archive at viewport ${vp.name} (${vp.width}px)`).toBe(0)
    }
  })

  test('category folder tabs switch active category and update URL', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/tai-lieu')

    // Click on "Nghiên cứu & Báo cáo khoa học" tab
    const catTab = page.locator('.project-folder-tab', { hasText: 'Nghiên cứu & Báo cáo khoa học' })
    await catTab.click()
    await expect(page).toHaveURL(/category=NGHIEN-CUU/)
    await expect(catTab).toHaveClass(/is-active/)

    // Click on "Tất cả" tab
    const allTab = page.locator('.project-folder-tab', { hasText: 'Tất cả' })
    await allTab.click()
    await expect(page).not.toHaveURL(/category=/)
    await expect(allTab).toHaveClass(/is-active/)
  })

  test('year filter with compact 3-year row and expandable grid updates URL', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/tai-lieu')

    // In compact row, click year 2026
    const year2026Btn = page.locator('.project-years-compact-row .project-year-btn', { hasText: '2026' })
    await year2026Btn.click()
    await expect(page).toHaveURL(/year=2026/)
    await expect(year2026Btn).toHaveClass(/is-active/)

    // Click "Mở rộng năm" toggle button
    const toggleBtn = page.locator('.project-year-toggle-btn')
    await toggleBtn.click()
    await expect(page.locator('.project-years-expanded-box')).toBeVisible()

    // Click year 2024 in expanded grid
    const year2024Btn = page.locator('.project-years-grid .project-year-btn', { hasText: '2024' })
    await year2024Btn.click()
    await expect(page).toHaveURL(/year=2024/)

    // Click "Tất cả các năm"
    const allYearsBtn = page.locator('.project-year-all-btn')
    await allYearsBtn.click()
    await expect(page).not.toHaveURL(/year=/)
  })

  test('search input filters documents and updates URL', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/tai-lieu')

    const searchInput = page.locator('#doc-search-input')
    await searchInput.fill('Vi điện tử')
    await expect(page).toHaveURL(/q=Vi\+%C4%91i%E1%BB%87n\+t%E1%BB%AD|q=Vi/)

    // Test reset filters button
    const resetBtn = page.locator('.project-sidebar-reset-btn')
    await resetBtn.click()
    await expect(page).not.toHaveURL(/q=/)
  })

  test('accordion dropdowns filter by category, file type, project, and sort', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/tai-lieu')

    // Category dropdown
    const catSelect = page.locator('#doc-category-select')
    await catSelect.selectOption('KY-THUAT')
    await expect(page).toHaveURL(/category=KY-THUAT/)

    // File type dropdown
    const typeSelect = page.locator('#doc-filetype-select')
    await typeSelect.selectOption('PDF')
    await expect(page).toHaveURL(/type=PDF/)

    // Project dropdown
    const projSelect = page.locator('#doc-project-select')
    await projSelect.selectOption('101')
    await expect(page).toHaveURL(/project=101/)

    // Sort dropdown
    const sortSelect = page.locator('#doc-sort-select')
    await sortSelect.selectOption('TITLE_ASC')
    await expect(page).toHaveURL(/sort=TITLE_ASC/)
  })

  test('shows empty state when no documents match', async ({ page }) => {
    await page.route('**/api/v1.0/documents/public*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [],
          totalElements: 0,
          totalPages: 0,
          page: 0,
          size: 12,
        }),
      })
    })

    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/tai-lieu?q=NonExistent')

    await expect(page.getByText('Không tìm thấy tài liệu phù hợp')).toBeVisible()
  })

  test('admin document category page is protected and accessible via rbac', async ({ page }) => {
    await page.goto('/admin/document-categories')
    await expect(page).toHaveURL(/\/(login|profile|$)/)
  })

  test('category folder tabs row is horizontally scrollable, keeps single line, and reaches "Ấn phẩm & Bài giảng" across viewports', async ({ page }) => {
    const viewports = [
      { name: '1440-desktop', width: 1440, height: 900 },
      { name: '1024-standard', width: 1024, height: 768 },
      { name: '768-tablet', width: 768, height: 1024 },
      { name: '375-mobile', width: 375, height: 812 },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/tai-lieu')

      // Ensure tabs wrapper and tabs strip are visible
      const tabsWrapper = page.locator('.project-folder-tabs-wrapper')
      await expect(tabsWrapper).toBeVisible()
      const tabsContainer = page.locator('.project-folder-tabs')
      await expect(tabsContainer).toBeVisible()

      // Verify all 5 tabs are rendered
      const tabs = page.locator('.project-folder-tab')
      await expect(tabs).toHaveCount(5)

      // Guarantee single-line: all tabs must share the exact same vertical offset (no line-wrapping)
      const offsetTops = await tabs.evaluateAll((elements) =>
        elements.map((el) => (el as HTMLElement).offsetTop)
      )
      const minOffset = Math.min(...offsetTops)
      const maxOffset = Math.max(...offsetTops)
      expect(maxOffset - minOffset, `Tabs must remain on a single line on ${vp.name}`).toBeLessThanOrEqual(4)

      // Verify flex-shrink: 0 and white-space: nowrap on every tab
      const tabStyles = await tabs.evaluateAll((elements) =>
        elements.map((el) => {
          const s = window.getComputedStyle(el)
          return { flexShrink: s.flexShrink, whiteSpace: s.whiteSpace }
        })
      )
      for (const s of tabStyles) {
        expect(s.flexShrink).toBe('0')
        expect(s.whiteSpace).toBe('nowrap')
      }

      // Check overflow metrics on tabsContainer
      const scrollMetrics = await tabsContainer.evaluate((el) => ({
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth,
      }))

      // Locate "Ấn phẩm & Bài giảng" tab
      const lastTab = page.locator('.project-folder-tab', { hasText: 'Ấn phẩm & Bài giảng' })
      await expect(lastTab).toBeAttached()

      // If container overflows horizontally, verify scroll buttons / scrolling mechanics
      if (scrollMetrics.scrollWidth > scrollMetrics.clientWidth) {
        // Next scroll button should be available or container should be scrollable
        const nextBtn = page.locator('.project-folder-tabs-scroll-btn.is-next')
        if (await nextBtn.isVisible()) {
          await nextBtn.click()
        }
      }

      // Scroll last tab into view and click it
      await lastTab.scrollIntoViewIfNeeded()
      await expect(lastTab).toBeVisible()

      // Verify breathing room margin on last tab
      const lastTabMarginRight = await lastTab.evaluate((el) => {
        return window.getComputedStyle(el).marginRight
      })
      expect(lastTabMarginRight).toBe('28px')

      // Click "Ấn phẩm & Bài giảng"
      await lastTab.click()

      // Assert URL updated with category param
      await expect(page).toHaveURL(/category=AN-PHAM/)

      // Assert active styling on the tab
      await expect(lastTab).toHaveClass(/is-active/)

      // Active styling assertions (white background, orange accent)
      await expect(lastTab).toHaveCSS('background-color', 'rgb(255, 255, 255)')
      await expect(lastTab).toHaveCSS('color', 'rgb(234, 88, 12)')

      // Active badge styling assertion
      const activeBadge = lastTab.locator('.project-folder-tab-count')
      await expect(activeBadge).toHaveCSS('background-color', 'rgb(255, 237, 213)')
      await expect(activeBadge).toHaveCSS('color', 'rgb(194, 65, 12)')

      // Assert ZERO page horizontal overflow outside tabs
      const isDocumentOverflowing = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth + 1
      })
      expect(isDocumentOverflowing, `Entire page must have zero horizontal overflow at ${vp.name}`).toBe(false)

      // Capture screenshot for visual evidence
      await page.screenshot({
        path: `e2e/.artifacts/screenshots/tai-lieu-category-tabs-${vp.name}.png`,
        fullPage: false,
      })
    }
  })
})
