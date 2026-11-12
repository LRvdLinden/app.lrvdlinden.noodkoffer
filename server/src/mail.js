// Mails van Noodpakket: bevestigen, wachtwoord herstellen en uitnodigen voor een gezin.
// Templates zijn pure functies (getest); verzenden gaat via SMTP (worker-mailer) met de SMTP_*-secrets.

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

const T = {
  nl: {
    confirm: {
      subject: "Bevestig je Noodpakket-account",
      title: "Welkom bij Noodpakket",
      intro: "Bevestig je e-mailadres om je account te activeren. Daarna kun je inloggen in de app en je pakket delen met je gezin.",
      button: "E-mailadres bevestigen",
      note: "De link werkt 48 uur. Heb je geen account aangemaakt? Dan kun je deze mail negeren; zonder bevestiging wordt het account na 7 dagen verwijderd.",
    },
    reset: {
      subject: "Nieuw wachtwoord voor Noodpakket",
      title: "Wachtwoord herstellen",
      intro: "Je hebt gevraagd om een nieuw wachtwoord. Kies er hieronder een; daarna log je op al je apparaten opnieuw in.",
      button: "Nieuw wachtwoord kiezen",
      note: "De link werkt 1 uur. Heb je dit niet gevraagd? Dan hoef je niets te doen; je wachtwoord blijft hetzelfde.",
    },
    invite: {
      subject: "{name} nodigt je uit voor een gezamenlijk noodpakket",
      title: "Samen één noodpakket",
      intro: "{name} wil het noodpakket van {household} met je delen in de app Noodpakket. Jullie zien dan allebei wat er in huis is, wat bijna verloopt en wat nog op de boodschappenlijst staat.",
      button: "Uitnodiging bekijken",
      note: "Log in de app in (of maak een gratis account) met dit e-mailadres; de uitnodiging staat dan onder Instellingen → Gezin. Ze is 14 dagen geldig.",
    },
    footer: "Noodpakket · 72 uur voorbereid · noodpakket.lrvdlinden.app",
    fallback: "Werkt de knop niet? Kopieer dan deze link:",
  },
  en: {
    confirm: {
      subject: "Confirm your Noodpakket account",
      title: "Welcome to Noodpakket",
      intro: "Confirm your email address to activate your account. You can then sign in to the app and share your kit with your household.",
      button: "Confirm email address",
      note: "The link works for 48 hours. Didn't create an account? You can ignore this email; unconfirmed accounts are deleted after 7 days.",
    },
    reset: {
      subject: "New password for Noodpakket",
      title: "Reset your password",
      intro: "You asked for a new password. Choose one below; afterwards you sign in again on all your devices.",
      button: "Choose a new password",
      note: "The link works for 1 hour. Didn't ask for this? Then there is nothing to do; your password stays the same.",
    },
    invite: {
      subject: "{name} invites you to share an emergency kit",
      title: "One kit, together",
      intro: "{name} wants to share the emergency kit of {household} with you in the Noodpakket app. You will both see what is at home, what expires soon and what is still on the shopping list.",
      button: "View invitation",
      note: "Sign in to the app (or create a free account) with this email address; the invitation appears under Settings → Household. It is valid for 14 days.",
    },
    footer: "Noodpakket · Prepared for 72 hours · noodpakket.lrvdlinden.app",
    fallback: "Button not working? Copy this link:",
  },
};

const fill = (s, vars) => s.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? "");

/** Builds {subject, text, html} for kind confirm | reset | invite. */
export function buildMail(kind, lang, link, vars = {}) {
  const L = T[lang === "en" ? "en" : "nl"];
  const t = L[kind];
  if (!t) throw new Error("unknown mail " + kind);
  const subject = fill(t.subject, vars);
  const title = fill(t.title, vars);
  const intro = fill(t.intro, vars);
  const note = fill(t.note, vars);
  const text = `${title}\n\n${intro}\n\n${t.button}: ${link}\n\n${note}\n\n— ${L.footer}\n`;
  const html = `<!doctype html><html lang="${lang === "en" ? "en" : "nl"}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(subject)}</title></head>
<body style="margin:0;padding:0;background:#F2F5F9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1E1A24">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#F2F5F9;padding:24px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#ffffff;border-radius:20px;overflow:hidden">
<tr><td style="background:#3C175B;padding:28px 28px 24px">
<table role="presentation" cellspacing="0" cellpadding="0"><tr>
<td style="width:44px;height:44px;border-radius:22px;border:4px solid #F4B943;color:#F4B943;font-weight:800;font-size:15px;text-align:center;line-height:36px">72</td>
<td style="padding-left:12px;color:#FCF2DB;font-weight:700;font-size:17px">Noodpakket</td></tr></table>
<h1 style="margin:22px 0 0;color:#ffffff;font-size:24px;line-height:1.25">${esc(title)}</h1></td></tr>
<tr><td style="padding:26px 28px 8px;font-size:16px;line-height:1.55">${esc(intro)}</td></tr>
<tr><td style="padding:16px 28px 8px"><a href="${esc(link)}" style="display:inline-block;background:#F4B943;color:#2E0F47;text-decoration:none;font-weight:700;font-size:16px;padding:14px 24px;border-radius:999px">${esc(t.button)}</a></td></tr>
<tr><td style="padding:14px 28px 4px;font-size:14px;line-height:1.5;color:#5b5566">${esc(note)}</td></tr>
<tr><td style="padding:10px 28px 24px;font-size:12px;line-height:1.5;color:#8a8494">${esc(L.fallback)}<br><a href="${esc(link)}" style="color:#5A2E85;word-break:break-all">${esc(link)}</a></td></tr>
</table>
<p style="font-size:12px;color:#8a8494;margin:16px 0 0">${esc(L.footer)}</p>
</td></tr></table></body></html>`;
  return { subject, text, html };
}

/** Sends one mail. Returns true when sent, false when SMTP is not configured or fails (never throws). */
export async function sendMail(env, to, mail) {
  if (typeof env.__mail === "function") { env.__mail(to, mail); return true; } // alleen in de tests
  if (!env.SMTP_HOST || !env.SMTP_USER || !env.SMTP_PASSWORD) {
    console.warn("SMTP niet ingesteld; mail niet verstuurd:", mail.subject);
    return false;
  }
  const port = Number(env.SMTP_PORT || 465);
  const fromRaw = env.SMTP_FROM || env.SMTP_USER;
  const m = /^\s*(.*?)\s*<([^>]+)>\s*$/.exec(fromRaw);
  const from = m ? { name: m[1].replace(/^"|"$/g, "") || "Noodpakket", email: m[2] } : { name: "Noodpakket", email: fromRaw.trim() };
  try {
    const { WorkerMailer } = await import("worker-mailer");
    const mailer = await WorkerMailer.connect({
      credentials: { username: env.SMTP_USER, password: env.SMTP_PASSWORD },
      authType: "plain",
      host: env.SMTP_HOST,
      port,
      secure: port === 465,
      startTls: port !== 465,
    });
    await mailer.send({ from, to: { email: to }, subject: mail.subject, text: mail.text, html: mail.html });
    await mailer.close?.();
    return true;
  } catch (e) {
    console.error("Mail mislukt:", e?.message || e);
    return false;
  }
}
