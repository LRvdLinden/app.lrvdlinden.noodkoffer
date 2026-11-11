'use strict';
const assert = require('assert');
const { daysBetween, normaliseInventory, summarise, codesMatch, newPairingCode } = require('../lib/kit');

assert.strictEqual(daysBetween('2026-11-11', '2026-12-11'), 30);
assert.strictEqual(daysBetween('2026-03-28', '2026-03-30'), 2); // DST change
const kit = normaliseInventory({ items: [
  { id: 'w', name: 'Drinkwater', status: 'have', expiresAt: '2026-12-01' },
  { id: 'b', name: 'Batterijen', status: 'partial', expiresAt: '2026-11-01' },
  { id: 'r', name: 'Noodradio', status: 'missing' },
  { id: 'x', name: 'Bad date', status: 'have', expiresAt: 'tomorrow' },
], plan: 'Verzamelen bij de buren', nextCheck: '2027-05-01' });
assert.strictEqual(kit.items[3].expiresAt, null);
const s = summarise(kit, '2026-11-11');
assert.strictEqual(s.completeness, Math.round((1 + 0.5 + 0 + 1) / 4 * 100));
assert.strictEqual(s.expired.length, 1);
assert.strictEqual(s.next.name, 'Drinkwater');
assert.strictEqual(s.next.daysLeft, 20);
assert.throws(() => normaliseInventory({}));
assert.ok(codesMatch('123456', '123456'));
assert.ok(!codesMatch('123456', '12345'));
assert.match(newPairingCode(), /^\d{6}$/);
console.log('kit tests OK');
