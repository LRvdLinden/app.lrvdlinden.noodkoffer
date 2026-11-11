# Noodpakket voor Android

Kotlin + Jetpack Compose, Android 8+ (minSdk 26), package `app.lrvdlinden.noodpakket`. Talen: Engels en Nederlands.

- `data/` — model, standaardchecklist (Denk vooruit), opslag als JSON in de privé-opslag van de app, barcode-opzoeken (Open Food Facts) en back-up.
- `notify/` — dagelijkse controle met WorkManager; meldingen 30 en 7 dagen vooraf, op de dag zelf, bij verlopen en bij het checkmoment.
- `ui/` — Pakket, Houdbaar, Boodschappen, Noodplan (met "Wat te doen bij…" en PDF), Instellingen met de Meer apps-kaart en versie-footer, scannen (Google-codescanner + tekstherkenning voor datums), onboarding.
- `widget/` — Glance-widget voor het beginscherm.

Bouwen gebeurt in GitHub Actions (`.github/workflows/android.yml`); lokaal kan met Android Studio. Een release-build wordt
alleen ondertekend als `ANDROID_KEYSTORE_FILE` en de wachtwoorden als omgevingsvariabelen zijn gezet; de sleutel staat nooit in de repo.

Geen review-prompt: de Google Play In-App Review-API werkt alleen bij installatie via de Play Store, niet bij een APK.
