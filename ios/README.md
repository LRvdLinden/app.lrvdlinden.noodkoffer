# Noodpakket voor iOS

SwiftUI + SwiftData (iCloud), iOS 17+, iPhone en iPad. Bundle-ID `app.lrvdlinden.noodpakket`, widget `app.lrvdlinden.noodpakket.widget`, team EU3UY476T6.

## Openen

Open `Noodpakket.xcodeproj` in Xcode en kies het schema **Noodpakket**. Zet bij de App ID in het developer-account:
iCloud (container `iCloud.app.lrvdlinden.noodpakket`), App Groups (`group.app.lrvdlinden.noodpakket`) en Push Notifications
(nodig voor CloudKit-sync). Met automatische signing doet Xcode dit meestal zelf.

Bestanden toegevoegd of verwijderd? Draai `python3 tools/make_xcodeproj.py` om het project opnieuw te maken.

## Opbouw

- `Noodpakket/Models` — SwiftData (`KitItem`, `Household`, `EmergencyPlan`, `ScannedProduct`) en de standaardchecklist (Denk vooruit).
- `Noodpakket/Services` — samenvatting, meldingen (30/7/0 dagen + checkmoment), barcode-opzoeken via Open Food Facts / Open Products Facts.
- `Noodpakket/Views` — Pakket, Houdbaar, Boodschappen, Noodplan (met "Wat te doen bij…" en PDF), Instellingen, scannen, onboarding.
- `NoodpakketWidget` — widget klein/middel en toegangsscherm.
- `Shared` — thema, widget-snapshot en `Localizable.xcstrings` (en + nl).
