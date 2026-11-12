// Noodpakket API (Cloudflare Worker + D1): accounts met verplichte e-mailbevestiging, gezinnen met uitnodigingen,
// synchronisatie van het pakket tussen gezinsleden en tellingen voor het beheer.
//
// Een account is nooit verplicht: de apps werken zonder. Alle endpoints spreken JSON.

import {
  amsterdamDay, cleanChange, cleanEmail, cleanLang, cleanName, hashPassword, makeAdminToken, passwordProblem,
  randomToken, rateStep, readAdminToken, sameString, sha256, verifyPassword, wins,
} from "./core.js";
import { buildMail, sendMail } from "./mail.js";

const HOUR = 3600_000;
const DAY = 24 * HOUR;
const CONFIRM_TTL = 48 * HOUR;
const RESET_TTL = 1 * HOUR;
const INVITE_TTL = 14 * DAY;
const MAX_MEMBERS = 10;
const MAX_CHANGES = 500;
const PAGE = 1000;

class HttpError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
const fail = (status, code) => { throw new HttpError(status, code); };

// ---------- HTTP-hulp ----------

function cors(req, env) {
  const origin = req.headers.get("origin");
  const allowed = [env.SITE, "https://www.noodpakket.lrvdlinden.app"];
  if (!origin || !allowed.includes(origin)) return {};
  return {
    "access-control-allow-origin": origin,
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type, authorization",
    "access-control-max-age": "86400",
    vary: "origin",
  };
}

const json = (data, status, extra) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...extra },
});

async function body(req) {
  const text = await req.text();
  if (text.length > 400_000) fail(413, "too_large");
  if (!text) return {};
  try {
    const o = JSON.parse(text);
    return o && typeof o === "object" && !Array.isArray(o) ? o : fail(400, "bad_json");
  } catch (e) { if (e instanceof HttpError) throw e; fail(400, "bad_json"); }
}

const bearer = (req) => (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "").trim();
const clientIP = (req) => req.headers.get("cf-connecting-ip") || "0.0.0.0";

// ---------- teller en limieten ----------

async function count(env, metric, n = 1) {
  await env.DB.prepare("insert into daily (day, metric, n) values (?, ?, ?) on conflict(day, metric) do update set n = n + excluded.n")
    .bind(amsterdamDay(), metric, n).run();
}

async function limit(env, key, windowMs, max) {
  const now = Date.now();
  const row = await env.DB.prepare("select count, window_start from rate where key = ?").bind(key).first();
  const next = rateStep(row, now, windowMs, max);
  await env.DB.prepare("insert into rate (key, count, window_start) values (?, ?, ?) on conflict(key) do update set count = excluded.count, window_start = excluded.window_start")
    .bind(key, next.count, next.window_start).run();
  if (!next.allowed) fail(429, "rate_limited");
}

// ---------- accounts ----------

const publicUser = (u) => ({ id: u.id, email: u.email, name: u.name, lang: u.lang, household_id: u.household_id });

async function newToken(env, userId, kind, ttl) {
  const token = randomToken();
  await env.DB.batch([
    env.DB.prepare("delete from tokens where user_id = ? and kind = ?").bind(userId, kind),
    env.DB.prepare("insert into tokens (token_hash, user_id, kind, expires_at) values (?, ?, ?, ?)")
      .bind(await sha256(token), userId, kind, Date.now() + ttl),
  ]);
  return token;
}

async function useToken(env, token, kind) {
  if (typeof token !== "string" || token.length < 20 || token.length > 100) fail(400, "bad_token");
  const h = await sha256(token);
  const row = await env.DB.prepare("select user_id, expires_at from tokens where token_hash = ? and kind = ?").bind(h, kind).first();
  if (!row || row.expires_at < Date.now()) fail(410, "token_expired");
  await env.DB.prepare("delete from tokens where token_hash = ?").bind(h).run();
  return row.user_id;
}

async function sendConfirm(env, user) {
  const token = await newToken(env, user.id, "confirm", CONFIRM_TTL);
  const link = `${env.SITE}/bevestigen/?t=${encodeURIComponent(token)}${user.lang === "en" ? "&lang=en" : ""}`;
  return sendMail(env, user.email, buildMail("confirm", user.lang, link));
}

async function ensureHousehold(env, user) {
  if (user.household_id) return user.household_id;
  const id = crypto.randomUUID();
  const name = user.name ? (user.lang === "en" ? `${user.name}'s household` : `Gezin van ${user.name}`) : (user.lang === "en" ? "My household" : "Mijn gezin");
  await env.DB.batch([
    env.DB.prepare("insert into households (id, name, owner_id, created_at, seq) values (?, ?, ?, ?, 0)").bind(id, name, user.id, Date.now()),
    env.DB.prepare("update users set household_id = ? where id = ?").bind(id, user.id),
  ]);
  user.household_id = id;
  return id;
}

async function signup(req, env) {
  const b = await body(req);
  if (typeof b.website === "string" && b.website.trim()) { // honingpot: bots vullen dit verborgen veld in
    await count(env, "bot");
    return json({ status: "check_mail" }, 200);
  }
  const email = cleanEmail(b.email);
  if (!email) fail(400, "bad_email");
  const problem = passwordProblem(b.password);
  if (problem) fail(400, problem);
  await limit(env, `signup:${clientIP(req)}`, HOUR, 8);
  const lang = cleanLang(b.lang);
  const existing = await env.DB.prepare("select * from users where email = ?").bind(email).first();
  if (existing) {
    // Zelfde antwoord als bij een nieuw account (geen hint of een adres al bestaat). Niet bevestigd: mail opnieuw.
    if (!existing.confirmed_at) {
      await limit(env, `confirm-mail:${email}`, HOUR, 3).catch(() => null);
      await sendConfirm(env, existing);
    }
    return json({ status: "check_mail" }, 200);
  }
  const { hash, salt } = await hashPassword(b.password);
  const user = { id: crypto.randomUUID(), email, name: cleanName(b.name), lang, household_id: null };
  await env.DB.prepare("insert into users (id, email, pass_hash, pass_salt, name, lang, created_at) values (?, ?, ?, ?, ?, ?, ?)")
    .bind(user.id, email, hash, salt, user.name, lang, Date.now()).run();
  await count(env, "signup");
  const sent = await sendConfirm(env, user);
  return json({ status: "check_mail", mail: sent }, 200);
}

async function confirm(req, env) {
  const b = await body(req);
  const userId = await useToken(env, b.token, "confirm");
  const user = await env.DB.prepare("select * from users where id = ?").bind(userId).first();
  if (!user) fail(410, "token_expired");
  if (!user.confirmed_at) {
    await env.DB.prepare("update users set confirmed_at = ? where id = ?").bind(Date.now(), userId).run();
    await count(env, "confirm");
  }
  await ensureHousehold(env, user);
  return json({ ok: true, email: user.email, lang: user.lang }, 200);
}

async function resend(req, env) {
  const b = await body(req);
  const email = cleanEmail(b.email);
  if (!email) fail(400, "bad_email");
  await limit(env, `resend:${clientIP(req)}`, HOUR, 10);
  await limit(env, `confirm-mail:${email}`, HOUR, 3);
  const user = await env.DB.prepare("select * from users where email = ?").bind(email).first();
  if (user && !user.confirmed_at) await sendConfirm(env, user);
  return json({ status: "check_mail" }, 200);
}

async function login(req, env) {
  const b = await body(req);
  const email = cleanEmail(b.email);
  if (!email || typeof b.password !== "string") fail(401, "bad_credentials");
  await limit(env, `login-ip:${clientIP(req)}`, 10 * 60_000, 30);
  await limit(env, `login:${email}`, 10 * 60_000, 10);
  const user = await env.DB.prepare("select * from users where email = ?").bind(email).first();
  if (!user) { await hashPassword(b.password); fail(401, "bad_credentials"); } // gelijke rekentijd
  if (!(await verifyPassword(b.password, user.pass_hash, user.pass_salt))) fail(401, "bad_credentials");
  if (!user.confirmed_at) fail(403, "not_confirmed");
  await ensureHousehold(env, user);
  const token = "np_" + randomToken();
  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("insert into sessions (token_hash, user_id, created_at, last_used, device) values (?, ?, ?, ?, ?)")
      .bind(await sha256(token), user.id, now, now, cleanName(b.device, 40)),
    env.DB.prepare("update users set last_login = ? where id = ?").bind(now, user.id),
  ]);
  await count(env, "login");
  return json({ token, user: publicUser(user) }, 200);
}

async function forgot(req, env) {
  const b = await body(req);
  const email = cleanEmail(b.email);
  if (!email) fail(400, "bad_email");
  await limit(env, `forgot-ip:${clientIP(req)}`, HOUR, 10);
  await limit(env, `forgot:${email}`, HOUR, 3);
  const user = await env.DB.prepare("select * from users where email = ?").bind(email).first();
  if (user) {
    const token = await newToken(env, user.id, "reset", RESET_TTL);
    const link = `${env.SITE}/account/?reset=${encodeURIComponent(token)}${user.lang === "en" ? "&lang=en" : ""}`;
    await sendMail(env, user.email, buildMail("reset", user.lang, link));
  }
  return json({ status: "check_mail" }, 200);
}

async function reset(req, env) {
  const b = await body(req);
  const problem = passwordProblem(b.password);
  if (problem) fail(400, problem);
  const userId = await useToken(env, b.token, "reset");
  const { hash, salt } = await hashPassword(b.password);
  const now = Date.now();
  // Wie de resetlink uit de mail gebruikt, heeft het adres ook bevestigd.
  await env.DB.batch([
    env.DB.prepare("update users set pass_hash = ?, pass_salt = ?, confirmed_at = coalesce(confirmed_at, ?) where id = ?").bind(hash, salt, now, userId),
    env.DB.prepare("delete from sessions where user_id = ?").bind(userId),
  ]);
  const user = await env.DB.prepare("select * from users where id = ?").bind(userId).first();
  if (user) await ensureHousehold(env, user);
  return json({ ok: true }, 200);
}

/** Ingelogde gebruiker bij het Bearer-token, of 401. */
async function auth(req, env) {
  const token = bearer(req);
  if (!token.startsWith("np_")) fail(401, "unauthorized");
  const h = await sha256(token);
  const row = await env.DB.prepare(
    "select u.*, s.last_used as s_used from sessions s join users u on u.id = s.user_id where s.token_hash = ?",
  ).bind(h).first();
  if (!row) fail(401, "unauthorized");
  if (Date.now() - row.s_used > HOUR) {
    await env.DB.prepare("update sessions set last_used = ? where token_hash = ?").bind(Date.now(), h).run();
  }
  row.token_hash = h;
  await ensureHousehold(env, row);
  return row;
}

async function me(env, user) {
  const [household, members, invites, sent] = await Promise.all([
    env.DB.prepare("select id, name, owner_id, seq from households where id = ?").bind(user.household_id).first(),
    env.DB.prepare("select id, name, email from users where household_id = ? order by created_at").bind(user.household_id).all(),
    env.DB.prepare(
      "select i.id, i.household_id, i.created_at, i.expires_at, h.name as household_name, u.name as from_name, u.email as from_email " +
      "from invites i join households h on h.id = i.household_id left join users u on u.id = i.invited_by " +
      "where i.email = ? and i.expires_at > ? and i.household_id != ? order by i.created_at desc",
    ).bind(user.email, Date.now(), user.household_id).all(),
    env.DB.prepare("select id, email, created_at, expires_at from invites where household_id = ? and expires_at > ? order by created_at desc")
      .bind(user.household_id, Date.now()).all(),
  ]);
  return {
    user: publicUser(user),
    household: household && {
      id: household.id, name: household.name, seq: household.seq, is_owner: household.owner_id === user.id,
      members: members.results.map((m) => ({ id: m.id, name: m.name, email: m.email, owner: m.id === household.owner_id, me: m.id === user.id })),
      invites: sent.results,
    },
    invites: invites.results,
  };
}

async function updateMe(req, env, user) {
  const b = await body(req);
  const name = b.name === undefined ? user.name : cleanName(b.name);
  const lang = b.lang === undefined ? user.lang : cleanLang(b.lang);
  await env.DB.prepare("update users set name = ?, lang = ? where id = ?").bind(name, lang, user.id).run();
  return json(await me(env, { ...user, name, lang }), 200);
}

async function changePassword(req, env, user) {
  const b = await body(req);
  if (!(await verifyPassword(String(b.old ?? ""), user.pass_hash, user.pass_salt))) fail(401, "bad_credentials");
  const problem = passwordProblem(b.password);
  if (problem) fail(400, problem);
  const { hash, salt } = await hashPassword(b.password);
  await env.DB.batch([
    env.DB.prepare("update users set pass_hash = ?, pass_salt = ? where id = ?").bind(hash, salt, user.id),
    env.DB.prepare("delete from sessions where user_id = ? and token_hash != ?").bind(user.id, user.token_hash),
  ]);
  return json({ ok: true }, 200);
}

/** Haalt iemand uit zijn gezin. Laatste lid: gezin en gegevens weg. Eigenaar: het oudste andere lid wordt eigenaar. */
function leaveStatements(env, userId, householdId, ownerId, others) {
  if (!householdId) return [];
  if (others.length === 0) {
    return [
      env.DB.prepare("delete from records where household_id = ?").bind(householdId),
      env.DB.prepare("delete from invites where household_id = ?").bind(householdId),
      env.DB.prepare("delete from households where id = ?").bind(householdId),
    ];
  }
  if (ownerId === userId) return [env.DB.prepare("update households set owner_id = ? where id = ?").bind(others[0].id, householdId)];
  return [];
}

async function otherMembers(env, householdId, userId) {
  return (await env.DB.prepare("select id from users where household_id = ? and id != ? order by created_at").bind(householdId, userId).all()).results;
}

async function deleteMe(req, env, user) {
  const b = await body(req);
  if (!(await verifyPassword(String(b.password ?? ""), user.pass_hash, user.pass_salt))) fail(401, "bad_credentials");
  const h = await env.DB.prepare("select owner_id from households where id = ?").bind(user.household_id).first();
  const others = await otherMembers(env, user.household_id, user.id);
  await env.DB.batch([
    ...leaveStatements(env, user.id, user.household_id, h?.owner_id, others),
    env.DB.prepare("delete from sessions where user_id = ?").bind(user.id),
    env.DB.prepare("delete from tokens where user_id = ?").bind(user.id),
    env.DB.prepare("delete from invites where email = ?").bind(user.email),
    env.DB.prepare("delete from users where id = ?").bind(user.id),
  ]);
  await count(env, "deleted");
  return json({ ok: true }, 200);
}

async function logout(env, user) {
  await env.DB.prepare("delete from sessions where token_hash = ?").bind(user.token_hash).run();
  return json({ ok: true }, 200);
}

// ---------- gezin ----------

async function renameHousehold(req, env, user) {
  const b = await body(req);
  const name = cleanName(b.name, 50);
  if (name.length < 1) fail(400, "bad_name");
  await env.DB.prepare("update households set name = ? where id = ?").bind(name, user.household_id).run();
  return json(await me(env, user), 200);
}

async function invite(req, env, user) {
  const b = await body(req);
  const email = cleanEmail(b.email);
  if (!email) fail(400, "bad_email");
  if (email === user.email) fail(400, "self");
  await limit(env, `invite:${user.id}`, DAY, 20);
  const members = await env.DB.prepare("select email from users where household_id = ?").bind(user.household_id).all();
  if (members.results.some((m) => m.email === email)) fail(409, "already_member");
  if (members.results.length >= MAX_MEMBERS) fail(409, "household_full");
  const household = await env.DB.prepare("select name from households where id = ?").bind(user.household_id).first();
  const now = Date.now();
  await env.DB.prepare(
    "insert into invites (id, household_id, email, invited_by, created_at, expires_at) values (?, ?, ?, ?, ?, ?) " +
    "on conflict(household_id, email) do update set invited_by = excluded.invited_by, created_at = excluded.created_at, expires_at = excluded.expires_at",
  ).bind(crypto.randomUUID(), user.household_id, email, user.id, now, now + INVITE_TTL).run();
  const invitee = await env.DB.prepare("select lang from users where email = ?").bind(email).first();
  const lang = invitee?.lang || user.lang;
  const sent = await sendMail(env, email, buildMail("invite", lang, `${env.SITE}/gezin/${lang === "en" ? "?lang=en" : ""}`, {
    name: user.name || user.email, household: household?.name || (lang === "en" ? "their household" : "hun gezin"),
  }));
  await count(env, "invite");
  return json({ ...(await me(env, user)), mail: sent }, 200);
}

async function cancelInvite(req, env, user) {
  const b = await body(req);
  await env.DB.prepare("delete from invites where id = ? and household_id = ?").bind(String(b.id ?? ""), user.household_id).run();
  return json(await me(env, user), 200);
}

async function acceptInvite(req, env, user) {
  const b = await body(req);
  const inv = await env.DB.prepare("select * from invites where id = ? and email = ? and expires_at > ?").bind(String(b.id ?? ""), user.email, Date.now()).first();
  if (!inv) fail(404, "invite_not_found");
  const target = await env.DB.prepare("select id from households where id = ?").bind(inv.household_id).first();
  if (!target) fail(404, "invite_not_found");
  const n = await env.DB.prepare("select count(*) as n from users where household_id = ?").bind(inv.household_id).first();
  if (n.n >= MAX_MEMBERS) fail(409, "household_full");
  const old = await env.DB.prepare("select owner_id from households where id = ?").bind(user.household_id).first();
  const others = await otherMembers(env, user.household_id, user.id);
  await env.DB.batch([
    ...leaveStatements(env, user.id, user.household_id, old?.owner_id, others),
    env.DB.prepare("update users set household_id = ? where id = ?").bind(inv.household_id, user.id),
    env.DB.prepare("delete from invites where household_id = ? and email = ?").bind(inv.household_id, user.email),
  ]);
  await count(env, "joined");
  return json(await me(env, { ...user, household_id: inv.household_id }), 200);
}

async function declineInvite(req, env, user) {
  const b = await body(req);
  await env.DB.prepare("delete from invites where id = ? and email = ?").bind(String(b.id ?? ""), user.email).run();
  return json(await me(env, user), 200);
}

async function moveToOwnHousehold(env, user, ownerId) {
  const others = await otherMembers(env, user.household_id, user.id);
  if (others.length === 0) fail(409, "alone");
  await env.DB.batch([
    ...leaveStatements(env, user.id, user.household_id, ownerId, others),
    env.DB.prepare("update users set household_id = null where id = ?").bind(user.id),
  ]);
  const fresh = { ...user, household_id: null };
  await ensureHousehold(env, fresh);
  return fresh;
}

async function leave(env, user) {
  const h = await env.DB.prepare("select owner_id from households where id = ?").bind(user.household_id).first();
  const fresh = await moveToOwnHousehold(env, user, h?.owner_id);
  return json(await me(env, fresh), 200);
}

async function removeMember(req, env, user) {
  const b = await body(req);
  const h = await env.DB.prepare("select owner_id from households where id = ?").bind(user.household_id).first();
  if (h?.owner_id !== user.id) fail(403, "not_owner");
  const target = await env.DB.prepare("select * from users where id = ? and household_id = ?").bind(String(b.user_id ?? ""), user.household_id).first();
  if (!target || target.id === user.id) fail(404, "member_not_found");
  await moveToOwnHousehold(env, target, h.owner_id);
  return json(await me(env, user), 200);
}

// ---------- synchronisatie ----------

async function sync(req, env, user) {
  const b = await body(req);
  const since = Math.max(0, Math.floor(Number(b.since) || 0));
  const raw = Array.isArray(b.changes) ? b.changes : [];
  if (raw.length > MAX_CHANGES) fail(413, "too_many_changes");
  const hid = user.household_id;
  const now = Date.now();
  const changes = raw.map((c) => cleanChange(c, now)).filter(Boolean);
  let applied = 0;
  if (changes.length) {
    // Bestaande versies ophalen zodat alleen winnende wijzigingen een volgnummer krijgen.
    const existing = new Map();
    for (let i = 0; i < changes.length; i += 50) {
      const part = changes.slice(i, i + 50);
      const rows = await env.DB.prepare(
        `select kind, id, updated_at, deleted from records where household_id = ? and (${part.map(() => "(kind = ? and id = ?)").join(" or ")})`,
      ).bind(hid, ...part.flatMap((c) => [c.kind, c.id])).all();
      for (const r of rows.results) existing.set(`${r.kind}/${r.id}`, { updated_at: r.updated_at, deleted: !!r.deleted });
    }
    const stmts = [];
    for (const c of changes) {
      if (!wins(c, existing.get(`${c.kind}/${c.id}`))) continue;
      applied++;
      stmts.push(env.DB.prepare("update households set seq = seq + 1 where id = ?").bind(hid));
      // De where-clausule bewaakt 'laatste wijziging wint' ook als twee apparaten tegelijk schrijven.
      stmts.push(env.DB.prepare(
        "insert into records (household_id, kind, id, data, updated_at, deleted, seq) values (?, ?, ?, ?, ?, ?, (select seq from households where id = ?)) " +
        "on conflict(household_id, kind, id) do update set data = excluded.data, updated_at = excluded.updated_at, deleted = excluded.deleted, seq = excluded.seq " +
        "where excluded.updated_at > records.updated_at or (excluded.updated_at = records.updated_at and excluded.deleted > records.deleted)",
      ).bind(hid, c.kind, c.id, c.data, c.updated_at, c.deleted ? 1 : 0, hid));
    }
    for (let i = 0; i < stmts.length; i += 100) await env.DB.batch(stmts.slice(i, i + 100));
  }
  const rows = await env.DB.prepare(
    "select kind, id, data, updated_at, deleted, seq from records where household_id = ? and seq > ? order by seq limit ?",
  ).bind(hid, since, PAGE + 1).all();
  const more = rows.results.length > PAGE;
  const list = rows.results.slice(0, PAGE);
  const h = await env.DB.prepare("select seq from households where id = ?").bind(hid).first();
  if (raw.length) await count(env, "sync");
  return json({
    household_id: hid,
    seq: more ? list[list.length - 1].seq : (h?.seq ?? 0),
    more,
    applied,
    rejected: raw.length - changes.length,
    changes: list.map((r) => ({
      kind: r.kind, id: r.id, updated_at: r.updated_at, deleted: !!r.deleted, seq: r.seq,
      data: r.deleted ? null : JSON.parse(r.data),
    })),
  }, 200);
}

// ---------- beheer ----------

async function adminLogin(req, env) {
  if (!env.BEHEER_USER || !env.BEHEER_PASSWORD || !env.ADMIN_SECRET) fail(503, "not_configured");
  const b = await body(req);
  await limit(env, `admin:${clientIP(req)}`, 15 * 60_000, 10);
  if (!sameString(String(b.user ?? ""), env.BEHEER_USER) || !sameString(String(b.password ?? ""), env.BEHEER_PASSWORD)) fail(401, "unauthorized");
  return json(await makeAdminToken(env.ADMIN_SECRET), 200);
}

async function adminStats(req, env) {
  if (!(await readAdminToken(env.ADMIN_SECRET, bearer(req)))) fail(401, "unauthorized");
  const q = (sql, ...a) => env.DB.prepare(sql).bind(...a).first();
  const since = amsterdamDay(new Date(Date.now() - 30 * DAY));
  const [accounts, confirmed, active30, households, shared, invites, records, days] = await Promise.all([
    q("select count(*) as n from users"),
    q("select count(*) as n from users where confirmed_at is not null"),
    q("select count(*) as n from users where last_login > ?", Date.now() - 30 * DAY),
    q("select count(*) as n from households"),
    q("select count(*) as households, coalesce(sum(n), 0) as members from (select household_id, count(*) as n from users where household_id is not null group by household_id having n > 1)"),
    q("select count(*) as n from invites where expires_at > ?", Date.now()),
    q("select count(*) as n from records where deleted = 0"),
    env.DB.prepare("select day, metric, n from daily where day >= ? order by day").bind(since).all(),
  ]);
  const byDay = {};
  for (const r of days.results) (byDay[r.day] ??= { day: r.day })[r.metric] = r.n;
  return json({
    accounts: accounts.n,
    confirmed: confirmed.n,
    unconfirmed: accounts.n - confirmed.n,
    active_30d: active30.n,
    households: households.n,
    shared_households: shared.households,
    shared_members: shared.members,
    open_invites: invites.n,
    records: records.n,
    days: Object.values(byDay),
  }, 200);
}

// ---------- onderhoud (cron) ----------

export async function cleanup(env, now = Date.now()) {
  const stale = await env.DB.prepare("select id from users where confirmed_at is null and created_at < ?").bind(now - 7 * DAY).all();
  const stmts = [];
  for (const u of stale.results) {
    stmts.push(env.DB.prepare("delete from tokens where user_id = ?").bind(u.id));
    stmts.push(env.DB.prepare("delete from records where household_id in (select id from households where owner_id = ? and id not in (select household_id from users where id != ? and household_id is not null))").bind(u.id, u.id));
    stmts.push(env.DB.prepare("delete from households where owner_id = ? and id not in (select household_id from users where id != ? and household_id is not null)").bind(u.id, u.id));
    stmts.push(env.DB.prepare("delete from users where id = ?").bind(u.id));
  }
  stmts.push(env.DB.prepare("delete from tokens where expires_at < ?").bind(now));
  stmts.push(env.DB.prepare("delete from invites where expires_at < ?").bind(now));
  stmts.push(env.DB.prepare("delete from sessions where last_used < ?").bind(now - 365 * DAY));
  stmts.push(env.DB.prepare("delete from rate where window_start < ?").bind(now - 2 * DAY));
  stmts.push(env.DB.prepare("delete from daily where day < ?").bind(amsterdamDay(new Date(now - 400 * DAY))));
  for (let i = 0; i < stmts.length; i += 100) await env.DB.batch(stmts.slice(i, i + 100));
  return { removed_unconfirmed: stale.results.length };
}

// ---------- router ----------

const routes = {
  "POST /auth/signup": (r, e) => signup(r, e),
  "POST /auth/confirm": (r, e) => confirm(r, e),
  "POST /auth/resend": (r, e) => resend(r, e),
  "POST /auth/login": (r, e) => login(r, e),
  "POST /auth/forgot": (r, e) => forgot(r, e),
  "POST /auth/reset": (r, e) => reset(r, e),
  "POST /auth/logout": async (r, e) => logout(e, await auth(r, e)),
  "GET /me": async (r, e) => json(await me(e, await auth(r, e)), 200),
  "POST /me": async (r, e) => updateMe(r, e, await auth(r, e)),
  "POST /me/password": async (r, e) => changePassword(r, e, await auth(r, e)),
  "POST /me/delete": async (r, e) => deleteMe(r, e, await auth(r, e)),
  "POST /household": async (r, e) => renameHousehold(r, e, await auth(r, e)),
  "POST /household/invite": async (r, e) => invite(r, e, await auth(r, e)),
  "POST /household/invite/cancel": async (r, e) => cancelInvite(r, e, await auth(r, e)),
  "POST /household/leave": async (r, e) => leave(e, await auth(r, e)),
  "POST /household/remove": async (r, e) => removeMember(r, e, await auth(r, e)),
  "POST /invite/accept": async (r, e) => acceptInvite(r, e, await auth(r, e)),
  "POST /invite/decline": async (r, e) => declineInvite(r, e, await auth(r, e)),
  "POST /sync": async (r, e) => sync(r, e, await auth(r, e)),
  "POST /admin/login": (r, e) => adminLogin(r, e),
  "GET /admin/stats": (r, e) => adminStats(r, e),
};

export default {
  async fetch(req, env) {
    const c = cors(req, env);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: c });
    const path = new URL(req.url).pathname.replace(/\/+$/, "") || "/";
    if (req.method === "GET" && (path === "/" || path === "/health")) {
      return json({ ok: true, service: "noodpakket-api", mail: !!(env.SMTP_HOST && env.SMTP_USER && env.SMTP_PASSWORD) }, 200, c);
    }
    const handler = routes[`${req.method} ${path}`];
    if (!handler) return json({ error: "not_found" }, 404, c);
    try {
      const res = await handler(req, env);
      for (const [k, v] of Object.entries(c)) res.headers.set(k, v);
      return res;
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.code }, e.status, c);
      console.error(path, e?.stack || e);
      return json({ error: "server_error" }, 500, c);
    }
  },

  async scheduled(_event, env, ctx) {
    ctx.waitUntil(cleanup(env).then((r) => console.log("Onderhoud:", JSON.stringify(r))));
  },
};
