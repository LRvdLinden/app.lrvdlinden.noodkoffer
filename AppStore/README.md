# Noodpakket – App Store-pakket

Alles voor App Store Connect, versie 1.0.0.

## Inhoud
- `app-info.md` – algemene info, categorie, leeftijd, privacylabel, toegankelijkheid
- `metadata/` – fastlane-deliver-structuur, **nl-NL** (primair) en **en-US**; privacyteksten in `metadata/privacy/`; App Review-notities (EN) in `metadata/review_information/notes.txt` met NL-referentie `notes-nl.txt` (niet uploaden)
- `screenshots/<taal>/` – iPhone 6.3" (1206×2622), iPhone 6.1" (1179×2556), iPad 13" (2064×2752); 5 per toestel
- `marketing/<taal>/` – header 5244×2950 en 3840×1646, search 5244×2950, 3840×2560 en 1920×1280

## Beeldtalen
NL en EN, omdat de app beide talen ondersteunt (String Catalog en + nl).
De screenshots zijn **nagebouwd** vanuit de SwiftUI-schermen (HTML, `design/mockups/app.html`) en niet met de simulator gemaakt. Bron en renderscripts: `design/appstore-src/` (`render_screens.mjs`, `compose.mjs`, `frame.html`).

## Uploadchecklist
1. Maak de app aan in App Store Connect (bundle ID `app.lrvdlinden.noodpakket`, SKU idem) en zet het Apple ID in `app-info.md`.
2. Vul per taal naam, ondertitel, promotietekst, beschrijving, trefwoorden en URL's in uit `metadata/<taal>/`.
3. Upload de screenshots per toestelgrootte; zet de marketingafbeeldingen bij de header- en zoekresultaatassets.
4. Privacylabel: Identifiers → Device ID (niet gekoppeld, geen tracking, App Functionality), alleen voor Berichten van Noodpakket. Leeftijd: alles None → 4+.
5. App Review: notities uit `notes.txt`; Sign-in required aan met het demo-account appreview@noodpakket.lrvdlinden.app (wachtwoord staat niet in de repo; opnieuw instellen via workflow Review-account). Voeg Noodpakket-review-barcodes.png toe als bijlage.
6. Categorie Lifestyle, secundair Utilities. Copyright: `© 2026 Léon van der Linden`.
