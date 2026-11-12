# Noodpakket

Noodpakket-app voor 72 uur zelfredzaam: checklist, barcode scannen, houdbaarheid, boodschappenlijst en noodplan, voor iPhone/iPad en Android.

- `ios/` — iOS-app (`app.lrvdlinden.noodpakket`), open `ios/Noodpakket.xcodeproj`
- `android/` — Android-app (`app.lrvdlinden.noodpakket`), als APK via GitHub Actions
- `web/` — website (noodpakket.lrvdlinden.app); als ZIP te uploaden naar eigen hosting
- `design/` — icoon, app-schermen en screenshots van de site

## Android-APK en de vaste sleutel

Elke APK is met dezelfde sleutel ondertekend, zodat een update over de vorige versie heen installeert.

1. Eenmalig: start in `LRvdLinden/app.lrvdlinden.secrets` de workflow **Android signing**. Die maakt de sleutel één keer aan
   en zet hem als secrets in deze repo. Daarna maakt hij nooit een nieuwe.
2. Zet de vingerafdruk uit die run als repository-variabele `ANDROID_CERT_SHA256` (optioneel; dan weigert de build
   elke APK met een andere sleutel).
3. Elke push naar main (map `android/`) bouwt, controleert de handtekening en publiceert meteen een release `v1.0.<run>`
   met `noodpakket.apk`. Een eigen versienummer: tag `vX.Y.Z` en push de tag.

Bezoekers downloaden altijd de nieuwste versie via de site: https://noodpakket.lrvdlinden.app/download/android/
(die pagina stuurt door naar `https://noodpakket-api.noodpakket.workers.dev/download/android`; de API geeft de APK uit de
nieuwste release door met de naam `Noodpakket-<versie>.apk`).

https://lrvdlinden.app
