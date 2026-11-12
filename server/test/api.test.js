import test from "node:test";
import assert from "node:assert/strict";
import worker, { cleanup } from "../src/index.js";
import { makeD1 } from "./d1shim.js";
import { buildMail } from "../src/mail.js";
import { cleanChange, makeAdminToken, passwordProblem, rateStep, readAdminToken, wins } from "../src/core.js";

function setup() {
  const mails = [];
  const env = {
    DB: makeD1(), SITE: "https://noodpakket.lrvdlinden.app",
    BEHEER_USER: "beheer", BEHEER_PASSWORD: "geheim-wachtwoord", ADMIN_SECRET: "s".repeat(40),
    __mail: (to, m) => mails.push({ to, ...m }),
  };
  let ip = 1;
  const call = async (method, path, body, token, headers = {}) => {
    const res = await worker.fetch(new Request("https://api.test" + path, {
      method, body: body ? JSON.stringify(body) : undefined,
      headers: { "content-type": "application/json", "cf-connecting-ip": `10.0.0.${ip}`, ...(token ? { authorization: "Bearer " + token } : {}), ...headers },
    }), env);
    return { status: res.status, body: await res.json().catch(() => null), headers: res.headers };
  };
  const linkToken = (mail, param) => new URL(mail.text.match(/https:\/\/\S+/)[0]).searchParams.get(param);
  async function account(email, name = "") {
    await call("POST", "/auth/signup", { email, password: "Wachtwoord1", name });
    const t = linkToken(mails.filter((m) => m.to === email).at(-1), "t");
    assert.equal((await call("POST", "/auth/confirm", { token: t })).status, 200);
    const r = await call("POST", "/auth/login", { email, password: "Wachtwoord1" });
    assert.equal(r.status, 200);
    return r.body.token;
  }
  return { env, mails, call, linkToken, account, nextIP: () => ip++ };
}

test("core: wachtwoordregels, LWW, rate, admin-token", async () => {
  assert.equal(passwordProblem("kort1"), "password_short");
  assert.equal(passwordProblem("alleenletters"), "password_weak");
  assert.equal(passwordProblem("Goedgenoeg1"), null);
  assert.equal(wins({ updated_at: 2, deleted: false }, { updated_at: 1, deleted: false }), true);
  assert.equal(wins({ updated_at: 1, deleted: false }, { updated_at: 2, deleted: false }), false);
  assert.equal(wins({ updated_at: 2, deleted: true }, { updated_at: 2, deleted: false }), true);
  assert.equal(cleanChange({ kind: "plan", id: "x", updated_at: 1, data: {} }), null);
  assert.equal(cleanChange({ kind: "item", id: "a b", updated_at: 1, data: {} }), null);
  const far = cleanChange({ kind: "item", id: "cat:water", updated_at: 9e15, data: { q: 1 } }, 1000);
  assert.equal(far.updated_at, 1000 + 300000);
  let s = null;
  for (let i = 0; i < 3; i++) { s = rateStep(s, 10, 100, 3); assert.ok(s.allowed); }
  assert.equal(rateStep(s, 20, 100, 3).allowed, false);
  assert.equal(rateStep(s, 200, 100, 3).allowed, true);
  const { token } = await makeAdminToken("k".repeat(32), 0);
  assert.equal(await readAdminToken("k".repeat(32), token, 1000), true);
  assert.equal(await readAdminToken("x".repeat(32), token, 1000), false);
  assert.equal(await readAdminToken("k".repeat(32), token, 13 * 3600_000), false);
});

test("mail: NL en EN, link en escaping", () => {
  const m = buildMail("invite", "nl", "https://x/gezin/", { name: "<Eva>", household: "Gezin de Vries" });
  assert.match(m.subject, /Eva/);
  assert.ok(!m.html.includes("<Eva>"));
  assert.ok(m.html.includes("&lt;Eva&gt;"));
  assert.match(buildMail("confirm", "en", "https://x").subject, /Confirm/);
});

test("account: bevestigen verplicht, honingpot, geen hint bij bestaand adres", async () => {
  const { call, mails, linkToken, env } = setup();
  const bot = await call("POST", "/auth/signup", { email: "bot@x.nl", password: "Wachtwoord1", website: "spam" });
  assert.equal(bot.status, 200);
  assert.equal(mails.length, 0);

  assert.equal((await call("POST", "/auth/signup", { email: "nee", password: "Wachtwoord1" })).status, 400);
  assert.equal((await call("POST", "/auth/signup", { email: "a@b.nl", password: "kort" })).body.error, "password_short");

  const r = await call("POST", "/auth/signup", { email: "Eva@Voorbeeld.nl", password: "Wachtwoord1", name: "Eva" });
  assert.equal(r.status, 200);
  assert.equal(mails.at(-1).to, "eva@voorbeeld.nl");
  assert.equal((await call("POST", "/auth/login", { email: "eva@voorbeeld.nl", password: "Wachtwoord1" })).body.error, "not_confirmed");
  assert.equal((await call("POST", "/auth/login", { email: "eva@voorbeeld.nl", password: "Fout12345" })).status, 401);

  const again = await call("POST", "/auth/signup", { email: "eva@voorbeeld.nl", password: "AnderWachtwoord2" });
  assert.deepEqual(again.body, { status: "check_mail" });
  assert.equal(mails.length, 2); // bevestigingsmail opnieuw, wachtwoord niet overschreven

  const t = linkToken(mails.at(-1), "t");
  assert.equal((await call("POST", "/auth/confirm", { token: t })).status, 200);
  assert.equal((await call("POST", "/auth/confirm", { token: t })).status, 410);
  const login = await call("POST", "/auth/login", { email: "eva@voorbeeld.nl", password: "Wachtwoord1" });
  assert.equal(login.status, 200);
  assert.ok(login.body.token.startsWith("np_"));
  const meRes = await call("GET", "/me", null, login.body.token);
  assert.equal(meRes.body.household.name, "Gezin van Eva");
  assert.equal(meRes.body.household.members.length, 1);
  const stored = env.DB.raw.prepare("select token_hash from sessions").get();
  assert.notEqual(stored.token_hash, login.body.token);
});

test("wachtwoord vergeten: reset logt overal uit", async () => {
  const { call, mails, linkToken, account } = setup();
  const tok = await account("jan@x.nl", "Jan");
  await call("POST", "/auth/forgot", { email: "jan@x.nl" });
  await call("POST", "/auth/forgot", { email: "onbekend@x.nl" });
  const reset = linkToken(mails.at(-1), "reset");
  assert.equal((await call("POST", "/auth/reset", { token: reset, password: "NieuwWw22" })).status, 200);
  assert.equal((await call("GET", "/me", null, tok)).status, 401);
  assert.equal((await call("POST", "/auth/login", { email: "jan@x.nl", password: "NieuwWw22" })).status, 200);
});

test("gezin: uitnodigen, accepteren, samen synchroniseren, verwijderen", async () => {
  const { call, mails, account } = setup();
  const eva = await account("eva@x.nl", "Eva");
  const jan = await account("jan@x.nl", "Jan");

  // Jan had al iets in zijn eigen pakket
  await call("POST", "/sync", { since: 0, changes: [{ kind: "item", id: "cat:radio", updated_at: 5, data: { q: 1 } }] }, jan);

  const inv = await call("POST", "/household/invite", { email: "jan@x.nl" }, eva);
  assert.equal(inv.status, 200);
  assert.equal(mails.at(-1).to, "jan@x.nl");
  assert.match(mails.at(-1).subject, /Eva/);
  assert.equal((await call("POST", "/household/invite", { email: "eva@x.nl" }, eva)).body.error, "self");

  const janMe = await call("GET", "/me", null, jan);
  assert.equal(janMe.body.invites.length, 1);
  const oldHousehold = janMe.body.household.id;
  const acc = await call("POST", "/invite/accept", { id: janMe.body.invites[0].id }, jan);
  assert.equal(acc.status, 200);
  assert.equal(acc.body.household.members.length, 2);
  assert.notEqual(acc.body.household.id, oldHousehold);

  // Eva schrijft, Jan ziet het; Jans oude pakket is weg
  const s1 = await call("POST", "/sync", { since: 0, changes: [
    { kind: "item", id: "cat:water", updated_at: 100, data: { q: 12 } },
    { kind: "household", id: "household", updated_at: 100, data: { adults: 2 } },
  ] }, eva);
  assert.equal(s1.body.applied, 2);
  const j1 = await call("POST", "/sync", { since: 0, changes: [] }, jan);
  assert.deepEqual(j1.body.changes.map((c) => c.id).sort(), ["cat:water", "household"]);

  // Oudere wijziging verliest, nieuwere wint, verwijderen komt door
  await call("POST", "/sync", { since: j1.body.seq, changes: [{ kind: "item", id: "cat:water", updated_at: 50, data: { q: 1 } }] }, jan);
  const j2 = await call("POST", "/sync", { since: j1.body.seq, changes: [{ kind: "item", id: "cat:water", updated_at: 200, data: { q: 18 } }] }, jan);
  assert.equal(j2.body.applied, 1);
  const e2 = await call("POST", "/sync", { since: s1.body.seq, changes: [] }, eva);
  assert.equal(e2.body.changes[0].data.q, 18);
  const e3 = await call("POST", "/sync", { since: e2.body.seq, changes: [{ kind: "item", id: "cat:water", updated_at: 300, deleted: true }] }, eva);
  const j3 = await call("POST", "/sync", { since: j2.body.seq, changes: [] }, jan);
  assert.equal(j3.body.changes.at(-1).deleted, true);
  assert.equal(j3.body.changes.at(-1).data, null);
  assert.ok(e3.body.seq >= j3.body.seq);

  // Alleen de eigenaar mag iemand verwijderen
  const ids = acc.body.household.members;
  const evaId = ids.find((m) => m.owner).id;
  const janId = ids.find((m) => !m.owner).id;
  assert.equal((await call("POST", "/household/remove", { user_id: evaId }, jan)).body.error, "not_owner");
  const rm = await call("POST", "/household/remove", { user_id: janId }, eva);
  assert.equal(rm.body.household.members.length, 1);
  const janAfter = await call("GET", "/me", null, jan);
  assert.equal(janAfter.body.household.members.length, 1);
  assert.notEqual(janAfter.body.household.id, rm.body.household.id);
});

test("eigenaar vertrekt: ander lid wordt eigenaar; account verwijderen", async () => {
  const { call, account, env } = setup();
  const eva = await account("eva@x.nl", "Eva");
  const jan = await account("jan@x.nl", "Jan");
  await call("POST", "/household/invite", { email: "jan@x.nl" }, eva);
  const inv = (await call("GET", "/me", null, jan)).body.invites[0];
  const shared = (await call("POST", "/invite/accept", { id: inv.id }, jan)).body.household.id;
  await call("POST", "/sync", { since: 0, changes: [{ kind: "plan", id: "plan", updated_at: 1, data: { meet: "school" } }] }, eva);

  assert.equal((await call("POST", "/me/delete", { password: "fout" }, eva)).status, 401);
  assert.equal((await call("POST", "/me/delete", { password: "Wachtwoord1" }, eva)).status, 200);
  const janMe = await call("GET", "/me", null, jan);
  assert.equal(janMe.body.household.id, shared);
  assert.equal(janMe.body.household.is_owner, true);
  assert.equal((await call("POST", "/sync", { since: 0 }, jan)).body.changes.length, 1);

  assert.equal((await call("POST", "/me/delete", { password: "Wachtwoord1" }, jan)).status, 200);
  assert.equal(env.DB.raw.prepare("select count(*) as n from records").get().n, 0);
  assert.equal(env.DB.raw.prepare("select count(*) as n from households").get().n, 0);
});

test("beheer en onderhoud", async () => {
  const { call, account, env } = setup();
  await account("eva@x.nl", "Eva");
  await call("POST", "/auth/signup", { email: "oud@x.nl", password: "Wachtwoord1" });
  assert.equal((await call("POST", "/admin/login", { user: "beheer", password: "nee" })).status, 401);
  const a = await call("POST", "/admin/login", { user: "beheer", password: "geheim-wachtwoord" });
  assert.equal(a.status, 200);
  const st = await call("GET", "/admin/stats", null, a.body.token);
  assert.equal(st.body.accounts, 2);
  assert.equal(st.body.confirmed, 1);
  assert.equal(st.body.days.at(-1).signup, 2);
  assert.equal((await call("GET", "/admin/stats", null, "np_x")).status, 401);

  const r = await cleanup(env, Date.now() + 8 * 24 * 3600_000);
  assert.equal(r.removed_unconfirmed, 1);
  assert.equal(env.DB.raw.prepare("select count(*) as n from users").get().n, 1);
});

test("CORS alleen voor de site; rate limit bij inloggen", async () => {
  const { call } = setup();
  const ok = await call("OPTIONS", "/auth/login", null, null, { origin: "https://noodpakket.lrvdlinden.app" });
  assert.equal(ok.headers.get("access-control-allow-origin"), "https://noodpakket.lrvdlinden.app");
  const bad = await call("POST", "/auth/login", { email: "a@b.nl", password: "x" }, null, { origin: "https://evil.example" });
  assert.equal(bad.headers.get("access-control-allow-origin"), null);
  let last;
  for (let i = 0; i < 11; i++) last = await call("POST", "/auth/login", { email: "a@b.nl", password: "Wachtwoord1" });
  assert.equal(last.status, 429);
  assert.equal((await call("GET", "/health")).body.ok, true);
});
