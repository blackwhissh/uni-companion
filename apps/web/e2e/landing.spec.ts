import { expect, test } from '@playwright/test'

test('landing page shows the product and a path to log in', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Uni Companion' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Log in' }).first()).toBeVisible()
  await expect(page.getByText('Universität Siegen')).toBeVisible()
})
