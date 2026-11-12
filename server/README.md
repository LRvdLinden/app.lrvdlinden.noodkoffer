# Noodpakket-API

Cloudflare Worker + D1 voor de (optionele) accounts van Noodpakket: e-mailbevestiging verplicht, gezinnen met
uitnodigingen en synchronisatie van het pakket tussen gezinsleden. Zonder account werkt de app gewoon (lokaal + iCloud).

- `src/index.js` – router en endpoints, `src/core.js` – pure hulpfuncties (wachtwoorden, LWW, tokens), `src/mail.js` – mails (NL/EN).
- `schema.sql` – tabellen; de workflow voert dit bij elke deploy uit (idempotent).
- Testen: `npm ci && npm test` (Node 22+, gebruikt `node:sqlite` als nep-D1).
- Deploy: `.github/workflows/server.yml` bij een push naar `server/**` op main.

## Endpoints

| Methode | Pad | Wat |
|---|---|---|
| POST | /auth/signup | `{email, password, name, lang, website}` → bevestigingsmail (website = honingpot) |
| POST | /auth/confirm | `{token}` uit de mail → account actief (pagina /bevestigen/) |
| POST | /auth/resend | `{email}` → bevestigingsmail opnieuw |
| POST | /auth/login | `{email, password, device}` → `{token, user}`; 403 `not_confirmed` |
| POST | /auth/forgot, /auth/reset | wachtwoord herstellen (pagina /account/?reset=) |
| GET/POST | /me | gebruiker, gezin, leden en uitnodigingen / naam of taal wijzigen |
| POST | /me/password, /me/delete, /auth/logout | |
| POST | /household, /household/invite, /household/invite/cancel, /household/leave, /household/remove | gezin |
| POST | /invite/accept, /invite/decline | uitnodiging |
| POST | /sync | `{since, changes:[{kind, id, data, updated_at, deleted}]}` → wijzigingen sinds `since` |
| POST/GET | /admin/login, /admin/stats | beheer (LRbeheer en noodpakket.lrvdlinden.app/beheer) |

Secrets in de Worker: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `BEHEER_USER`,
`BEHEER_PASSWORD`, `ADMIN_SECRET`. Wachtwoorden worden met PBKDF2-SHA256 (100.000 rondes) opgeslagen, sessietokens
en mailtokens alleen als SHA-256-hash. Niet-bevestigde accounts worden na 7 dagen verwijderd (cron 03:17 UTC).
