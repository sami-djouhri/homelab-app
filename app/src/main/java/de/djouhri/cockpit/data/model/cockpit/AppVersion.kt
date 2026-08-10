package de.djouhri.cockpit.data.model.cockpit

import kotlinx.serialization.Serializable

/**
 * /app-version treibt den In-App-Updater.
 *
 * `sha256` (optional) traegt den erwarteten Hash der APK. Die eigentliche
 * Integritaets- und Herkunftspruefung uebernimmt Androids Paket-Installer ueber
 * die APK-Signatur (v2/v3): ein Update installiert nur, wenn es mit demselben
 * Schluessel signiert ist. Der Hash wird dem Nutzer zusaetzlich angezeigt.
 */
@Serializable
data class AppVersion(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val apkUrl: String = "/cockpit.apk",
    val sha256: String = "",
)
