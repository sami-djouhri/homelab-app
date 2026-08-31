package de.djouhri.cockpit.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validiert die vom `/app-version`-Endpunkt gelieferte APK-URL, bevor sie ans
 * System (Browser/Installer) uebergeben wird.
 *
 * Regel (clientseitige Zusatzschicht): die APK darf ausschliesslich vom
 * gekoppelten Gateway kommen. Relative Pfade werden gegen das Gateway aufgeloest;
 * absolute URLs muessen in Schema, Host UND Port exakt dem Gateway entsprechen.
 * Alles andere (fremder Host, http↔https-Downgrade, andere Ports) wird abgelehnt
 * - so kann eine manipulierte Server-Antwort keinen Fremd-Download unterschieben.
 */
object UpdateUrl {

    fun resolve(gateway: String, apkUrl: String): Result<String> = runCatching {
        val base = gateway.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Gateway-URL ungueltig")

        val trimmed = apkUrl.trim()
        require(trimmed.isNotEmpty()) { "Leere APK-URL" }

        val resolved: HttpUrl = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed.toHttpUrlOrNull() ?: throw IllegalArgumentException("APK-URL ungueltig")
        } else {
            base.resolve(trimmed) ?: throw IllegalArgumentException("APK-Pfad nicht aufloesbar")
        }

        require(
            resolved.scheme == base.scheme &&
                resolved.host == base.host &&
                resolved.port == base.port,
        ) { "APK-URL nicht vom Gateway (${base.host}:${base.port})" }

        resolved.toString()
    }
}
