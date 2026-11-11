// Composes the App Store screenshots and promo images from the rendered app screens.
import { chromium } from 'playwright';
import fs from 'fs';
const here = 'file:///home/claude/app.lrvdlinden.noodkoffer/design/appstore-src/';
const out = '/home/claude/app.lrvdlinden.noodkoffer/AppStore/';
const T = {
  nl: [
    ['kit', 'Je noodpakket, altijd compleet', 'Volgens het 72-uursadvies van de overheid'],
    ['scan', 'Scan het. De app vult de rest in.', 'Product, hoeveelheid en datum in één keer'],
    ['shop', 'Weet wat je nog mist', 'Je boodschappenlijst maakt zichzelf'],
    ['exp', 'Nooit meer over datum', 'Herinneringen 30 en 7 dagen vooraf'],
    ['plan', 'Een noodplan, ook op papier', 'Plus wat te doen bij stroomuitval en NL-Alert'],
  ],
  en: [
    ['kit', 'Your emergency kit, always complete', 'Based on the Dutch 72-hour advice'],
    ['scan', 'Scan it. The app fills in the rest.', 'Product, amount and date in one go'],
    ['shop', 'Know what you still need', 'Your shopping list writes itself'],
    ['exp', 'Never past the date again', 'Reminders 30 and 7 days ahead'],
    ['plan', 'An emergency plan, on paper too', 'Plus what to do in a power cut'],
  ],
};
const MK = {
  nl: { t: 'Je noodpakket, altijd compleet', s: 'Scan je spullen, zie wat ontbreekt en mis nooit meer een houdbaarheidsdatum.', chips: 'Barcode scannen|Boodschappenlijst|Noodplan als PDF' },
  en: { t: 'Your emergency kit, always complete', s: 'Scan your supplies, see what is missing and never miss an expiry date again.', chips: 'Barcode scanning|Shopping list|Emergency plan as PDF' },
};
const locales = { nl: 'nl-NL', en: 'en-US' };
const sets = [['iphone-6.3', 'iphone', 1206, 2622], ['iphone-6.1', 'iphone', 1179, 2556], ['ipad-13', 'ipad', 2064, 2752]];
const marketing = [['header', 5244, 2950], ['header', 3840, 1646], ['search', 5244, 2950], ['search', 3840, 2560], ['search', 1920, 1280]];
const browser = await chromium.launch();
for (const [lang, loc] of Object.entries(locales)) {
  for (const [folder, kind, W, H] of sets) {
    const dir = `${out}screenshots/${loc}/${folder}/`; fs.mkdirSync(dir, { recursive: true });
    const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
    for (const [i, [s, t, sub]] of T[lang].entries()) {
      const img = `screens/${lang}-${kind === 'iphone' ? 'iphone' : 'ipad'}-${s}.png`;
      await page.goto(`${here}frame.html?kind=${kind}&W=${W}&H=${H}&img=${encodeURIComponent(img)}&t=${encodeURIComponent(t)}&s=${encodeURIComponent(sub)}`);
      await page.waitForTimeout(250);
      await page.screenshot({ path: `${dir}0${i + 1}_${s}_${W}x${H}.png` });
    }
    await page.close();
  }
  const dir = `${out}marketing/${loc}/`; fs.mkdirSync(dir, { recursive: true });
  for (const [name, W, H] of marketing) {
    const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
    const imgs = ['shop', 'kit', 'scan'].map(s => `screens/${lang}-iphone-${s}.png`).join(',');
    const m = MK[lang];
    await page.goto(`${here}frame.html?kind=mk&W=${W}&H=${H}&imgs=${encodeURIComponent(imgs)}&t=${encodeURIComponent(m.t)}&s=${encodeURIComponent(m.s)}&chips=${encodeURIComponent(m.chips)}`);
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${dir}${name}_${W}x${H}.png` });
    await page.close();
  }
}
await browser.close();
