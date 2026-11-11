import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:3010',
    trace: 'on-first-retry'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  webServer: [
    {
      command: 'npm run dev',
      cwd: '../backend',
      env: {
        PORT: '4010',
        DATABASE_URL: ':memory:',
        CORS_ORIGIN: 'http://127.0.0.1:3010',
        NODE_ENV: 'test'
      },
      url: 'http://127.0.0.1:4010/health',
      reuseExistingServer: false,
      timeout: 30000
    },
    {
      command: 'npx next dev -p 3010',
      cwd: '.',
      env: {
        NEXT_PUBLIC_API_URL: 'http://127.0.0.1:4010/api/v1',
        NEXT_TELEMETRY_DISABLED: '1'
      },
      url: 'http://127.0.0.1:3010',
      reuseExistingServer: false,
      timeout: 60000
    }
  ]
});

