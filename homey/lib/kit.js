'use strict';

const crypto = require('crypto');

const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_ITEMS = 300;
const MAX_NAME = 80;
const MAX_PLAN = 4000;
const STATUS_SCORE = { have: 1, partial: 0.5, missing: 0 };

/** Today's date (YYYY-MM-DD) in the given IANA time zone. */
function todayIn(timeZone) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  return parts; // en-CA formats as YYYY-MM-DD
}

/** Whole days from `fromDate` to `toDate` (both YYYY-MM-DD). */
function daysBetween(fromDate, toDate) {
  const a = Date.UTC(...fromDate.split('-').map((n, i) => (i === 1 ? Number(n) - 1 : Number(n))));
  const b = Date.UTC(...toDate.split('-').map((n, i) => (i === 1 ? Number(n) - 1 : Number(n))));
  return Math.round((b - a) / DAY_MS);
}

function isDate(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value));
}

function cleanString(value, max) {
  if (typeof value !== 'string') return '';
  return value.replace(/[\u0000-\u001F\u007F]/g, ' ').trim().slice(0, max);
}

/**
 * Validates and normalises the inventory sent by the iPhone app.
 * Throws an Error with `statusCode` 400 when the payload is unusable.
 */
function normaliseInventory(body) {
  const fail = (message) => Object.assign(new Error(message), { statusCode: 400 });
  if (!body || typeof body !== 'object') throw fail('invalid_body');
  if (!Array.isArray(body.items)) throw fail('items_missing');
  if (body.items.length > MAX_ITEMS) throw fail('too_many_items');

  const items = body.items.map((raw, index) => {
    if (!raw || typeof raw !== 'object') throw fail(`invalid_item_${index}`);
    const id = cleanString(raw.id, 64) || `item-${index}`;
    const name = cleanString(raw.name, MAX_NAME);
    if (!name) throw fail(`item_name_missing_${index}`);
    const status = Object.prototype.hasOwnProperty.call(STATUS_SCORE, raw.status) ? raw.status : 'missing';
    const expiresAt = isDate(raw.expiresAt) ? raw.expiresAt : null;
    return { id, name, status, expiresAt };
  });

  return {
    items,
    plan: cleanString(body.plan, MAX_PLAN),
    nextCheck: isDate(body.nextCheck) ? body.nextCheck : null,
  };
}

/** Constant-time comparison of two pairing codes. */
function codesMatch(expected, given) {
  if (typeof expected !== 'string' || typeof given !== 'string') return false;
  const a = Buffer.from(expected);
  const b = Buffer.from(given);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function newPairingCode() {
  return String(crypto.randomInt(0, 1000000)).padStart(6, '0');
}

/** Pure summary of the kit for a given date. */
function summarise(kit, today) {
  const items = (kit && kit.items) || [];
  const score = items.reduce((sum, item) => sum + STATUS_SCORE[item.status], 0);
  const completeness = items.length ? Math.round((score / items.length) * 100) : 0;

  const dated = items
    .filter((item) => item.expiresAt)
    .map((item) => ({ ...item, daysLeft: daysBetween(today, item.expiresAt) }))
    .sort((a, b) => a.daysLeft - b.daysLeft);

  const expired = dated.filter((item) => item.daysLeft < 0);
  const upcoming = dated.filter((item) => item.daysLeft >= 0);

  return {
    itemCount: items.length,
    completeness,
    expired,
    upcoming,
    next: upcoming[0] || null,
  };
}

module.exports = {
  DAY_MS, todayIn, daysBetween, isDate, normaliseInventory, codesMatch, newPairingCode, summarise,
};
