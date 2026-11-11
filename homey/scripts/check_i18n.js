'use strict';
// Fails when any of the 13 Homey languages is missing a locale key or an inline manifest translation.
const fs = require('fs');
const path = require('path');

const LANGS = ['en', 'nl', 'de', 'fr', 'it', 'sv', 'no', 'es', 'da', 'ru', 'pl', 'ko', 'ar'];
const root = path.join(__dirname, '..');
const errors = [];

function flatten(obj, prefix = '') {
  return Object.entries(obj).flatMap(([k, v]) => (v && typeof v === 'object' && !Array.isArray(v)
    ? flatten(v, `${prefix}${k}.`) : [`${prefix}${k}`]));
}

const base = flatten(JSON.parse(fs.readFileSync(path.join(root, 'locales/en.json'), 'utf8')));
for (const lang of LANGS) {
  const file = path.join(root, `locales/${lang}.json`);
  if (!fs.existsSync(file)) { errors.push(`missing locales/${lang}.json`); continue; }
  const keys = new Set(flatten(JSON.parse(fs.readFileSync(file, 'utf8'))));
  base.filter((k) => !keys.has(k)).forEach((k) => errors.push(`${lang}: missing locale key ${k}`));
}

// Any object that has an "en" string is treated as a translation object.
function walk(node, where) {
  if (Array.isArray(node)) { node.forEach((n, i) => walk(n, `${where}[${i}]`)); return; }
  if (!node || typeof node !== 'object') return;
  if (typeof node.en === 'string' || Array.isArray(node.en)) {
    LANGS.filter((l) => !(l in node)).forEach((l) => errors.push(`${where}: missing ${l}`));
    if (typeof node.en === 'string') {
      const ph = (s) => (s.match(/\[\[\w+\]\]|__\w+__/g) || []).sort().join(',');
      LANGS.filter((l) => typeof node[l] === 'string' && ph(node[l]) !== ph(node.en))
        .forEach((l) => errors.push(`${where}: placeholder mismatch in ${l}`));
    }
    return;
  }
  for (const [k, v] of Object.entries(node)) {
    if (k === 'example') continue;
    walk(v, `${where}.${k}`);
  }
}

function scan(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) { if (entry.name !== 'node_modules') scan(p); continue; }
    if (entry.name.endsWith('.json') && !p.includes(`${path.sep}locales${path.sep}`)) {
      walk(JSON.parse(fs.readFileSync(p, 'utf8')), path.relative(root, p));
    }
  }
}
scan(path.join(root, '.homeycompose'));
scan(path.join(root, 'drivers'));
scan(path.join(root, 'widgets'));
walk(JSON.parse(fs.readFileSync(path.join(root, '.homeychangelog.json'), 'utf8')), '.homeychangelog.json');

for (const lang of LANGS.filter((l) => l !== 'en')) {
  if (!fs.existsSync(path.join(root, `README.${lang}.txt`))) errors.push(`missing README.${lang}.txt`);
}

if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
console.log(`i18n OK: ${LANGS.length} languages, ${base.length} locale keys`);
