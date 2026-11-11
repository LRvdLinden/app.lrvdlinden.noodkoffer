# Noodpakket – App-informatie voor App Store Connect

## Algemene informatie
| Veld | Waarde |
|---|---|
| Bundle ID | app.lrvdlinden.noodpakket |
| SKU | app.lrvdlinden.noodpakket |
| Apple ID | TODO: invullen na aanmaken app in App Store Connect |
| Primaire taal | Dutch (nl-NL) |
| App-talen | Nederlands, Engels |
| Versie | 1.0.0 (build 1) |
| Platformen | iPhone, iPad (iOS/iPadOS 17+) + widget |
| Primaire categorie | Lifestyle – huishouden en voorbereiding thuis |
| Secundaire categorie | Utilities – checklist, scanner en herinneringen |
| Prijs | Gratis |
| Content Rights | Ja, de app toont content van derden: productgegevens van Open Food Facts en Open Products Facts (Open Database License, ODbL, met bronvermelding in de app). Checklist gebaseerd op openbaar advies van de Nederlandse overheid (denkvooruit.nl), geen officiële overheidsapp. |
| License Agreement | Apple Standard EULA |
| Copyright | © 2026 Léon van der Linden |

## Leeftijdsclassificatie (vragenlijst)
Alle categorieën (geweld, seksualiteit, grof taalgebruik, drugs, alcohol, tabak, gokken, horror, medische behandelinformatie, wedstrijden) = **None**.
Onbeperkte webtoegang: **No**. Gebruikersgegenereerde content/chat: **No**. Kansspelen: **No**. Leeftijdscontroles/ouderlijk toezicht in de app: **No**.
Verwachte uitkomst: **4+**.

## App Privacy (privacylabel)
**Data Not Collected.**
- Geen account, analytics, crash-SDK, advertenties of tracking (geen ATT-prompt).
- Gegevens staan lokaal en in de privé-iCloud-database van de gebruiker (CloudKit); de ontwikkelaar kan er niet bij. Apple telt dit niet als verzameling door de ontwikkelaar.
- Barcode-opzoeking: alleen het barcodenummer gaat naar Open Food Facts / Open Products Facts, niet gekoppeld aan de gebruiker of een identifier.
- Camera: alleen on-device verwerking (VisionKit), niets opgeslagen of verstuurd.
- `PrivacyInfo.xcprivacy`: geen tracking, geen verzamelde datatypes; required-reason API UserDefaults (CA92.1, app group 1C8F.1).
- Externe SDK's: geen.

## App Accessibility (Accessibility Nutrition Labels)
| Functie | iPhone | iPad | Toelichting |
|---|---|---|---|
| Dark Interface | Ja | Ja | Systeemkleuren en eigen kleuren met donkere variant |
| Larger Text | Niet geclaimd | Niet geclaimd | Meeste tekst gebruikt Dynamic Type, enkele vaste formaten; eerst testen op AX-groottes |
| VoiceOver | Niet geclaimd | Niet geclaimd | Labels deels aanwezig; nog niet volledig getest |
| Voice Control | Niet geclaimd | Niet geclaimd | |
| Differentiate Without Color Alone | Niet geclaimd | Niet geclaimd | |
| Sufficient Contrast | Niet geclaimd | Niet geclaimd | |
| Reduced Motion | Niet geclaimd | Niet geclaimd | |
| Captions / Audio Descriptions | n.v.t. | n.v.t. | Geen video/audio |

## URL's
- Support: https://noodpakket.lrvdlinden.app/support/
- Marketing: https://noodpakket.lrvdlinden.app/
- Privacy: https://noodpakket.lrvdlinden.app/privacy/
