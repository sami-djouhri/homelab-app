# Sicherheit & Bedrohungsmodell

Dieses Dokument beschreibt, was die App **tatsächlich** durchsetzt, welche
Annahmen sie trifft und wo die Grenze zwischen client- und serverseitiger
Kontrolle verläuft. Es beschreibt bewusst keine Fähigkeiten, die der Code nicht
erfüllt.

## Grundhaltung

Die App ist ein **Client** für ein privates Gateway (`mobile-api`) im internen
Netz/VPN. Sicherheitskritische Kontrollen - **Authentifizierung, Autorisierung,
Rate-Limiting, Eingabevalidierung** - werden **serverseitig** durchgesetzt. Die
clientseitigen Prüfungen hier sind **zusätzliche Schutzschichten**
(Defense-in-Depth), niemals der alleinige Kontrollpunkt.

## Identität & Authentifizierung

- **Gerätegebundener Schlüssel:** Beim Pairing erzeugt die App ein EC-P256-
  Schlüsselpaar im **AndroidKeyStore** (hardware-backed, falls verfügbar). Der
  private Schlüssel ist **non-exportable** und verlässt das Gerät nie. Für das
  Pairing wird nur ein PKCS#10-CSR erzeugt und mit dem Keystore-Schlüssel signiert.
- **Laufende Requests** authentifizieren sich mit einem **Bearer-JWT**, das das
  Gateway beim Pairing ausstellt.
- **Mutual TLS (mTLS):** Die App präsentiert das gerätegebundene Client-Zertifikat
  über einen `X509ExtendedKeyManager`, der mit dem Keystore-Schlüssel signiert -
  **aktiv, sobald das Gateway über TLS mit Client-Zertifikatsprüfung antwortet**.
  In diesem Modus bindet das Gateway den JWT-`sub` an die Zertifikats-CN; ein
  gestohlenes Token ist dann ohne den (nicht exportierbaren) Geräteschlüssel
  nutzlos. Über Klartext-HTTP (Phase-1-VPN) ist mTLS inert und es trägt allein
  das JWT.

> Ehrlich eingeordnet: „device-bound" gilt vollständig **nur im mTLS-Modus**.
> Läuft das Gateway als HTTP über VPN, ist die Sitzung durch das VPN + JWT
> geschützt, aber nicht kryptografisch ans Gerät gebunden.

## Vertrauensanker (CA-Pinning)

Der QR-Code enthält `gateway_url`, `ca_fingerprint` und eine einmalige `nonce` -
**alle drei werden verwendet**:

- `gateway_url` setzt die Laufzeit-Basis-URL (kein hartkodiertes Ziel).
- Nach dem Pairing wird `sha256(ca_cert_pem)` gegen den QR-`ca_fingerprint`
  geprüft. Stimmt er nicht, bricht das Pairing ab - ein untergeschobener
  Pairing-Server kann so keine Fremd-CA einschleusen.
- Der TrustManager validiert Server-Zertifikate anschließend gegen genau diese
  gepinnte CA. **Es gibt keinen „trust-all"-Pfad.**

## Speicherung von Geheimnissen

- JWT und Zertifikate werden vor dem Schreiben mit einem AndroidKeyStore-**AES-256-
  GCM**-Schlüssel verschlüsselt (`SecretCipher`) und liegen **nicht im Klartext**
  im DataStore.
- `allowBackup=false`; DataStore ist zusätzlich in `backup_rules`/
  `data_extraction_rules` von Cloud-Backup und Geräte-Transfer ausgeschlossen.
- Ist ein Wert unlesbar (Schlüssel rotiert/entfernt), gilt er als „nicht
  vorhanden" → erneutes Pairing.

## Transportsicherheit

- **Release:** `network_security_config` mit `cleartextTrafficPermitted="false"`
  - HTTP ist verboten, TLS Pflicht.
- **Debug/Sideload:** eine Ressourcen-Überschreibung erlaubt Klartext (HTTP über
  VPN, Phase 1). So kann kein Release-Artefakt versehentlich unverschlüsselt reden.
- Kein HTTP-Body-Logging (kein Token-Leak in Logs).

## Sitzungs-Robustheit

Ein `401` **löscht nicht** mehr den kompletten Pairing-Zustand (Keystore-Key,
Zertifikat). Er setzt nur ein `sessionExpired`-Signal; der Nutzer koppelt bei
Bedarf bewusst neu. Ein transienter 401 erzwingt so kein Zwangs-Re-Pairing.

## Schreibende Aktionen

Start/Stop/Restart und das Entkoppeln werden **einheitlich bestätigt**. Ist eine
Gerätesicherung eingerichtet, verlangt die App zusätzlich eine **Biometrie-/PIN-
Bestätigung** (`BiometricPrompt`, ab API 30 mit Geräte-Credential-Fallback). Das
ist eine lokale Zusatzschicht - die eigentliche Autorisierung macht das Gateway.

## In-App-Update

Der Updater übergibt die APK-URL an den System-Installer. **Clientseitige
Zusatzprüfung:** die URL muss **Same-Origin** zum gekoppelten Gateway sein
(Schema, Host **und** Port); fremde Hosts / `http↔https`-Downgrades werden
abgelehnt. Die eigentliche **Integritäts- und Herkunftsgarantie** liefert Androids
**APK-Signaturprüfung** (v2/v3): ein Update installiert nur, wenn es mit demselben
Schlüssel signiert ist. Ein optionaler `sha256` aus `/app-version` wird dem Nutzer
zusätzlich angezeigt.

## Bewusste Grenzen / Annahmen

- Das Gateway ist erreichbar über VPN/internes Netz und setzt Auth/AuthZ/
  Rate-Limiting serverseitig durch (siehe `docs/API_CONTRACT.md`).
- Ohne eingerichtete Gerätesicherung entfällt die Biometrie-Schicht; es bleibt die
  textuelle Bestätigung.
- Auf API 28–29 ist der PIN-Fallback des `BiometricPrompt` nicht über diese API
  kombinierbar; dort greift die textuelle Bestätigung.
- Der Demo-Modus enthält ausschließlich statische, neutrale Beispieldaten.
