// Belangrijke nummers voor een postcode: gemeente, rampenzender (dichtstbijzijnde FM-zender), waterbedrijf en wijkagent.
// De postcode gaat alleen naar PDOK (Locatieserver van de overheid) om gemeente, provincie en plaats te vinden; de
// server bewaart hem niet (alleen een tijdelijke cache van het PDOK-antwoord).
import gemeenten from "../data/gemeenten.json" with { type: "json" };
import { WATER, WATER_PER_GEMEENTE, WATER_PER_PROVINCIE, ZENDERS, ZUID_HOLLAND_WEST } from "./lokaal-data.js";

export function cleanPostcode(v) {
  const p = String(v ?? "").toUpperCase().replace(/\s+/g, "");
  return /^[1-9][0-9]{3}[A-Z]{2}$/.test(p) && !/^\d{4}(SA|SD|SS)$/.test(p) ? p : null;
}

const norm = (s) => String(s ?? "").toLowerCase().trim();

/** Rampenzender bij provincie en gemeente; FM-zenders gesorteerd op afstand (dichtstbijzijnde eerst). */
export function zenderVoor(provincie, gemeente, lat, lon) {
  const pv = norm(provincie).replace(/^fryslân$/, "friesland").replace(/^fryslan$/, "friesland");
  const key = pv === "zuid-holland" ? (ZUID_HOLLAND_WEST.has(norm(gemeente)) ? "west" : "rijnmond") : pv;
  const z = ZENDERS[key];
  if (!z) return null;
  const d = (f) => (Number.isFinite(lat) && Number.isFinite(lon)) ? Math.hypot(f.lat - lat, (f.lon - lon) * Math.cos(lat * Math.PI / 180)) : 0;
  const fm = [...z.fm].sort((a, b) => d(a) - d(b)).map(({ mhz, gebied }) => ({ mhz, gebied }));
  return { omroep: z.omroep, site: z.site, dab: z.dab, fm };
}

export function waterVoor(provincie, gemeente) {
  const pv = norm(provincie).replace(/^fryslân$/, "friesland");
  const opties = [...(WATER_PER_PROVINCIE[pv] ?? Object.keys(WATER))];
  const voorkeur = WATER_PER_GEMEENTE[norm(gemeente)];
  if (voorkeur) { opties.splice(opties.indexOf(voorkeur) >>> 0, opties.includes(voorkeur) ? 1 : 0); opties.unshift(voorkeur); }
  return [...new Set(opties)].map((id) => ({ id, ...WATER[id] }));
}

export function gemeenteVoor(code, naam) {
  const id = "gm" + String(code ?? "").replace(/^gm/i, "").padStart(4, "0");
  const g = gemeenten[id];
  return { code: id, naam: g?.naam ?? naam ?? "", telefoon: g?.telefoon ?? null, site: g?.site ?? null };
}

async function pdok(postcode, ctx) {
  const url = `https://api.pdok.nl/bzk/locatieserver/search/v3_1/free?q=${postcode}&fq=type:postcode&rows=1` +
    "&fl=postcode,woonplaatsnaam,gemeentecode,gemeentenaam,provincienaam,centroide_ll";
  const cache = globalThis.caches?.default;
  const key = new Request(url);
  let res = cache && (await cache.match(key));
  if (!res) {
    res = await fetch(url, { headers: { "user-agent": "noodpakket-api" } });
    if (!res.ok) return null;
    res = new Response(await res.text(), { headers: { "content-type": "application/json", "cache-control": "max-age=2592000" } });
    if (cache) ctx?.waitUntil(cache.put(key, res.clone()));
  }
  const doc = (await res.json())?.response?.docs?.[0];
  return doc ?? null;
}

export async function lokaal(postcodeRaw, ctx, lookup = pdok) {
  const postcode = cleanPostcode(postcodeRaw);
  if (!postcode) return { ok: false, status: 400, error: "bad_postcode" };
  let doc;
  try { doc = await lookup(postcode, ctx); } catch { return { ok: false, status: 502, error: "pdok_unreachable" }; }
  if (!doc) return { ok: false, status: 404, error: "unknown_postcode" };
  const m = /POINT\(([-\d.]+) ([-\d.]+)\)/.exec(doc.centroide_ll ?? "");
  const lon = m ? Number(m[1]) : NaN, lat = m ? Number(m[2]) : NaN;
  const gemeente = gemeenteVoor(doc.gemeentecode, doc.gemeentenaam);
  return {
    ok: true,
    postcode: postcode.slice(0, 4) + " " + postcode.slice(4),
    woonplaats: doc.woonplaatsnaam ?? "",
    provincie: doc.provincienaam ?? "",
    gemeente,
    noodzender: zenderVoor(doc.provincienaam, gemeente.naam || doc.gemeentenaam, lat, lon),
    water: waterVoor(doc.provincienaam, gemeente.naam || doc.gemeentenaam),
    wijkagent: "https://www.politie.nl/mijn-buurt/wijkagenten",
    bronnen: {
      gemeente: "organisaties.overheid.nl",
      noodzender: "denkvooruit.nl en de regionale omroep",
      water: "website van het waterbedrijf",
    },
  };
}
