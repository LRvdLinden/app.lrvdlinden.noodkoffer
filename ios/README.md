# Noodkoffer voor iOS

SwiftUI + SwiftData (iCloud), iOS 17+, iPhone en iPad. Bundle-ID `app.lrvdlinden.noodkoffer`, widget `app.lrvdlinden.noodkoffer.widget`, team EU3UY476T6.

## Openen in Xcode

```sh
brew install xcodegen
cd ios
xcodegen generate
open Noodkoffer.xcodeproj
```

Zet in het developer-account bij de App ID: iCloud (container `iCloud.app.lrvdlinden.noodkoffer`), App Groups (`group.app.lrvdlinden.noodkoffer`) en Push Notifications (nodig voor CloudKit-sync). Xcode doet dit meestal zelf bij automatische signing.

## Opbouw

- `Noodkoffer/Models` — SwiftData-modellen (`KitItem`, `Household`, `EmergencyPlan`) en de standaardchecklist (Denk vooruit).
- `Noodkoffer/Services` — samenvatting, lokale meldingen (30/7/0 dagen + checkmoment), koppeling met Homey (lokaal netwerk, koppelcode in de Keychain).
- `Noodkoffer/Views` — Pakket, Houdbaar, Noodplan (PDF), Instellingen (MoreAppsCard + AppInfoFooter), onboarding en splash.
- `NoodkofferWidget` — widget klein/middel en toegangsscherm.
- `Shared` — thema, widget-snapshot en `Localizable.xcstrings` (en + nl).

## Homey-koppeling

De app stuurt het pakket met `PUT http://<homey>/api/app/app.lrvdlinden.noodkoffer/inventory` naar de Homey-app (map `homey/`), beveiligd met de 6-cijferige code van de instellingenpagina in Homey.
