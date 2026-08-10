package de.djouhri.cockpit.util

import retrofit2.HttpException
import java.io.IOException

fun Throwable.userMessage(): String = when (this) {
    is IOException -> "Netzwerkfehler. Ist das VPN aktiv?"
    is HttpException -> when (code()) {
        401 -> "Nicht autorisiert. Bitte Geraet erneut koppeln."
        403 -> "Zugriff verweigert."
        404 -> "Nicht gefunden."
        422 -> "Ungueltige Daten."
        in 500..599 -> "Serverfehler. Bitte spaeter erneut versuchen."
        else -> "Fehler: ${code()} ${message()}"
    }
    else -> message ?: "Unbekannter Fehler"
}

/** Bytes menschenlesbar (GiB/MiB) fuer die Host-Metriken. */
fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val gib = this / (1024.0 * 1024.0 * 1024.0)
    if (gib >= 1.0) return "%.1f GiB".format(gib)
    val mib = this / (1024.0 * 1024.0)
    return "%.0f MiB".format(mib)
}

fun Double?.formatPercent(): String = this?.let { "%.0f %%".format(it) } ?: "–"
