package de.djouhri.tardis.data.repository

/**
 * Der Ablauf des Entkoppelns als reine Funktion, und die Deutung der
 * Widerrufs-Anzahl. Beides liegt hier, damit es pruefbar ist.
 *
 * Der Grund fuer diese Datei: [PairingRepository] haengt an DataStore und
 * AndroidKeyStore und ist in einem JVM-Unit-Test nicht instanziierbar (das
 * Projekt hat bewusst nur `junit` + `coroutines-test`, kein Robolectric und
 * kein Mock-Werkzeug). Die Reihenfolge ist aber genau das Stueck, das nicht
 * verrutschen darf, und eine Regel ohne Test ist hier ein Vorsatz.
 */

/**
 * Widerruf beim Gateway, danach lokales Aufraeumen.
 *
 * Zwei Zusagen, und beide sind getestet:
 * 1. [widerrufen] laeuft **vor** [lokalRaeumen]. Die Gateway-Route braucht das
 *    noch gueltige Token; nach dem lokalen Loeschen waere es eine 401.
 * 2. [lokalRaeumen] laeuft **immer**, auch wenn [widerrufen] scheitert. Ohne
 *    VPN oder bei totem Gateway muss man die App loswerden koennen.
 *
 * Das Ergebnis von [widerrufen] wird unveraendert weitergegeben und **nicht**
 * verschluckt: ein stilles Scheitern erzeugt genau die Annahme, die ein
 * Widerruf ausschliessen soll, naemlich das Geraet sei draussen.
 */
suspend fun entkoppelAblauf(
    widerrufen: suspend () -> Result<Boolean>,
    lokalRaeumen: suspend () -> Unit,
): Result<Boolean> {
    val ergebnis = widerrufen()
    lokalRaeumen()
    return ergebnis
}

/**
 * Klartext zur Anzahl widerrufener Token.
 *
 * `0` ist **kein** Fehler: das Gateway antwortet dann bewusst mit 200 statt
 * 404, weil ein 404 eine Auskunft darueber waere, welche Geraete-Kennungen es
 * gibt. Gemeldet wird deshalb die Zahl, nicht ein Erfolg.
 *
 * Mehr als 1 ist moeglich und gewollt: ein zweimal gekoppeltes Geraet haette
 * sonst nach dem Abmelden noch ein zweites gueltiges Token.
 */
fun widerrufHinweis(anzahl: Int): String = when {
    anzahl > 1 -> "$anzahl Token widerrufen."
    anzahl == 1 -> "1 Token widerrufen."
    else -> "Nichts zu widerrufen (unbekannt oder schon widerrufen)."
}
