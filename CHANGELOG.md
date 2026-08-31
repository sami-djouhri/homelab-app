# Changelog

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
