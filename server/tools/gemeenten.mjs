// Maakt data/gemeenten.json uit het register van overheidsorganisaties (organisaties.overheid.nl, CC0):
// per gemeentecode (gm0200) de naam, het algemene telefoonnummer en de website.
// Gebruik: node tools/gemeenten.mjs exportOO_gemeenten.xml > data/gemeenten.json
import { readFileSync } from "node:fs";

const unesc = (s) => s.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
  .replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").trim();

export function parse(xml) {
  const out = {};
  const blocks = xml.split(/<p:organisatie\s/).slice(1);
  for (const b of blocks) {
    const id = /resourceIdentifierTOOI="[^"]*\/gemeente\/(gm\d{4})"/.exec(b)?.[1];
    if (!id) continue;
    if (/<p:datumOpheffing>|<p:eindDatum>/.test(b.split("<p:organisatie")[0])) continue;
    const naam = unesc(/<p:naam>([\s\S]*?)<\/p:naam>/.exec(b)?.[1] ?? "").replace(/^Gemeente\s+/i, "");
    const contact = /<p:contact>([\s\S]*?)<\/p:contact>/.exec(b)?.[1] ?? "";
    const tels = [...contact.matchAll(/<p:telefoonnummer>\s*<p:nummer>([\s\S]*?)<\/p:nummer>(?:\s*<p:label>([\s\S]*?)<\/p:label>)?/g)]
      .map((m) => ({ nr: unesc(m[1]), label: unesc(m[2] ?? "") }));
    const tel = (tels.find((t) => /algemeen/i.test(t.label)) ?? tels[0])?.nr ?? null;
    const sites = [...contact.matchAll(/<p:internetadres>\s*<p:url>([\s\S]*?)<\/p:url>(?:\s*<p:label>([\s\S]*?)<\/p:label>)?/g)]
      .map((m) => ({ url: unesc(m[1]), label: unesc(m[2] ?? "") }));
    const site = (sites.find((s) => /algemeen/i.test(s.label)) ?? sites[0])?.url ?? null;
    if (!naam) continue;
    out[id] = { naam, telefoon: tel, site };
  }
  return out;
}

if (process.argv[2]) {
  const data = parse(readFileSync(process.argv[2], "utf8"));
  const n = Object.keys(data).length;
  if (n < 300) { console.error(`Te weinig gemeenten (${n}); bestand niet bijgewerkt.`); process.exit(1); }
  const sorted = Object.fromEntries(Object.entries(data).sort(([a], [b]) => a.localeCompare(b)));
  process.stdout.write(JSON.stringify(sorted, null, 0).replace(/\},"/g, '},\n"') + "\n");
  console.error(`${n} gemeenten`);
}
