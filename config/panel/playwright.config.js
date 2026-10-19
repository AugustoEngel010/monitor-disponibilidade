import {defineConfig} from '@playwright/test';

export default defineConfig({
  testDir: '.',
  globalSetup: './ready.js',
  workers: 1,
  retries: 0,
  timeout: 45000,
  expect: {timeout: 10000},
  outputDir: '/reports/results',
  reporter: [['list'], ['html', {outputFolder: '/reports/html', open: 'never'}],
    ['junit', {outputFile: '/reports/junit.xml'}]],
  use: {
    baseURL: process.env.PANEL_URL || 'http://panel-app:8080',
    httpCredentials: {username: 'panel-test-operator', password: 'panel-test-only'},
    viewport: {width: 1440, height: 900},
    locale: 'pt-BR', timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure', screenshot: 'only-on-failure'
  }
});
