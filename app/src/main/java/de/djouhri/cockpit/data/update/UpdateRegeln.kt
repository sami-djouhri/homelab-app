package de.djouhri.cockpit.data.update

/**
 * Die Entscheidungen der Update-Kette, ohne Android und ohne Netz.
 *
 * Bis 2026-09-27 lief das Update so: in den Einstellungen auf "Auf Updates
 * pruefen" tippen, dann uebernahm der Browser. Zwei Dinge fehlten daran. Erstens
 * musste man daran denken; die installierte App war einen Monat alt, ohne dass
 * es jemandem auffiel. Zweitens wurde die angezeigte Pruefsumme nie geprueft,
 * sie stand nur da.
 *
 * Beides ist hier entschieden, damit es pruefbar ist: der Rest der Kette
 * (Netz, Dateisystem, Installer) haengt an Android und laesst sich nur auf
 * einem Geraet testen.
 */
object UpdateRegeln {

    /**
     * Abstand zwischen zwei selbsttaetigen Pruefungen.
     *
     * Sechs Stunden, nicht bei jedem Start: die Pruefung geht ueber WireGuard
     * ans Gateway, und wer die App zehnmal am Tag oeffnet, soll nicht zehnmal
     * eine Verbindung aufbauen, die er nicht angefordert hat.
     */
    const val PRUEF_ABSTAND_MS: Long = 6 * 60 * 60 * 1000L

    /** true, wenn seit der letzten Pruefung genug Zeit vergangen ist. */
    fun darfPruefen(jetzt: Long, letztePruefung: Long): Boolean =
        jetzt - letztePruefung >= PRUEF_ABSTAND_MS

    /**
     * Nur eine hoehere Nummer ist ein Update.
     *
     * Gleichstand ist kein Update, und eine NIEDRIGERE Nummer erst recht nicht:
     * Android lehnt ein Downgrade ohnehin ab, und ein Angebot, das sich nicht
     * installieren laesst, sieht wie ein Fehler der App aus.
     */
    fun istNeuer(angeboten: Int, installiert: Int): Boolean = angeboten > installiert

    /**
     * Vergleicht den gemessenen Hash der geladenen Datei mit dem angekuendigten.
     *
     * Gross- und Kleinschreibung und umschliessende Leerzeichen sind egal, der
     * Rest nicht. Fehlt die Ankuendigung, ist das KEIN Bestehen, sondern ein
     * eigener Fall: die Oberflaeche sagt dann, dass nicht verglichen werden
     * konnte, statt ein Haekchen zu zeigen, das nichts bedeutet.
     */
    fun hashUrteil(erwartet: String?, gemessen: String): HashUrteil {
        val soll = erwartet?.trim()?.lowercase().orEmpty()
        if (soll.isEmpty()) return HashUrteil.OHNE_VERGLEICH
        return if (soll == gemessen.trim().lowercase()) HashUrteil.PASST else HashUrteil.ABWEICHUNG
    }
}

enum class HashUrteil {
    /** Gemessen und gleich. */
    PASST,

    /** Gemessen und ungleich: die Datei wird nicht installiert, sondern geloescht. */
    ABWEICHUNG,

    /** Der Server hat keinen Hash genannt. Kein Urteil, und das wird gesagt. */
    OHNE_VERGLEICH,
}
