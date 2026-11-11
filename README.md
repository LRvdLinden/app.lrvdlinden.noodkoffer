# Noodkoffer

Noodpakket-app voor 72 uur zelfredzaam: checklist, houdbaarheid en noodplan, voor iPhone/iPad en Android, met website noodkoffer.lrvdlinden.app.

- `ios/` — iOS-app (`app.lrvdlinden.noodkoffer`, SwiftUI + SwiftData)
- `android/` — Android-app (`app.lrvdlinden.noodkoffer`, Kotlin + Compose), als APK
- `website/` — noodkoffer.lrvdlinden.app (GitHub Pages)
- `design/` — icoon (C, schild 72), app-schermen en screenshots van de site

## Android-APK en de vaste sleutel

Elke APK is met dezelfde sleutel ondertekend, zodat een update over de vorige versie heen installeert.

1. Eenmalig: start in `LRvdLinden/app.lrvdlinden.secrets` de workflow **Android signing**. Die maakt de sleutel één keer aan
   en zet hem als secrets in deze repo. Daarna maakt hij nooit een nieuwe.
2. Zet de vingerafdruk uit die run als repository-variabele `ANDROID_CERT_SHA256` (optioneel, maar dan weigert de build
   elke APK met een andere sleutel).
3. Nieuwe versie: tag `vX.Y.Z` en push de tag. De workflow **Android** bouwt, controleert de handtekening en publiceert
   een release met `noodkoffer.apk`.

Nieuwste APK: https://github.com/LRvdLinden/app.lrvdlinden.noodkoffer/releases/latest/download/noodkoffer.apk

https://lrvdlinden.app
