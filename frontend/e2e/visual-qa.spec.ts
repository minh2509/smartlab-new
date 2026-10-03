import { test } from '@playwright/test'

test.use({ video: 'off' })

const MOCK_YEARS = [2026, 2025, 2024, 2023, 2022, 2021, 2020]

const MOCK_CATEGORIES = [
  {
    id: 1,
    code: 'NGHIEN-CUU',
    name: 'Nghiên cứu & Báo cáo khoa học',
    description: 'Báo cáo tổng kết đề tài, bài báo khoa học đã bình duyệt và tiền ấn phẩm.',
    displayOrder: 1,
    isActive: true,
    documentCount: 24,
  },
  {
    id: 2,
    code: 'KY-THUAT',
    name: 'Đặc tả & Tài liệu kỹ thuật',
    description: 'Kiến trúc hệ thống, API specification, hướng dẫn triển khai và sơ đồ kỹ thuật.',
    displayOrder: 2,
    isActive: true,
    documentCount: 18,
  },
  {
    id: 3,
    code: 'DAO-TAO',
    name: 'Giáo trình & Tài liệu đào tạo',
    description: 'Tài liệu hướng dẫn thực tập, đề cương seminar và bài giảng chuyên đề nội bộ.',
    displayOrder: 3,
    isActive: true,
    documentCount: 15,
  },
  {
    id: 4,
    code: 'QUY-TRINH',
    name: 'Quy trình & Hướng dẫn chuẩn',
    description: 'Quy chuẩn phòng lab, quy trình vận hành thiết bị và an toàn nghiên cứu.',
    displayOrder: 4,
    isActive: true,
    documentCount: 9,
  },
]

const MOCK_PROJECTS = {
  items: [
    {
      id: 101,
      name: 'Hệ thống SmartLab AI Core',
      code: 'PRJ-001',
      description: 'Nền tảng trí tuệ nhân tạo lõi cho phòng thí nghiệm.',
      projectType: 'APPLICATION',
      publicStatus: 'RECRUITING',
      startDate: '2026-01-01',
      leaders: [{ id: 1, name: 'Nguyễn Văn An' }],
      researchFields: [{ id: 1, name: 'Trí tuệ nhân tạo' }],
    },
    {
      id: 102,
      name: 'Robot Tự Hành Trinh Sát',
      code: 'PRJ-002',
      description: 'Hệ thống robot thám hiểm môi trường nguy hiểm.',
      projectType: 'RESEARCH',
      publicStatus: 'RECRUITING',
      startDate: '2026-02-01',
      leaders: [{ id: 2, name: 'Trần Thị Bình' }],
      researchFields: [{ id: 2, name: 'Robotics' }],
    },
    {
      id: 103,
      name: 'Nền tảng Thị giác Máy tính',
      code: 'PRJ-003',
      description: 'Nhận diện và phân tích luồng video tốc độ cao.',
      projectType: 'APPLICATION',
      publicStatus: 'ACTIVE',
      startDate: '2026-01-15',
      leaders: [{ id: 3, name: 'Lê Hoàng Cường' }],
      researchFields: [{ id: 3, name: 'Thị giác máy tính' }],
    },
  ],
  totalElements: 3,
  totalPages: 1,
  page: 0,
  size: 48,
}

const MOCK_DOCUMENTS = {
  items: [
    {
      id: 201,
      title: 'Báo cáo Kiến trúc Vi điện tử và Xử lý Tín hiệu Tự động',
      description: 'Phân tích hiệu năng bộ vi điều khiển nhúng trong môi trường biên và tối ưu tiêu thụ năng lượng.',
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
    {
      id: 202,
      title: 'Đặc tả Kỹ thuật Giao thức Truyền dữ liệu Phân tán v2',
      description: 'Quy định cấu trúc gói tin RPC và thuật toán đồng thuận Byzantine chịu lỗi cục bộ.',
      currentFileId: 502,
      originalFileName: 'spec-protocol-rpc-v2.docx',
      sizeBytes: 1250000,
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      currentVersionNo: 1,
      projectId: 101,
      projectName: 'Hệ thống SmartLab AI Core',
      categoryId: 2,
      categoryCode: 'KY-THUAT',
      categoryName: 'Đặc tả & Tài liệu kỹ thuật',
      updatedAt: '2026-02-28T14:15:00Z',
    },
    {
      id: 203,
      title: 'Bảng Thống kê Ma trận Đánh giá Mô hình Thị giác',
      description: 'Dữ liệu đo đạc mAP, độ trễ suy luận trên chip Jetson Orin qua 10,000 khung hình.',
      currentFileId: 503,
      originalFileName: 'matrix-evaluation-cv-benchmarks.xlsx',
      sizeBytes: 890000,
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      currentVersionNo: 3,
      projectId: 103,
      projectName: 'Nền tảng Thị giác Máy tính',
      categoryId: 1,
      categoryCode: 'NGHIEN-CUU',
      categoryName: 'Nghiên cứu & Báo cáo khoa học',
      updatedAt: '2026-01-20T10:00:00Z',
    },
    {
      id: 204,
      title: 'Slide Báo cáo Seminar Khoa học Mùa Xuân 2026',
      description: 'Tổng quan đột phá học sâu không giám sát và phương hướng nghiên cứu tiếp theo.',
      currentFileId: 504,
      originalFileName: 'seminar-spring-2026-slides.pptx',
      sizeBytes: 15400000,
      mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      currentVersionNo: 1,
      projectId: 102,
      projectName: 'Robot Tự Hành Trinh Sát',
      categoryId: 3,
      categoryCode: 'DAO-TAO',
      categoryName: 'Giáo trình & Tài liệu đào tạo',
      updatedAt: '2026-01-10T16:45:00Z',
    },
    {
      id: 205,
      title: 'Bộ Công cụ và Mã nguồn Mẫu Tích hợp Cảm biến Lidar',
      description: 'Lưu trữ tệp nén chứa firmware C++, driver ROS2 và kịch bản hiệu chuẩn.',
      currentFileId: 505,
      originalFileName: 'lidar-driver-firmware-package.zip',
      sizeBytes: 32400000,
      mimeType: 'application/zip',
      currentVersionNo: 1,
      projectId: 102,
      projectName: 'Robot Tự Hành Trinh Sát',
      categoryId: 2,
      categoryCode: 'KY-THUAT',
      categoryName: 'Đặc tả & Tài liệu kỹ thuật',
      updatedAt: '2025-12-05T09:20:00Z',
    },
    {
      id: 206,
      title: 'Sổ tay Quy chuẩn Vận hành Phòng Thí nghiệm IoT',
      description: 'Quy định mượn trả linh kiện, an toàn tĩnh điện và bảo quản máy in 3D.',
      currentFileId: 506,
      originalFileName: 'quy-chuan-van-hanh-lab-iot.pdf',
      sizeBytes: 670000,
      mimeType: 'application/pdf',
      currentVersionNo: 4,
      projectId: 101,
      projectName: 'Hệ thống SmartLab AI Core',
      categoryId: 4,
      categoryCode: 'QUY-TRINH',
      categoryName: 'Quy trình & Hướng dẫn chuẩn',
      updatedAt: '2025-11-18T11:00:00Z',
    },
  ],
  totalElements: 6,
  totalPages: 1,
  page: 0,
  size: 12,
}

test.describe('visual qa and screenshot capture', () => {
  test.beforeEach(async ({ page }) => {
    // Intercept API routes to provide rich realistic fixtures
    await page.route('**/api/v1.0/documents/public/years', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_YEARS),
      })
    })

    await page.route('**/api/v1.0/documents/public/categories*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_CATEGORIES),
      })
    })

    await page.route('**/api/v1.0/projects/public/years', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_YEARS),
      })
    })

    await page.route('**/api/v1.0/research-fields*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 1, code: 'AI', name: 'Trí tuệ nhân tạo' },
          { id: 2, code: 'ROBOTICS', name: 'Robotics' },
        ]),
      })
    })

    await page.route('**/api/v1.0/projects/public*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_PROJECTS),
      })
    })

    await page.route('**/api/v1.0/projects*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_PROJECTS),
      })
    })

    await page.route('**/api/v1.0/documents/public*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_DOCUMENTS),
      })
    })
  })

  test('capture screenshots across viewports for synchronized /tai-lieu and /du-an comparison', async ({ page }) => {
    const viewports = [
      { name: 'desktop-1440', width: 1440, height: 900 },
      { name: 'laptop-1024', width: 1024, height: 768 },
      { name: 'tablet-768', width: 768, height: 1024 },
      { name: 'mobile-375', width: 375, height: 812 },
    ]

    for (const vp of viewports) {
      // 1. Capture Synchronized /tai-lieu
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/tai-lieu')
      await page.waitForSelector('.doc-card')

      await page.screenshot({
        path: `e2e/.artifacts/screenshots/${vp.name}-tai-lieu-synchronized.png`,
        fullPage: true,
      })

      // 2. Capture /du-an Reference
      await page.goto('/du-an')
      await page.waitForSelector('.project-card')

      await page.screenshot({
        path: `e2e/.artifacts/screenshots/${vp.name}-du-an-reference.png`,
        fullPage: true,
      })
    }

    // Capture state with active category tab in /tai-lieu
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/tai-lieu?category=NGHIEN-CUU')
    await page.waitForSelector('.doc-card')
    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-tai-lieu-category-active.png',
      fullPage: true,
    })

    // Capture state with search active in /tai-lieu
    await page.goto('/tai-lieu?q=Kien+truc')
    await page.waitForSelector('.doc-card')
    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-tai-lieu-search-active.png',
      fullPage: true,
    })

    // Capture state with expanded year grid
    await page.goto('/tai-lieu')
    await page.waitForSelector('.project-year-toggle-btn')
    await page.locator('.project-year-toggle-btn').click()
    await page.waitForSelector('.project-years-expanded-box')
    await page.screenshot({
      path: 'e2e/.artifacts/screenshots/desktop-1440-tai-lieu-year-expanded.png',
      fullPage: true,
    })
  })
})
