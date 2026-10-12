import type { Page } from "playwright";

/**
 * Smoke repro: home page must show the Uni Companion brand heading.
 * Passes when the local Vite app is up at BASE_URL.
 */
export async function run(page: Page, baseUrl: string): Promise<void> {
  await page.goto(baseUrl, { waitUntil: "domcontentloaded", timeout: 15_000 });
  const heading = page.getByRole("heading", { name: "Uni Companion", level: 1 });
  await heading.waitFor({ state: "visible", timeout: 10_000 });
}
