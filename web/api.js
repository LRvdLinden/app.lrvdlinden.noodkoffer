// Adres van de Noodpakket-API (Cloudflare Worker): accounts, gezinnen en de Android-download.
window.NOODPAKKET_API = 'https://noodpakket-api.noodpakket.workers.dev';

// Versie en grootte van de nieuwste Android-app bij elementen met data-apk-version.
(function () {
  var els = document.querySelectorAll('[data-apk-version]');
  if (!els.length || !window.fetch) return;
  fetch(window.NOODPAKKET_API + '/download/android/info').then(function (r) { return r.json(); }).then(function (i) {
    if (!i || !i.version) return;
    var mb = i.size ? ' · ' + (i.size / 1048576).toFixed(1).replace('.', ',') + ' MB' : '';
    els.forEach(function (el) { el.textContent = 'Versie ' + i.version + mb; el.hidden = false; });
  }).catch(function () {});
})();
