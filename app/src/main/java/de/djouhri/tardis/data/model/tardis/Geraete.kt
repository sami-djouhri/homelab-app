package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Antwort von `POST /v1/auth/unpair` und
 * `POST /v1/auth/geraete/{id}/widerrufen`.
 *
 * `widerrufen` ist die Anzahl der widerrufenen Token, nicht ein Erfolgskennzeichen.
 * **0 ist kein Fehler**: es heisst „kenne ich nicht oder war schon widerrufen".
 * Das Gateway antwortet in diesem Fall bewusst mit 200 statt 404, weil ein 404
 * eine Auskunft darueber waere, welche Geraete-Kennungen es gibt. Die
 * Oberflaeche nennt deshalb die Zahl, statt einen Erfolg zu behaupten.
 *
 * Mehr als 1 ist moeglich und gewollt: ein zweimal gekoppeltes Geraet haette
 * sonst nach dem Abmelden noch ein zweites gueltiges Token.
 */
@Serializable
data class WiderrufResponse(
    @SerialName("geraet_id") val geraetId: String = "",
    val widerrufen: Int = 0,
)

/** Antwort von `GET /v1/auth/geraete`. */
@Serializable
data class GeraeteResponse(
    val geraete: List<Geraet> = emptyList(),
)

/**
 * Ein beim Gateway bekanntes Geraet. Zeiten sind Unix-Sekunden.
 *
 * `jtiKurz` sind bewusst nur die ersten acht Zeichen der Token-Kennung: ohne
 * Signatur ist sie keine Berechtigung, aber sie gehoert auch nicht vollstaendig
 * in eine Uebersicht. Sie dient hier dazu, zwei Kopplungen desselben Geraets
 * auseinanderzuhalten.
 *
 * `gueltig` rechnet das Gateway aus (nicht widerrufen UND nicht abgelaufen).
 * Bewusst nicht in der App nachgerechnet: die Uhr des Telefons ist nicht die
 * Uhr, gegen die das Token geprueft wird.
 */
@Serializable
data class Geraet(
    @SerialName("geraet_id") val geraetId: String,
    @SerialName("jti_kurz") val jtiKurz: String = "",
    val etikett: String? = null,
    @SerialName("ausgestellt_am") val ausgestelltAm: Long? = null,
    @SerialName("laeuft_ab_am") val laeuftAbAm: Long? = null,
    @SerialName("widerrufen_am") val widerrufenAm: Long? = null,
    @SerialName("zuletzt_gesehen") val zuletztGesehen: Long? = null,
    val gueltig: Boolean = false,
)
