# Changelog

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
