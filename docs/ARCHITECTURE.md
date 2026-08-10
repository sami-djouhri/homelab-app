# Architektur

## Überblick

Single-Activity-Compose-App nach **MVVM** mit unidirektionalem Datenfluss.
Abhängigkeiten werden über **Hilt** bereitgestellt. Kein `Fragment`-Backstack -
Navigation läuft über `navigation-compose` mit typsicheren Routen.

```
UI (Compose)  ─►  ViewModel (StateFlow)  ─►  Repository  ─►  Retrofit-API  ─►  Gateway
     ▲                                            │
     └──────────────  UiState  ◄──────────────────┘
                                                  └─►  DemoModeManager (Offline-Beispieldaten)
```

## Schichten

| Schicht | Ort | Aufgabe |
|---------|-----|---------|
| UI | `ui/**`, `navigation/**` | Compose-Screens, Scaffold, Banner, Statuskomponenten |
| Präsentation | `*ViewModel` je Screen | hält `UiState` als `StateFlow`, ruft Repositories |
| Daten | `data/repository/**` | kapselt API + Demo-Verzweigung, gibt `Result<…>` zurück |
| Transport | `data/api/**` (Retrofit) + `di/AppModule` | HTTP, Auth-Interceptor, mTLS, Serialisierung |
| Modelle | `data/model/cockpit/**` | `@Serializable` DTOs des Gateway-Vertrags |
| Sicherheit | `security/**` | Keystore, CSR, Fingerprint, mTLS, verschlüsselter Speicher, Session |
| Lokaler Speicher | `data/local/SettingsStore` | DataStore (verschlüsselte Auth-Artefakte) |

## Zustands- und Auth-Fluss

- **`SessionState`** ist der In-Memory-Spiegel des Auth-Zustands (Token, Gateway-
  URL, CA/Client-Cert, `sessionExpired`). Er wird asynchron aus dem
  `SettingsStore` gepflegt, damit der OkHttp-Interceptor **ohne `runBlocking`**
  arbeitet.
- Der **Auth-Interceptor** (`di/AppModule`) setzt pro Request den `Authorization:
  Bearer`-Header und schreibt die Ziel-Basis-URL aus `SessionState` (die aus dem
  QR stammende Gateway-Adresse). Ein `401` **signalisiert** nur eine abgelaufene
  Sitzung (`sessionExpired`) - er löscht **nicht** den Pairing-Zustand.
- **`RootViewModel`** entscheidet zwischen `PAIRING` und `COCKPIT` (inkl.
  Demo-Modus) und reicht `demoActive`/`sessionExpired` an das Scaffold-Banner.

## Pairing-Sequenz

1. QR scannen → `QrPayload` (`gateway_url`, `ca_fingerprint`, `nonce`).
2. `SessionState.overrideGateway(gateway_url)` - Folge-Requests gehen ans QR-Gateway.
3. EC-Schlüsselpaar im AndroidKeyStore erzeugen, PKCS#10-CSR bauen.
4. `POST /v1/auth/pair` (nonce + CSR) → Antwort: `jwt`, `client_cert_pem`, `ca_cert_pem`.
5. **Fingerprint-Pinning:** `sha256(ca_cert_pem)` muss zum QR-`ca_fingerprint` passen.
6. Verschlüsselt persistieren (JWT/Certs) + Gateway/Fingerprint speichern.

## Demo-Modus

`DemoModeManager` (In-Memory-Flag). Ist er aktiv, liefern die Repositories
`DemoData` und es geht **kein** Request ins Netz. So ist die App ohne
Gateway/Pairing öffentlich erlebbar. Ein Neustart verlässt den Demo-Modus.

## Build-Varianten & Verteilung

- **Debug:** erlaubt Klartext-HTTP (VPN, Phase 1), trägt eine eigene
  `network_security_config`-Überschreibung. CI baut dieses Artefakt als
  Kompilier-/Testnachweis (unsigniert).
- **Release:** verbietet Klartext (TLS-Pflicht), `minify`/`shrinkResources` an.
  Der **signierte** Release entsteht in der internen Verteil-Pipeline; das
  öffentliche Repo veröffentlicht keine installierbaren Releases.

## Tests

- **Unit (JVM):** `security/CertUtils`, `util/UpdateUrl`, `util/Extensions`,
  QR-Parsing.
- **Integration (JVM):** `OpsRepository` gegen ein Fake-`OpsApi` (inkl.
  Demo-Verzweigung, `kotlinx-coroutines-test`).
- Keystore-/Biometrie-Pfade sind gerätegebunden (AndroidKeyStore) und daher
  nicht Teil der JVM-Suite; ihre reine Logik ist in testbare Funktionen ausgelagert.
