# Changelog

## Die App holt ihre Updates selbst (2026-09-27, 2.0.5)

Bis hierher war der Weg zu einer neuen Version: daran denken, Einstellungen
öffnen, „Auf Updates prüfen" tippen, der Browser lädt, die APK von Hand
öffnen. Drei der vier Schritte waren Arbeit für den Nutzer, und der erste war
der, an dem es scheiterte.

### Update
- **Die App sieht alle sechs Stunden selbst nach** und meldet sich mit einem
  Hinweis über allen Flächen. Der Abstand ist Absicht: bei jedem Start zu
  fragen hieße, bei jedem Start eine Verbindung über WireGuard aufzubauen, die
  niemand angefordert hat.
- **Sie lädt die Datei selbst** und **vergleicht die Prüfsumme** mit der
  angekündigten, bevor sie sie dem Installer übergibt. Vorher stand der Hash in
  der Oberfläche und wurde nie mit etwas verglichen. Stimmt er nicht, wird die
  Datei gelöscht statt liegengelassen: eine nicht passende APK im
  Download-Ordner ist genau das, was beim nächsten Mal jemand von Hand antippt.
- Nennt das Gateway **keinen** Hash, sagt die App „verglichen wurde nichts"
  statt ein Häkchen zu zeigen, das nichts bedeutet.
- „Nicht jetzt" merkt sich genau diese Nummer. Wer von Hand nachsieht, bekommt
  sie trotzdem wieder: sonst antwortet die App „Aktuell", obwohl sie es nicht
  ist.
- Die Herkunftsprüfung bleibt, wie sie war: die APK darf nur vom gekoppelten
  Gateway kommen (gleiches Schema, gleicher Host, gleicher Port). Über die
  Echtheit entscheidet weiterhin Androids Signaturprüfung beim Installieren.

### Bedienung
- **Ein Befund auf der Übersicht ist anklickbar** und führt zu seinem Dienst.
  Vorher führte die ganze Karte in die Dienstliste, und man suchte den Namen,
  den man gerade angetippt hatte, noch einmal.
- **Die Übersicht frischt sich alle 30 Sekunden still auf**, solange sie offen
  ist, und sagt, von wann ihr Stand ist. Vorher stand dort der Stand vom Öffnen
  der App, auch nach einer Stunde, und nichts daran sah alt aus.

### Erscheinungsbild
- **Die Oberfläche schreibt Deutsch mit Umlauten.** „Uebersicht", „auffaellig",
  „Zurueckgestellt" und dreißig weitere Stellen waren Umschrift.
- **Die App benutzt wieder ihre eigene Palette.** Mit `dynamicColor = true`
  ersetzt Android ab Version 12 die gesamten Farben durch eine aus dem
  Hintergrundbild abgeleitete: die sorgfältig gesetzten Werte in `Color.kt`
  kamen auf einem aktuellen Telefon nie zum Vorschein. In einem Cockpit trägt
  Farbe Bedeutung, und ein Hinweis in `primaryContainer` soll nicht je nach
  Wandbild wie eine Warnung aussehen.

### Nebenbei geprüft
- Der Vorgabewert von `cockpit.versionCode` stand auf **2**, ausgeliefert war
  **4**. Ein Bau ohne gesetzte Property hätte ein Downgrade erzeugt, das
  Android wortlos ablehnt.
- Signatur der neuen APK gegen die ausgelieferte verglichen (beide
  `1039220b57be…`): dieses Update legt sich über die installierte App, anders
  als der Sprung auf 2.0.4.

## Wahrheit der Anzeige (2026-08-29)

Die App war funktional vollständig und sagte trotzdem drei Dinge, die nicht
stimmten. Alle drei lagen nicht in der Oberfläche, sondern in den Daten, die
sie bekam.

### Was falsch war
- **Jeder beendete Container galt als Problem.** Auf dem Hauptknoten waren das
  dauerhaft elf, und alle elf sind Absicht (Rückfall-Instanzen, Dienste auf
  Abruf, ein Einmal-Job). Die Übersicht war also dauerhaft rot ohne Anlass.
- **`unhealthy` konnte gar nicht angezeigt werden.** Das Feld wurde vom Portal
  nie gesetzt; die App zeichnete deshalb jeden laufenden Container grün. Der
  Fehler war unsichtbar, weil ein fehlendes Feld wie „alles gesund" aussieht.
- **Der Host wurde unterwegs verworfen.** Das Gateway nahm `host` entgegen und
  ignorierte es: die App sah 93 von 196 Containern und behauptete mit ihrer
  Host-Gruppierung trotzdem, den ganzen Bestand zu zeigen. Beim Stoppen hätte
  es den gleichnamigen Container auf dem falschen Rechner getroffen.

### Was jetzt gilt
- Fünf Zustände statt zwei (`LAEUFT`/`AUFFAELLIG`/`BEWUSST_AUS`/`AUSGEFALLEN`/
  `UNBEWERTET`), abgeleitet aus `expected_state` und `in_control_map` der
  control-map. Ein Zustand ohne hinterlegte Erwartung wird als solcher benannt,
  nicht zum Ausfall erklärt.
- `health` kommt durch, aber nur für laufende Container: Docker behält den
  letzten Befund nach dem Stoppen bei, ein roher Durchgriff hätte die elf
  Fehlalarme nur umbenannt.
- Alle steuerbaren Hosts, mit Host-Auswahl in der Dienste-Liste. Die Übersicht
  holt dafür einen Zählstand (`/v1/ops/hosts`) statt knapp zweihundert
  Container-Einträgen über die VPN-Strecke; die Container-Liste eines Hosts
  wird nur geladen, wenn dort überhaupt etwas gemeldet wird.
- Die Übersicht führt mit dem Befundstand, nicht mit der Laufzahl.

### Bedienung
- Suche über Name und Image, Filter nach Zustand mit Trefferzahl je Filter.
- Logs springen ans Ende (die interessante Zeile ist die letzte), lassen sich
  kopieren und einzeln neu laden.
- Start/Stop nur dort anklickbar, wo sie etwas bewirken.
- Der Demo-Modus zeigt jeden der fünf Zustände; sein Zählstand wird aus den
  Beispieldiensten gerechnet statt danebengeschrieben.

## Härtung & Portfolio-Aufbereitung

Sicherheits- und Robustheits-Welle; keine Funktion entfernt, bestehende
Architektur schrittweise verbessert.

### Sicherheit
- QR-`gateway_url` und `ca_fingerprint` werden jetzt **genutzt**: dynamische
  Gateway-Basis-URL + Fingerprint-Pinning der CA beim Pairing.
- **mTLS** im laufenden API-Verkehr: der gerätegebundene Keystore-Schlüssel +
  Client-Zertifikat werden über einen `X509ExtendedKeyManager` präsentiert;
  TrustManager pinnt die Homelab-CA (kein „trust-all"). Inert über HTTP.
- JWT und Zertifikate liegen **verschlüsselt** im DataStore (AndroidKeyStore-
  AES-GCM) statt im Klartext.
- Globales `usesCleartextTraffic` entfernt → `network_security_config`:
  Release verbietet Klartext (TLS-Pflicht), Debug erlaubt HTTP über VPN.
- Update-URLs werden gegen **Same-Origin** zum Gateway validiert; Herkunft/
  Integrität über Androids APK-Signaturprüfung (dokumentiert).
- Schreibende Aktionen einheitlich bestätigt + optionale **Biometrie/PIN**-
  Zusatzschicht.

### Robustheit
- Auth-Interceptor ohne `runBlocking` (In-Memory-`SessionState`).
- Ein `401` löscht nicht mehr den kompletten Pairing-Zustand, sondern
  signalisiert nur eine abgelaufene Sitzung.

### Qualität
- **Öffentlicher Demo-Modus** (offline, statische Beispieldaten).
- **Unit-/Integrationstests** (JVM): CertUtils, UpdateUrl, Extensions,
  QR-Parsing, OpsRepository (inkl. Demo).
- **CI**: Tests + Lint + Debug-Build; kein irreführendes Debug-Release mehr,
  minimale Rechte (`contents: read`).
- **Doku**: README neu, `docs/ARCHITECTURE.md`, `docs/SECURITY.md`,
  `docs/API_CONTRACT.md`.
