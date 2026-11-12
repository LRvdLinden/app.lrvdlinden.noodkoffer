// Minimal D1 stand-in on node:sqlite, enough for the API tests.
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";

class Stmt {
  constructor(db, sql, args = []) { this.db = db; this.sql = sql; this.args = args; }
  bind(...args) { return new Stmt(this.db, this.sql, args); }
  _p() { return this.db.prepare(this.sql); }
  async first() { return this._p().get(...this.args) ?? null; }
  async all() { return { results: this._p().all(...this.args) }; }
  async run() { const r = this._p().run(...this.args); return { meta: { changes: r.changes } }; }
  _exec() { return /^\s*select/i.test(this.sql) ? { results: this._p().all(...this.args) } : this._p().run(...this.args); }
}

export function makeD1() {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  return {
    raw: db,
    prepare: (sql) => new Stmt(db, sql),
    async batch(stmts) {
      db.exec("begin");
      try { const out = stmts.map((s) => s._exec()); db.exec("commit"); return out; } catch (e) { db.exec("rollback"); throw e; }
    },
  };
}
