# Gateway-Vertrag (`mobile-api`)

Die App ist ein dünner Client. Dieses Dokument hält den erwarteten Vertrag fest,
den das Gateway erfüllen und **serverseitig durchsetzen** muss. Beispiel-Host:
`https://gateway.internal:8140` (die reale Adresse kommt zur Laufzeit aus dem QR).

Alle Endpunkte außer Pairing/Health verlangen `Authorization: Bearer <jwt>`.
Autorisierung, Rate-Limiting und Eingabevalidierung liegen beim Gateway.

## Auth / Pairing

| Methode | Pfad | Auth | Zweck |
|---------|------|------|-------|
| `POST` | `/v1/auth/pair` | keine (einmalige Nonce) | CSR + Nonce → JWT + Client-/CA-Zertifikat |
| `GET` | `/v1/health` | keine | Liveness-Probe (Verbindungstest) |

`POST /v1/auth/pair` - Request:
```json
{ "nonce": "<einmalig, kurzlebig>", "csr_pem": "<PKCS#10>", "device_label": "Pixel 8" }
```
Response:
```json
{ "device_id": "<hash>", "jwt": "<token>",
  "client_cert_pem": "<PEM>", "ca_cert_pem": "<PEM>" }
```
Server-Pflichten: Nonce **einmalig** und ablaufend; CSR-Signatur prüfen;
Client-Cert von der Homelab-CA signieren; JWT mit begrenzter Gültigkeit ausstellen.

Der QR-Code, den die interne Pairing-Seite erzeugt, trägt:
```json
{ "gateway_url": "https://gateway.internal:8140",
  "ca_fingerprint": "AB:CD:…", "nonce": "…" }
```
Die App verifiziert `sha256(ca_cert_pem) == ca_fingerprint`.

## Ops

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/ops/hosts` | Steuerbare Hosts mit Zählstand (ohne Container-Liste) |
| `GET` | `/v1/ops/services?host=` | Container-Liste eines Hosts |
| `POST` | `/v1/ops/services/{host}/{name}/action?action=start\|stop\|restart` | Aktion ausführen |
| `GET` | `/v1/ops/logs/{host}/{name}?lines=` | Log-Ausschnitt |

Server-Pflichten: `action` gegen eine Allowlist prüfen; nur erlaubte Hosts/
Container; keine Shell-Injection über `name`/`host`. **`host` muss bis zum
Docker-Endpunkt durchgereicht werden.** Ein verworfener Hostname ist beim Lesen
ein Anzeigefehler, beim Stoppen der falsche Rechner.

`GET /v1/ops/services` liefert je Container:

```json
{ "host": "node1", "name": "immich", "status": "running",
  "health": "healthy", "image": "…", "ports": [],
  "expected_state": "", "in_control_map": true }
```

Zwei Felder tragen die ganze Einordnung:

- **`health`** darf nur gesetzt sein, solange der Container **läuft**. Docker
  behält den letzten Health-Befund nach dem Stoppen bei; ein roher Durchgriff
  meldet beendete Container als `unhealthy`. `null` heißt „keine Aussage", nicht
  „gesund".
- **`expected_state`** ist die control-map-Erwartung: `""` = soll laufen,
  `stopped`/`manual`/`migrated`/`job` = steht bewusst still. **`in_control_map`**
  trennt „soll laufen" von „steht nirgends"; ohne das Feld bedeutet ein leeres
  `expected_state` beides zugleich. Für Remote-Hosts sind beide `null`, weil die
  control-map ihre Erwartung nur für den lokalen Host führt.

Der Client leitet daraus fünf Zustände ab (`ServiceZustand`): `LAEUFT`,
`AUFFAELLIG`, `BEWUSST_AUS`, `AUSGEFALLEN`, `UNBEWERTET`. Nur die beiden
Problemfälle (`AUFFAELLIG`, `AUSGEFALLEN`) erscheinen in der Übersicht.

`GET /v1/ops/hosts` liefert denselben Zählstand aggregiert:

```json
{ "hosts": [
  { "name": "host", "erreichbar": true, "gesamt": 93, "laufend": 82,
    "auffaellig": 0, "bewusst_aus": 11, "ausgefallen": 0, "unbewertet": 0 },
  { "name": "jetson", "erreichbar": false }
] }
```

Die fünf Fächer **müssen sich auf `gesamt` summieren** und dieselbe Grenze
ziehen wie die Einzel-Einordnung, sonst kann die Übersicht „1 auffällig"
melden und die Liste darunter nichts Auffälliges enthalten. Ein nicht
erreichbarer Host liefert **gar keine Zahlen**: eine `0` wäre die Aussage
„dort läuft nichts".

## Dashboard

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/dashboard/summary` | Host-Metriken (`health`) + `inbox_counts` |

Die App nutzt aus der Summary bewusst nur `health` und `inbox_counts`.

## Inbox

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/inbox?limit=&include_done=` | Liste + Zähler |
| `POST` | `/v1/inbox/note` | Notiz anlegen (`title`, `content`, `tags`) |
| `POST` | `/v1/inbox/{external_id}/action` | Triage (`done`/`snooze`/`archive`), optional `snooze_minutes` |

## Update

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/app-version` | `{ versionCode, versionName, changelog, apkUrl, sha256? }` |

`apkUrl` muss **Same-Origin** zum Gateway sein (die App lehnt fremde Hosts ab).
Die Integrität sichert die APK-Signaturprüfung von Android; `sha256` ist optional
und wird nur angezeigt.

## Optionaler mTLS-Modus (Phase 2)

Antwortet das Gateway über TLS mit Client-Zertifikatsprüfung, präsentiert die App
ihr Client-Cert. Das Gateway bindet dann den JWT-`sub` an die Zertifikats-CN
(z. B. über `X-SSL-Client-Verify: SUCCESS` + `X-SSL-Client-Sub` vom TLS-
terminierenden Reverse-Proxy) und weist Nichtübereinstimmungen ab.
