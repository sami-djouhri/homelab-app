package de.djouhri.tardis.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Liest Paketname, Version und Signatur aus der geladenen APK und aus der
 * installierten App und laesst [ApkPruefung] entscheiden.
 *
 * @return `null`, wenn die Datei uebergeben werden darf, sonst den Grund.
 */
fun pruefeApk(kontext: Context, datei: File, angekuendigteVersion: Long): String? {
    val pm = kontext.packageManager
    val eigen = pm.getPackageInfo(kontext.packageName, signaturFlags())
    @Suppress("DEPRECATION")
    val archiv: PackageInfo? = pm.getPackageArchiveInfo(datei.path, signaturFlags())
    return ApkPruefung.einwand(
        befund = archiv?.let { ApkBefund(it.packageName, versionVon(it), zertifikateVon(it)) },
        eigenesPaket = kontext.packageName,
        installierteVersion = versionVon(eigen),
        angekuendigteVersion = angekuendigteVersion,
        eigeneZertifikate = zertifikateVon(eigen),
    )
}

@Suppress("DEPRECATION")
private fun signaturFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
    else PackageManager.GET_SIGNATURES

@Suppress("DEPRECATION")
private fun versionVon(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
    else info.versionCode.toLong()

/**
 * Bei Schluesselrotation steht der alte Schluessel in der Historie. Gezaehlt
 * wird deshalb die ganze Historie, und eine gemeinsame Signatur genuegt.
 */
@Suppress("DEPRECATION")
private fun zertifikateVon(info: PackageInfo): Set<String> {
    val signaturen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val s = info.signingInfo ?: return emptySet()
        if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
    } else {
        info.signatures
    }
    val md = MessageDigest.getInstance("SHA-256")
    return signaturen.orEmpty().map { sig ->
        md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
    }.toSet()
}
