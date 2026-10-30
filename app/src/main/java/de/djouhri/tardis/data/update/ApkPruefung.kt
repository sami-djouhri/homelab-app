package de.djouhri.tardis.data.update

/**
 * Was in einer geladenen APK steht, soweit es fuer die Uebergabe zaehlt.
 *
 * @param zertifikate SHA-256 der Signaturzertifikate, klein geschrieben.
 *   **Leer heisst „nicht lesbar", nicht „unsigniert"**: Android liest die
 *   Signatur eines Archivs nicht auf jeder Version, und zwei leere Mengen
 *   beweisen nichts. Eine leere Menge fuehrt deshalb weder zu einem Einwand
 *   noch zu einer Zusage, das letzte Wort hat dann der Systeminstallateur.
 */
data class ApkBefund(
    val paket: String,
    val versionCode: Long,
    val zertifikate: Set<String>,
)

/**
 * Vorpruefung einer geladenen Update-APK, BEVOR sie an den Systeminstallateur
 * geht.
 *
 * Die sha256 aus dem Manifest belegt nur, dass die Datei unterwegs nicht
 * veraendert wurde. Ob es die RICHTIGE Datei ist, sagt sie nicht: sie wird in
 * `ausliefern.sh` von genau der Datei genommen, die dort uebergeben wurde. Drei
 * Fehler kommen so durch, und alle drei sieht man auf dem Geraet erst im
 * Systemdialog, ohne Grund:
 *
 *  - **Falsche Versionsnummer.** `ausliefern.sh` schreibt sie
 *    und prueft das Dateialter, eine von Hand gebaute APK aus einem anderen
 *    Stand kommt trotzdem durch. Android installiert sie, die App meldet sich
 *    danach weiter mit der alten Nummer, und der Updater bietet dasselbe
 *    Update bei jedem Start erneut an.
 *  - **Falsche App.** Eine andere APK als `tardis.apk` am Gateway wuerde als
 *    zweite App daneben installiert.
 *  - **Falscher Schluessel.** Ein lokal gebautes `assembleRelease` ist
 *    unsigniert oder mit dem Debug-Schluessel signiert. Android lehnt es mit
 *    „App nicht installiert" ab, und das liest sich wie ein kaputtes Telefon.
 *
 * Diese Klasse entscheidet nur und ist damit ohne Geraet pruefbar; das Auslesen
 * der APK steht in `AndroidApkPruefung.kt`. Gleicher Stand wie in
 * `saganta-apps/core`, `lernen-android/core` und `bibliothek-android`
 * (Kopie, weil die Projekte eigenstaendig sind).
 */
object ApkPruefung {

    /** `null` heisst: darf uebergeben werden. Sonst der Grund im Klartext. */
    fun einwand(
        befund: ApkBefund?,
        eigenesPaket: String,
        installierteVersion: Long,
        angekuendigteVersion: Long,
        eigeneZertifikate: Set<String>,
    ): String? = when {
        befund == null ->
            "Die geladene Datei ist keine lesbare App."
        befund.paket != eigenesPaket ->
            "Die geladene Datei gehört zu einer anderen App (${befund.paket})."
        befund.versionCode != angekuendigteVersion ->
            "Angekündigt war Version $angekuendigteVersion, die Datei ist Version " +
                "${befund.versionCode}. Das Update klappt erst nach einer neuen Veröffentlichung."
        befund.versionCode <= installierteVersion ->
            "Die Datei ist nicht neuer als die installierte Version."
        befund.zertifikate.isNotEmpty() && eigeneZertifikate.isNotEmpty() &&
            befund.zertifikate.intersect(eigeneZertifikate).isEmpty() ->
            "Die Datei ist mit einem anderen Schlüssel signiert. Android nimmt sie nur nach " +
                "einer Deinstallation an, und dabei gingen die Daten der App verloren."
        else -> null
    }
}
