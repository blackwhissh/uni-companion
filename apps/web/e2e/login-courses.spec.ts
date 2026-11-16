import { expect, test } from '@playwright/test'

test('student can log in and open the courses workspace', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()

  await page.getByLabel('Username').fill('student1')
  await page.getByLabel('Password').fill('student1')
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page).toHaveURL(/\/courses/)
  await expect(page.getByRole('heading', { name: 'Your courses' })).toBeVisible()
  await expect(page.getByText('Study workspace')).toBeVisible()
})

test('bad credentials stay on login with an error', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Username').fill('student1')
  await page.getByLabel('Password').fill('wrong-password')
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('alert')).toBeVisible()
})
