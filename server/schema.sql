-- Noodpakket: accounts, gezinnen en gedeelde gegevens (Cloudflare D1 / SQLite).
-- Idempotent: de workflow voert dit bij elke deploy uit.

create table if not exists users (
  id text primary key,
  email text not null unique,          -- altijd in kleine letters
  pass_hash text not null,             -- PBKDF2-SHA256, base64
  pass_salt text not null,
  name text not null default '',
  lang text not null default 'nl',
  household_id text,
  confirmed_at integer,                -- null = nog niet bevestigd (dan kan er niet worden ingelogd)
  created_at integer not null,
  last_login integer
);
create index if not exists users_household on users (household_id);

create table if not exists tokens (     -- bevestigen en wachtwoord herstellen (alleen de hash wordt bewaard)
  token_hash text primary key,
  user_id text not null,
  kind text not null check (kind in ('confirm', 'reset')),
  expires_at integer not null
);
create index if not exists tokens_user on tokens (user_id);

create table if not exists sessions (
  token_hash text primary key,
  user_id text not null,
  created_at integer not null,
  last_used integer not null,
  device text not null default ''
);
create index if not exists sessions_user on sessions (user_id);

create table if not exists households (
  id text primary key,
  name text not null default '',
  owner_id text not null,
  created_at integer not null,
  seq integer not null default 0
);

create table if not exists invites (
  id text primary key,
  household_id text not null,
  email text not null,
  invited_by text not null,
  created_at integer not null,
  expires_at integer not null,
  unique (household_id, email)
);
create index if not exists invites_email on invites (email);

-- Gedeelde gegevens van een gezin: onderdelen van het pakket (kind 'item'), huishouden ('household') en noodplan ('plan').
-- data is JSON van de app; updated_at in milliseconden van het apparaat (laatste wijziging wint); seq loopt per gezin op.
create table if not exists records (
  household_id text not null,
  kind text not null check (kind in ('item', 'household', 'plan')),
  id text not null,
  data text not null default '{}',
  updated_at integer not null,
  deleted integer not null default 0,
  seq integer not null,
  primary key (household_id, kind, id)
);
create index if not exists records_seq on records (household_id, seq);

-- Teller tegen misbruik (per sleutel per tijdvak) en dagtellers voor het beheer
create table if not exists rate (
  key text primary key,
  count integer not null,
  window_start integer not null
);
create table if not exists daily (
  day text not null,
  metric text not null,
  n integer not null default 0,
  primary key (day, metric)
);
