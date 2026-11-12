// Accountpagina's van Noodpakket: e-mailadres bevestigen, nieuw wachtwoord kiezen, account bekijken en verwijderen,
// en de landingspagina van een gezinsuitnodiging. Praat met de Noodpakket-API (api.js). Werkt in het Nederlands en,
// met ?lang=en (links in Engelse mails), in het Engels.
(function () {
  var q = new URLSearchParams(location.search);
  var en = q.get('lang') === 'en' || (!q.get('lang') && /^en\b/i.test(navigator.language || '') && document.body.dataset.autoLang === '1');
  var T = {
    confirming: ['Even geduld…', 'One moment…'],
    confirmingText: ['We bevestigen je e-mailadres.', 'Confirming your email address.'],
    confirmed: ['Je account is actief', 'Your account is active'],
    confirmedText: ['Je e-mailadres is bevestigd. Open Noodpakket en log in onder Instellingen → Account.', 'Your email address is confirmed. Open Noodpakket and sign in under Settings → Account.'],
    expired: ['Deze link werkt niet meer', 'This link no longer works'],
    expiredText: ['De link is verlopen of al gebruikt. Is je account al actief, log dan gewoon in. Anders kun je hieronder een nieuwe mail aanvragen.', 'The link has expired or was already used. If your account is already active, just sign in. Otherwise request a new email below.'],
    noToken: ['Geen link gevonden', 'No link found'],
    noTokenText: ['Open de link uit de mail die je van Noodpakket hebt gekregen.', 'Open the link from the email you received from Noodpakket.'],
    resend: ['Nieuwe bevestigingsmail', 'New confirmation email'],
    email: ['E-mailadres', 'Email address'],
    sent: ['Als er een niet-bevestigd account bij dit adres hoort, is er een nieuwe mail onderweg.', 'If there is an unconfirmed account for this address, a new email is on its way.'],
    newPw: ['Nieuw wachtwoord', 'New password'],
    newPwHint: ['Minstens 8 tekens, met een letter en een cijfer.', 'At least 8 characters, with a letter and a number.'],
    repeat: ['Herhaal wachtwoord', 'Repeat password'],
    save: ['Wachtwoord opslaan', 'Save password'],
    mismatch: ['De wachtwoorden zijn niet gelijk.', 'The passwords do not match.'],
    pwSaved: ['Je wachtwoord is gewijzigd. Log in de app opnieuw in met je nieuwe wachtwoord.', 'Your password has been changed. Sign in to the app again with your new password.'],
    password_short: ['Het wachtwoord moet minstens 8 tekens hebben.', 'The password needs at least 8 characters.'],
    password_weak: ['Gebruik minstens één letter en één cijfer.', 'Use at least one letter and one number.'],
    password_long: ['Het wachtwoord is te lang.', 'The password is too long.'],
    rate_limited: ['Te veel pogingen. Probeer het over een tijdje opnieuw.', 'Too many attempts. Please try again later.'],
    network: ['Geen verbinding met de server. Probeer het opnieuw.', 'Could not reach the server. Please try again.'],
    bad_credentials: ['Onjuist e-mailadres of wachtwoord.', 'Incorrect email address or password.'],
    not_confirmed: ['Bevestig eerst je e-mailadres via de link in de mail.', 'Confirm your email address first using the link in the email.'],
    bad_email: ['Vul een geldig e-mailadres in.', 'Enter a valid email address.'],
    forgot: ['Wachtwoord vergeten?', 'Forgot your password?'],
    forgotSent: ['Als er een account bij dit adres hoort, krijg je een mail met een link om een nieuw wachtwoord te kiezen.', 'If there is an account for this address, you will receive an email with a link to choose a new password.'],
    sendLink: ['Stuur link', 'Send link'],
    login: ['Inloggen', 'Sign in'],
    password: ['Wachtwoord', 'Password'],
    signedIn: ['Ingelogd als', 'Signed in as'],
    household: ['Gezin', 'Household'],
    members: ['leden', 'members'],
    logout: ['Uitloggen', 'Sign out'],
    del: ['Account verwijderen', 'Delete account'],
    delConfirm: ['Weet je het zeker? Je account wordt direct verwijderd. Ben je de laatste van je gezin, dan worden ook de gedeelde gegevens verwijderd. Vul ter bevestiging je wachtwoord in.', 'Are you sure? Your account is deleted immediately. If you are the last member of your household, the shared data is deleted as well. Enter your password to confirm.'],
    deleted: ['Je account is verwijderd. De app blijft gewoon werken zonder account.', 'Your account has been deleted. The app keeps working without an account.'],
    working: ['Bezig…', 'Working…'],
    server_error: ['Er ging iets mis op de server. Probeer het later opnieuw.', 'Something went wrong on the server. Please try again later.']
  };
  function t(k) { var v = T[k]; return v ? v[en ? 1 : 0] : k; }
  function $(id) { return document.getElementById(id); }
  if (en) document.documentElement.lang = 'en';
  document.querySelectorAll('[data-t]').forEach(function (el) { el.textContent = t(el.dataset.t); });
  document.querySelectorAll('[data-en]').forEach(function (el) { if (en) el.innerHTML = el.dataset.en; });

  function api(path, body, token) {
    var opts = { method: body ? 'POST' : 'GET', headers: {} };
    if (body) { opts.headers['content-type'] = 'application/json'; opts.body = JSON.stringify(body); }
    if (token) opts.headers.authorization = 'Bearer ' + token;
    return fetch(window.NOODPAKKET_API + path, opts).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) { j._status = r.status; return j; });
    }, function () { return { _status: 0, error: 'network' }; });
  }
  function state(el, kind, title, text) {
    el.className = 'state ' + kind;
    el.innerHTML = '<span class="dot">' + (kind === 'ok' ? '✓' : kind === 'err' ? '!' : '…') + '</span><div><h2></h2><p></p></div>';
    el.querySelector('h2').textContent = title; el.querySelector('p').textContent = text;
  }
  function msg(el, kind, text) { el.className = 'msg ' + kind; el.textContent = text; }
  function busy(btn, on) { btn.disabled = on; if (on) { btn.dataset.label = btn.textContent; btn.textContent = t('working'); } else if (btn.dataset.label) btn.textContent = btn.dataset.label; }
  function err(j) { return t(j.error || 'server_error'); }

  // ---- /bevestigen/?t=… ----
  var conf = $('confirm-state');
  if (conf) {
    var tok = q.get('t');
    if (!tok) { state(conf, 'err', t('noToken'), t('noTokenText')); $('resend').hidden = false; }
    else {
      state(conf, '', t('confirming'), t('confirmingText'));
      api('/auth/confirm', { token: tok }).then(function (j) {
        if (j.ok) { state(conf, 'ok', t('confirmed'), t('confirmedText')); $('after-ok').hidden = false; history.replaceState(null, '', location.pathname + (en ? '?lang=en' : '')); }
        else if (j._status === 410 || j._status === 400) { state(conf, 'err', t('expired'), t('expiredText')); $('resend').hidden = false; }
        else state(conf, 'err', t('network'), '');
      });
    }
    var rf = $('resend-form');
    rf && rf.addEventListener('submit', function (e) {
      e.preventDefault(); var b = rf.querySelector('button'); busy(b, true);
      api('/auth/resend', { email: rf.email.value }).then(function (j) {
        busy(b, false); msg($('resend-msg'), j._status === 200 ? 'ok' : 'err', j._status === 200 ? t('sent') : err(j));
      });
    });
  }

  // ---- /account/ (?reset=…) ----
  var resetForm = $('reset-form');
  if (resetForm) {
    var rt = q.get('reset');
    if (rt) { $('reset').hidden = false; $('signin').hidden = true; history.replaceState(null, '', location.pathname + (en ? '?lang=en' : '')); }
    resetForm.addEventListener('submit', function (e) {
      e.preventDefault(); var m = $('reset-msg'), b = resetForm.querySelector('button');
      if (resetForm.pw.value !== resetForm.pw2.value) return msg(m, 'err', t('mismatch'));
      busy(b, true);
      api('/auth/reset', { token: rt, password: resetForm.pw.value }).then(function (j) {
        busy(b, false);
        if (j.ok) { msg(m, 'ok', t('pwSaved')); resetForm.reset(); b.disabled = true; }
        else msg(m, 'err', j._status === 410 ? t('expiredText') : err(j));
      });
    });

    var token = null;
    var lf = $('login-form');
    lf.addEventListener('submit', function (e) {
      e.preventDefault(); var b = lf.querySelector('button'); busy(b, true);
      api('/auth/login', { email: lf.email.value, password: lf.pw.value, device: 'web' }).then(function (j) {
        busy(b, false);
        if (!j.token) return msg($('login-msg'), 'err', err(j));
        token = j.token; lf.pw.value = ''; msg($('login-msg'), '', '');
        api('/me', null, token).then(showMe);
      });
    });
    function showMe(j) {
      if (!j.user) return;
      $('signin').hidden = true; $('me').hidden = false;
      $('me-email').textContent = j.user.email;
      var h = j.household;
      $('me-household').textContent = h ? h.name + ' · ' + h.members.length + ' ' + t('members') : '';
    }
    $('logout').addEventListener('click', function () {
      api('/auth/logout', {}, token); token = null; $('me').hidden = true; $('signin').hidden = false;
    });
    $('delete-form').addEventListener('submit', function (e) {
      e.preventDefault(); var b = e.target.querySelector('button'); busy(b, true);
      api('/me/delete', { password: e.target.pw.value }, token).then(function (j) {
        busy(b, false);
        if (j.ok) { token = null; $('me').hidden = true; msg($('done-msg'), 'ok', t('deleted')); }
        else msg($('delete-msg'), 'err', err(j));
      });
    });
    var ff = $('forgot-form');
    ff.addEventListener('submit', function (e) {
      e.preventDefault(); var b = ff.querySelector('button'); busy(b, true);
      api('/auth/forgot', { email: ff.email.value }).then(function (j) {
        busy(b, false); msg($('forgot-msg'), j._status === 200 ? 'ok' : 'err', j._status === 200 ? t('forgotSent') : err(j));
      });
    });
  }
})();
