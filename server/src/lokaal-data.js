// Gegevens per regio voor de belangrijke nummers in het noodplan. Bronnen (november 2026):
// - Rampenzenders en FM-frequenties: denkvooruit.nl (radio) en de ontvangst-/rampenzenderpagina's van de omroepen.
// - DAB+-kanalen: de ontvangstpagina's van de omroepen (alleen waar de omroep het zelf noemt).
// - Waterbedrijven: de storings-/contactpagina's van de bedrijven zelf (alleen nummers die daar staan).
// Coördinaten van zenders zijn bij benadering (plaats van de zender), alleen om de dichtstbijzijnde te kiezen.

export const ZENDERS = {
  groningen: { omroep: "RTV Noord", site: "https://www.rtvnoord.nl", dab: "7C",
    fm: [{ mhz: "97.5", gebied: "Groningen", lat: 53.22, lon: 6.57 }] },
  friesland: { omroep: "Omrop Fryslân", site: "https://www.omropfryslan.nl", dab: "5A",
    fm: [{ mhz: "92.2", gebied: "Fryslân en de Noordoostpolder", lat: 53.07, lon: 5.80 },
         { mhz: "92.5", gebied: "Ooststellingwerf", lat: 52.98, lon: 6.30 }] },
  drenthe: { omroep: "RTV Drenthe", site: "https://www.rtvdrenthe.nl", dab: "7C",
    fm: [{ mhz: "90.8", gebied: "Grote delen van Drenthe", lat: 52.95, lon: 6.40 },
         { mhz: "99.3", gebied: "Zuidoost-Drenthe en Emmen", lat: 52.79, lon: 6.90 }] },
  overijssel: { omroep: "RTV Oost", site: "https://www.oost.nl", dab: "6A",
    fm: [{ mhz: "99.4", gebied: "Noordwest-Overijssel", lat: 52.52, lon: 6.10 },
         { mhz: "97.9", gebied: "Deventer", lat: 52.25, lon: 6.16 },
         { mhz: "95.6", gebied: "Zuid-Salland", lat: 52.24, lon: 6.50 },
         { mhz: "89.4", gebied: "Twente", lat: 52.27, lon: 6.79 }] },
  flevoland: { omroep: "Omroep Flevoland", site: "https://www.omroepflevoland.nl", dab: "8B",
    fm: [{ mhz: "89.8", gebied: "Flevoland", lat: 52.52, lon: 5.47 }] },
  gelderland: { omroep: "Omroep Gelderland", site: "https://www.gld.nl", dab: "12B",
    fm: [{ mhz: "88.9", gebied: "Arnhem en Nijmegen", lat: 51.98, lon: 5.91 },
         { mhz: "103.5", gebied: "Veluwe", lat: 52.18, lon: 5.93 },
         { mhz: "90.4", gebied: "Achterhoek", lat: 52.09, lon: 6.45 },
         { mhz: "89.1", gebied: "Rivierengebied", lat: 51.82, lon: 5.56 },
         { mhz: "99.6", gebied: "Betuwe", lat: 51.81, lon: 5.25 }] },
  utrecht: { omroep: "RTV Utrecht (Radio M Utrecht)", site: "https://www.rtvutrecht.nl", dab: "12B",
    fm: [{ mhz: "93.1", gebied: "Hele provincie Utrecht", lat: 51.97, lon: 4.92 },
         { mhz: "97.9", gebied: "Rhenen en omgeving", lat: 51.96, lon: 5.57 }] },
  "noord-holland": { omroep: "NH Radio", site: "https://www.nhnieuws.nl", dab: null,
    fm: [{ mhz: "88.9", gebied: "Amsterdam", lat: 52.37, lon: 4.90 },
         { mhz: "88.7", gebied: "Alkmaar", lat: 52.63, lon: 4.75 },
         { mhz: "88.7", gebied: "Hilversum", lat: 52.22, lon: 5.18 },
         { mhz: "93.9", gebied: "Wieringermeer", lat: 52.85, lon: 5.03 }] },
  west: { omroep: "Omroep West (Radio West)", site: "https://www.omroepwest.nl", dab: "5B",
    fm: [{ mhz: "89.3", gebied: "Haaglanden en Hollands Midden", lat: 52.07, lon: 4.30 },
         { mhz: "88.0", gebied: "Leiden", lat: 52.16, lon: 4.49 }] },
  rijnmond: { omroep: "Radio Rijnmond", site: "https://www.rijnmond.nl", dab: "5B",
    fm: [{ mhz: "93.4", gebied: "Rijnmond en Zuid-Holland Zuid", lat: 51.92, lon: 4.48 }] },
  zeeland: { omroep: "Omroep Zeeland", site: "https://www.omroepzeeland.nl", dab: "5B",
    fm: [{ mhz: "87.9", gebied: "Bijna heel Zeeland", lat: 51.50, lon: 3.89 },
         { mhz: "98.4", gebied: "West-Walcheren en West-Zeeuws-Vlaanderen", lat: 51.44, lon: 3.57 }] },
  "noord-brabant": { omroep: "Omroep Brabant", site: "https://www.omroepbrabant.nl", dab: null,
    fm: [{ mhz: "91.0", gebied: "West-Brabant", lat: 51.59, lon: 4.78 },
         { mhz: "91.9", gebied: "Midden-Brabant", lat: 51.56, lon: 5.09 },
         { mhz: "95.8", gebied: "Noordoost-Brabant", lat: 51.69, lon: 5.30 },
         { mhz: "87.6", gebied: "Zuidoost-Brabant", lat: 51.44, lon: 5.48 }] },
  limburg: { omroep: "L1 Radio", site: "https://www.l1.nl", dab: "7C",
    fm: [{ mhz: "100.3", gebied: "Noord- en Midden-Limburg", lat: 51.19, lon: 5.99 },
         { mhz: "95.3", gebied: "Zuid-Limburg", lat: 50.90, lon: 5.92 }] },
};

// Zuid-Holland heeft twee rampenzenders: Omroep West voor de veiligheidsregio's Haaglanden en Hollands Midden,
// Radio Rijnmond voor Rotterdam-Rijnmond en Zuid-Holland Zuid.
export const ZUID_HOLLAND_WEST = new Set([
  "delft", "den haag", "'s-gravenhage", "leidschendam-voorburg", "midden-delfland", "pijnacker-nootdorp", "rijswijk",
  "wassenaar", "westland", "zoetermeer",
  "alphen aan den rijn", "bodegraven-reeuwijk", "gouda", "hillegom", "kaag en braassem", "katwijk", "krimpenerwaard",
  "leiden", "leiderdorp", "lisse", "nieuwkoop", "noordwijk", "oegstgeest", "teylingen", "voorschoten", "waddinxveen",
  "zoeterwoude", "zuidplas",
]);

export const WATER = {
  vitens: { naam: "Vitens", telefoon: "0800 0359", toelichting: "24 uur per dag, gratis", site: "https://www.vitens.nl" },
  evides: { naam: "Evides", telefoon: "0900 0787", toelichting: "keuze 2: storingen", site: "https://www.evides.nl" },
  dunea: { naam: "Dunea", telefoon: "088 347 47 47", toelichting: "storingen 24 uur per dag", site: "https://www.dunea.nl" },
  oasen: { naam: "Oasen", telefoon: "0182 59 35 30", toelichting: "bij een storing keuze 2, dag en nacht", site: "https://www.oasen.nl" },
  pwn: { naam: "PWN", telefoon: "0800 023 23 55", toelichting: "24 uur per dag, gratis", site: "https://www.pwn.nl" },
  waternet: { naam: "Waternet", telefoon: "088 939 40 95", toelichting: "storingen 24 uur per dag", site: "https://www.waternet.nl" },
  brabantwater: { naam: "Brabant Water", telefoon: null, toelichting: "storingen: zie de website", site: "https://www.brabantwater.nl/storingen-en-werkzaamheden" },
  wml: { naam: "WML", telefoon: null, toelichting: "storingen: zie de website", site: "https://www.wml.nl" },
  wmd: { naam: "WMD", telefoon: "0592 854 545", toelichting: "storingsdienst, dag en nacht", site: "https://wmd.nl" },
  wbg: { naam: "Waterbedrijf Groningen", telefoon: "050 368 86 88", toelichting: "storingen 24 uur per dag", site: "https://waterbedrijfgroningen.nl" },
};

// Waarschijnlijk waterbedrijf per provincie (eerste = voorstel); de gebruiker bevestigt of kiest zelf.
export const WATER_PER_PROVINCIE = {
  groningen: ["wbg"], friesland: ["vitens"], drenthe: ["wmd"], overijssel: ["vitens"], flevoland: ["vitens"],
  gelderland: ["vitens"], utrecht: ["vitens"], "noord-holland": ["pwn", "waternet"],
  "zuid-holland": ["evides", "dunea", "oasen"], zeeland: ["evides"], "noord-brabant": ["brabantwater"], limburg: ["wml"],
};
export const WATER_PER_GEMEENTE = {
  amsterdam: "waternet", "den haag": "dunea", "'s-gravenhage": "dunea", leiden: "dunea", gouda: "oasen",
  rotterdam: "evides", dordrecht: "evides",
};
