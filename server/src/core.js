// Pure helpers (no D1, no network) so they can be tested with `node --test`.

const enc = new TextEncoder();
export const PBKDF2_ITERATIONS = 100_000; // maximum that Cloudflare Workers allow

export const b64 = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes)));
export const unb64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
export const b64url = (bytes) => b64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

export function randomToken(bytes = 32) {
  return b64url(crypto.getRandomValues(new Uint8Array(bytes)));
}

export async function sha256(text) {
  return b64url(await crypto.subtle.digest("SHA-256", enc.encode(text)));
}

export async function hashPassword(password, saltB64 = b64(crypto.getRandomValues(new Uint8Array(16))), iterations = PBKDF2_ITERATIONS) {
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: unb64(saltB64), iterations }, key, 256);
  return { hash: b64(bits), salt: saltB64 };
}

/** Constant-time comparison of two strings. */
export function sameString(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const x = enc.encode(a), y = enc.encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

export async function verifyPassword(password, hash, salt) {
  const h = await hashPassword(password, salt);
  return sameString(h.hash, hash);
}

// ---- validation ----

export function cleanEmail(v) {
  if (typeof v !== "string") return null;
  const e = v.trim().toLowerCase();
  if (e.length < 6 || e.length > 254) return null;
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e) ? e : null;
}

/** Password rules: 8–128 characters, at least one letter and one digit. Returns an error code or null. */
export function passwordProblem(p) {
  if (typeof p !== "string" || p.length < 8) return "password_short";
  if (p.length > 128) return "password_long";
  if (!/[A-Za-z]/.test(p) || !/[0-9]/.test(p)) return "password_weak";
  return null;
}

export function cleanName(v, max = 60) {
  return typeof v === "string" ? v.replace(/[\u0000-\u001f<>]/g, "").trim().slice(0, max) : "";
}

export const cleanLang = (v) => (v === "en" ? "en" : "nl");

// ---- sync ----

export const KINDS = ["item", "household", "plan"];
const MAX_DATA = 8000;

/** Checks one change from an app. Returns {kind, id, data, updated_at, deleted} or null when invalid. */
export function cleanChange(c, now = Date.now()) {
  if (!c || typeof c !== "object") return null;
  if (!KINDS.includes(c.kind)) return null;
  if (typeof c.id !== "string" || !/^[A-Za-z0-9:_-]{1,80}$/.test(c.id)) return null;
  if (c.kind !== "item" && c.id !== c.kind) return null;
  const updated = Number(c.updated_at);
  if (!Number.isFinite(updated) || updated <= 0) return null;
  const deleted = c.deleted === true;
  let data = "{}";
  if (!deleted) {
    if (!c.data || typeof c.data !== "object" || Array.isArray(c.data)) return null;
    data = JSON.stringify(c.data);
    if (data.length > MAX_DATA) return null;
  }
  // a clock far in the future must not win every conflict forever
  return { kind: c.kind, id: c.id, data, updated_at: Math.min(Math.round(updated), now + 5 * 60_000), deleted };
}

/** Last write wins: does an incoming change replace what the server has? */
export function wins(incoming, existing) {
  if (!existing) return true;
  if (incoming.updated_at !== existing.updated_at) return incoming.updated_at > existing.updated_at;
  return incoming.deleted && !existing.deleted; // tie: deleting wins
}

// ---- rate limiting ----

/** Fixed window: returns {allowed, count, window_start} for the next state. */
export function rateStep(state, now, windowMs, max) {
  if (!state || now - state.window_start >= windowMs) return { allowed: true, count: 1, window_start: now };
  return { allowed: state.count < max, count: state.count + 1, window_start: state.window_start };
}

// ---- admin token (beheer) ----

async function hmac(secret, text) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return b64url(await crypto.subtle.sign("HMAC", key, enc.encode(text)));
}

export async function makeAdminToken(secret, now = Date.now(), ttlMs = 12 * 3600_000) {
  const exp = Math.floor((now + ttlMs) / 1000);
  const payload = b64url(enc.encode(JSON.stringify({ s: "noodpakket-admin", e: exp })));
  return { token: `${payload}.${await hmac(secret, payload)}`, expires: exp };
}

export async function readAdminToken(secret, token, now = Date.now()) {
  if (!secret || typeof token !== "string" || token.length > 400) return false;
  const [p, sig] = token.split(".");
  if (!p || !sig || !sameString(sig, await hmac(secret, p))) return false;
  try {
    const o = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(p.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0))));
    return o.s === "noodpakket-admin" && o.e * 1000 > now;
  } catch { return false; }
}

export const amsterdamDay = (d = new Date()) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Amsterdam", year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
