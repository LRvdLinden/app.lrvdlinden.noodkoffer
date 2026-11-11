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
3. Nieuwe versie: tag `vX.Y.Z` en push de tag. De workflow **Android** bouwt, controleert de handtekening en publiceert
   een release met `noodpakket.apk`.

Nieuwste APK: https://github.com/LRvdLinden/app.lrvdlinden.noodkoffer/releases/latest/download/noodpakket.apk

https://lrvdlinden.app
