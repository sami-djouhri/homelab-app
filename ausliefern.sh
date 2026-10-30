#!/usr/bin/env bash
# Liefert eine gebaute Tardis-APK aus: nach mobile-api/dist, samt Manifest.
#
# WARUM ES DIESES SKRIPT GIBT. Bis zum 2026-10-10 war das Ausliefern
# Handarbeit: Datei kopieren, `tardis-version.json` von Hand schreiben. Die
# Zahl darin und die Zahl IN der APK kamen aus zwei verschiedenen Koepfen, und
# am 2026-10-09 liefen sie auseinander -- gebaut wurde ohne
# `-Ptardis.versionCode`, die APK trug also die Vorgabe 5, dieselbe Nummer wie
# die bereits ausgelieferte.
#
# Ein Downgrade faellt auf: der Installer weigert sich. Ein STILLSTAND nicht.
# Das Manifest haette 6 angekuendigt, die Datei darunter waere 5 gewesen, die
# App haette das Update geholt, installiert, sich danach weiter als 5 gemeldet
# und beim naechsten Blick erneut dasselbe Update angeboten. In Schleife, ohne
# Fehlermeldung, auf dem Telefon des Owners.
#
# Deshalb liest dieses Skript die Version AUS DER APK und schreibt sie ins
# Manifest, statt sie entgegenzunehmen. Es gibt hier keine zweite Zahl.
#
# Aufruf:
#   ./ausliefern.sh <apk> [--changelog <datei>] [--trocken]
# Beispiel:
#   ./ausliefern.sh /tmp/tardis-neu.apk
set -euo pipefail

HIER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ZIEL="${TARDIS_DIST:-/home/user/docker/mobile-api/dist}"

apk=""
changelog=""
trocken=0
while [ $# -gt 0 ]; do
    case "$1" in
        --changelog) changelog="$2"; shift 2 ;;
        --trocken)   trocken=1; shift ;;
        -*)          echo "Unbekannte Option: $1" >&2; exit 2 ;;
        *)           apk="$1"; shift ;;
    esac
done
[ -n "$apk" ] || { echo "FEHLER: welche APK? Aufruf: ./ausliefern.sh <apk>" >&2; exit 2; }
[ -f "$apk" ] || { echo "FEHLER: $apk gibt es nicht." >&2; exit 2; }

# --- Was steckt wirklich drin? ----------------------------------------------
#
# `aapt2` gibt es auf host nicht (arm64, kein Build-Tools-Paket fuer SDK
# 35+). Gelesen wird deshalb der Stringpool der binaeren AndroidManifest.xml:
# `versionName` steht dort als UTF-16, und weil die Vorlage "2.0.$versionCode"
# lautet, traegt sie den versionCode mit. Das ist keine vollstaendige
# AXML-Analyse, aber es ist eine MESSUNG an der Datei und keine Annahme ueber
# den Bau.
messung="$(python3 - "$apk" <<'PY'
import re, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    roh = z.read("AndroidManifest.xml")
    signiert = any(n.startswith("META-INF/") and n.endswith((".RSA", ".EC", ".DSA"))
                   for n in z.namelist())
    v2 = b"APK Sig Block 42" in open(sys.argv[1], "rb").read()[-1024 * 1024:]
namen = sorted(set(re.findall(r"\d+\.\d+\.\d+",
                              roh.decode("utf-16-le", errors="ignore"))))
if len(namen) != 1:
    sys.exit(f"Im Manifest steht keine eindeutige Version: {namen}")
name = namen[0]
code = name.rsplit(".", 1)[1]
print(f"{name} {code} {int(signiert or v2)}")
PY
)" || { echo "FEHLER: Version liess sich nicht aus der APK lesen." >&2; exit 1; }

read -r versionName versionCode signiert <<<"$messung"
groesse="$(stat -c %s "$apk")"
pruefsumme="$(sha256sum "$apk" | cut -d' ' -f1)"

echo "== Gemessen an der Datei =="
echo "  Datei       $apk"
echo "  versionName $versionName"
echo "  versionCode $versionCode"
echo "  Groesse     $groesse Byte"
echo "  sha256      $pruefsumme"
echo "  signiert    $([ "$signiert" = 1 ] && echo ja || echo NEIN)"

if [ "$signiert" != 1 ]; then
    cat >&2 <<'HINWEIS'

FEHLER: Diese APK ist nicht signiert.

  Der Buildhost erzeugt bei fehlendem Schluessel bewusst ein UNSIGNIERTES
  Release statt eines Fehlschlags (damit der oeffentliche CI-Lauf durchlaeuft).
  Ausliefern laesst sie sich nicht: Android installiert sie nicht, und ein
  Signaturwechsel waere ohnehin eine Neuinstallation.
HINWEIS
    exit 1
fi

# --- Gegen den ausgelieferten Stand halten ----------------------------------
alt_code=0
if [ -f "$ZIEL/tardis-version.json" ]; then
    alt_code="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1])).get('versionCode',0))" \
                "$ZIEL/tardis-version.json")"
fi
echo
echo "== Gegen den ausgelieferten Stand =="
echo "  ausgeliefert ist versionCode $alt_code, neu waere $versionCode"

if [ "$versionCode" -le "$alt_code" ]; then
    cat >&2 <<HINWEIS

FEHLER: $versionCode ist nicht groesser als der ausgelieferte Stand $alt_code.

  Gleich heisst STILLSTAND, und der ist der unangenehmere Fall: die App haette
  das Update geholt, installiert, sich weiter als $alt_code gemeldet und es bei
  der naechsten Pruefung erneut angeboten. In Schleife, ohne Fehlermeldung.

  Der versionCode wird in app/build.gradle.kts hochgezaehlt, VOR dem Bauen.
HINWEIS
    exit 1
fi

# --- Changelog --------------------------------------------------------------
#
# Genommen wird der VORSPANN des obersten Abschnitts, also der Text zwischen
# der `## `-Ueberschrift und der ersten `### `-Zeile -- und nicht der ganze
# Abschnitt. Darunter steht, was am Code anders ist (Dateinamen, Routen,
# Begruendungen); der Owner liest den Text in einem Dialog auf dem Telefon und
# will wissen, was seine App jetzt anders macht. Ein Ort fuer beides, zwei
# Leserkreise, und die Trennlinie ist die Ueberschriftenebene.
if [ -z "$changelog" ]; then
    text="$(python3 - "$HIER/CHANGELOG.md" <<'PY'
import re, sys
roh = open(sys.argv[1], encoding="utf-8").read()
teile = roh.split("\n## ")
if len(teile) < 2:
    sys.exit("In CHANGELOG.md steht kein Abschnitt mit '## '.")
rumpf = teile[1].split("\n", 1)[1]
rumpf = rumpf.split("\n### ")[0]
# HTML-Kommentare raus: sie richten sich an den, der die Datei pflegt.
rumpf = re.sub(r"<!--.*?-->", "", rumpf, flags=re.S)
# Markdown-Auszeichnung entschaerfen, der Text landet als Klartext im Dialog.
rumpf = rumpf.replace("**", "").replace("`", "")
rumpf = re.sub(r"\n{3,}", "\n\n", rumpf)
if len(rumpf.strip()) < 80:
    sys.exit("Der Vorspann des obersten Abschnitts ist fast leer. Steht der "
             "Text fuer den Owner dort, vor der ersten ###-Zeile?")
print(rumpf.strip())
PY
)"
else
    [ -f "$changelog" ] || { echo "FEHLER: $changelog gibt es nicht." >&2; exit 2; }
    text="$(cat "$changelog")"
fi
echo
echo "== Changelog (erste 3 Zeilen) =="
printf '%s\n' "$text" | head -3 | sed 's/^/  /'

if [ "$trocken" = 1 ]; then
    echo
    echo "-- Trockenlauf, nichts geschrieben."
    exit 0
fi

# --- Schreiben --------------------------------------------------------------
#
# Erst die APK, dann das Manifest. Andersherum gaebe es ein Zeitfenster, in
# dem `/app-version` eine Version ankuendigt, die `/tardis.apk` noch nicht
# liefert -- die App laedt dann die alte Datei und prueft deren Pruefsumme gegen
# die neue. Sie bricht sauber ab, aber der Fehler sieht nach Uebertragungs-
# problem aus und nicht nach einer halb fertigen Auslieferung.
echo
echo "== Ausliefern nach $ZIEL =="
install -m 660 "$apk" "$ZIEL/tardis-$versionName.apk"
install -m 660 "$apk" "$ZIEL/tardis.apk"
echo "  tardis-$versionName.apk + tardis.apk geschrieben"

# Der Changelog-Text geht ueber die UMGEBUNG, nicht ueber stdin: `python3 -`
# liest sein Programm von dort. Ein zweiter stdin-Umleiter in derselben Zeile
# haette das Programm durch den Changelog ersetzt, und die Fehlermeldung waere
# ein SyntaxError in einem deutschen Satz gewesen.
TARDIS_CHANGELOG="$text" python3 - "$ZIEL/tardis-version.json" "$versionCode" "$versionName" "$pruefsumme" <<'PY'
import json, os, sys
ziel, code, name, summe = sys.argv[1:5]
with open(ziel, "w", encoding="utf-8") as f:
    json.dump({"versionCode": int(code), "versionName": name,
               "changelog": os.environ["TARDIS_CHANGELOG"].strip(),
               "sha256": summe},
              f, ensure_ascii=False, indent=2)
    f.write("\n")
PY
echo "  tardis-version.json geschrieben"

# --- Gegenlesen -------------------------------------------------------------
#
# Gegen die AUSGELIEFERTE Datei, nicht gegen die Quelle. Ein `install`, das
# auf halber Strecke abbricht, hinterlaesst eine kuerzere Datei mit einer
# anderen Pruefsumme -- und das Manifest behauptet dann etwas ueber Bytes, die
# so nicht dort liegen.
echo
echo "== Gegengelesen an der ausgelieferten Datei =="
python3 - "$ZIEL" <<'PY'
import hashlib, json, pathlib, sys
d = pathlib.Path(sys.argv[1])
m = json.loads((d / "tardis-version.json").read_text())
roh = (d / "tardis.apk").read_bytes()
echt = hashlib.sha256(roh).hexdigest()
ok = echt == m["sha256"]
print(f"  versionCode {m['versionCode']}  versionName {m['versionName']}")
print(f"  Groesse     {len(roh)} Byte")
print(f"  sha256      {'stimmt' if ok else 'WEICHT AB: ' + echt}")
sys.exit(0 if ok else 1)
PY

cat <<'HINWEIS'

-- Noch zu tun --
  * mobile-api neu starten ist NICHT noetig: dist/ ist read-only eingehaengt und
    wird je Anfrage gelesen.
  * Der Owner muss die App einmal neu koppeln (Gateway-Umstellung vom 2026-09-27).
  * Den Vorgabewert in app/build.gradle.kts auf den eben ausgelieferten Stand
    setzen, falls per -Ptardis.versionCode gebaut wurde.
HINWEIS
