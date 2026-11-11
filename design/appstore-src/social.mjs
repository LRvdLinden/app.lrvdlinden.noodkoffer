// Renders og:images (web/og) and social banners (social/noodpakket-<date>/nl) from banner.html.
import { chromium } from 'playwright';
import path from 'node:path';
import fs from 'node:fs';
const here = path.dirname(new URL(import.meta.url).pathname);
const repo = path.resolve(here, '../..');
const date = process.argv[2] || '2026-11-11';
const T = 'Je noodpakket,|altijd compleet', S = 'Scan je spullen, zie wat ontbreekt en mis nooit meer een houdbaarheidsdatum.';
const IM = 'nl-iphone-kit.png,nl-iphone-scan.png,nl-iphone-shop.png';
const jobs = [
  // og:images
  { out: 'web/og/home-nl.png', W: 1200, H: 630, t: 'Scan het. Klaar.|Je noodpakket, altijd compleet.', s: 'Checklist voor 72 uur, houdbaarheid, boodschappenlijst en noodplan.', imgs: 'nl-iphone-kit.png,nl-iphone-scan.png' },
  { out: 'web/og/support-nl.png', W: 1200, H: 630, t: 'Hulp bij|Noodpakket', s: 'De Android-app installeren, scannen en antwoorden op veelgestelde vragen.', imgs: 'nl-iphone-scan.png,nl-iphone-shop.png' },
  { out: 'web/og/privacy-nl.png', W: 1200, H: 630, t: 'Je gegevens|blijven van jou', s: 'Alles staat op je eigen toestel en in je eigen iCloud.', chips: 'Geen account,Geen reclame,Geen tracking', art: 'shield', cta: '0' },
  // social
  { out: `social/noodpakket-${date}/nl/instagram-1080x1080.png`, W: 1080, H: 1080, t: 'Je noodpakket,|altijd compleet', s: 'Scan je spullen en de app vult de rest in.', imgs: IM },
  { out: `social/noodpakket-${date}/nl/instagram-1080x1350.png`, W: 1080, H: 1350, t: T, s: S, imgs: IM },
  { out: `social/noodpakket-${date}/nl/story-1080x1920.png`, W: 1080, H: 1920, t: T, s: S, chips: 'Barcode scannen,Houdbaarheid,Noodplan als PDF', imgs: IM, safeY: 250 },
  { out: `social/noodpakket-${date}/nl/linkedin-1200x627.png`, W: 1200, H: 627, t: T, s: S, imgs: 'nl-iphone-kit.png,nl-iphone-exp.png' },
  { out: `social/noodpakket-${date}/nl/x-1600x900.png`, W: 1600, H: 900, t: T, s: S, chips: 'Barcode scannen,Boodschappenlijst,Noodplan als PDF', imgs: 'nl-iphone-kit.png,nl-iphone-scan.png' },
  { out: `social/noodpakket-${date}/nl/facebook-cover-1640x624.png`, W: 1640, H: 624, t: T, s: 'Scan je spullen en zie wat er ontbreekt.', imgs: 'nl-iphone-kit.png', safeX: 275 },
  { out: `social/noodpakket-${date}/nl/linkedin-cover-1584x396.png`, W: 1584, H: 396, t: T, s: 'Scan je spullen en zie wat er ontbreekt.', imgs: 'nl-iphone-kit.png,nl-iphone-scan.png', side: 'right' },
  { out: `social/noodpakket-${date}/nl/homey-forum-1200x400.png`, W: 1200, H: 400, t: T, s: 'Scan je spullen en zie wat er ontbreekt.', imgs: 'nl-iphone-kit.png,nl-iphone-scan.png' },
  { out: `social/noodpakket-${date}/nl/og-1200x630.png`, W: 1200, H: 630, t: T, s: S, imgs: 'nl-iphone-kit.png,nl-iphone-scan.png' },
];
const only = process.argv[3];
const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
for (const j of jobs) {
  if (only && !j.out.includes(only)) continue;
  const p = new URLSearchParams(Object.fromEntries(Object.entries(j).filter(([k]) => k !== 'out').map(([k, v]) => [k, String(v)])));
  await page.setViewportSize({ width: j.W, height: j.H });
  await page.goto('file://' + path.join(here, 'banner.html') + '?' + p.toString());
  await page.waitForSelector('body[data-ready="1"]');
  await page.waitForTimeout(150);
  const out = path.join(repo, j.out);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  await page.screenshot({ path: out });
  console.log(j.out);
}
await browser.close();
