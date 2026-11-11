// Writes each illustration as a standalone SVG (design/illustrations/svg/<key>.svg).
import fs from 'node:fs'; import path from 'node:path';
const here = path.dirname(new URL(import.meta.url).pathname);
await import(path.join(here, '../illustrations/illustrations.js'));
const { ART } = globalThis.NP_ART;
const out = path.join(here, '../illustrations/svg'); fs.mkdirSync(out, { recursive: true });
for (const k of Object.keys(ART)) {
  const body = ART[k].replace(/\n\s*/g, '');
  fs.writeFileSync(path.join(out, `${k}.svg`), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">${body}</svg>\n`);
}
console.log(Object.keys(ART).join(' '));
