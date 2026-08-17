import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173'
const apiBaseURL = (process.env.E2E_API_BASE_URL ?? 'http://127.0.0.1:8080/api/v1.0').replace(/\/+$/, '')
const startFrontend = process.env.E2E_START_FRONTEND !== 'false'
const browserExecutablePath = process.env.E2E_BROWSER_EXECUTABLE_PATH?.trim()
const inheritedEnv = Object.fromEntries(
  Object.entries(process.env).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
)

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.artifacts/test-results',
  globalSetup: './e2e/global.setup.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'e2e/.artifacts/report', open: 'never' }],
  ],
  timeout: 45_000,
  expect: { timeout: 10_000 },
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...(browserExecutablePath ? { launchOptions: { executablePath: browserExecutablePath } } : {}),
  },
  webServer: startFrontend
    ? {
        command: 'npm run dev -- --host 127.0.0.1 --port 4173',
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
        env: {
          ...inheritedEnv,
          VITE_API_BASE_URL: apiBaseURL,
        },
      }
    : undefined,
})
