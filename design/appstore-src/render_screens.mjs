import { chromium } from 'playwright';
const base = 'file:///home/claude/app.lrvdlinden.noodkoffer/design/mockups/app.html';
const screens = ['kit', 'scan', 'shop', 'exp', 'plan'];
const devices = { iphone: { w: 390, h: 844, scale: 3 }, ipad: { w: 1032, h: 1376, scale: 2 } };
const browser = await chromium.launch();
for (const lang of ['nl', 'en']) for (const [dev, d] of Object.entries(devices)) {
  const page = await browser.newPage({ viewport: { width: d.w, height: d.h }, deviceScaleFactor: d.scale });
  for (const s of screens) {
    await page.goto(`${base}?s=${s}&lang=${lang}&w=${d.w}&h=${d.h}`);
    await page.waitForTimeout(150);
    await page.screenshot({ path: `screens/${lang}-${dev}-${s}.png` });
  }
  await page.close();
}
await browser.close();
