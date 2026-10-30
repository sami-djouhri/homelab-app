package de.djouhri.tardis.data.update

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Vorpruefung der geladenen APK, bevor sie an den Installer geht. */
class ApkPruefungTest {

    private val z = setOf("c0ffee")
    private fun einwand(b: ApkBefund?) = ApkPruefung.einwand(b, "de.djouhri.tardis", 6, 7, z)

    @Test
    fun `die passende APK geht durch`() {
        assertNull(einwand(ApkBefund("de.djouhri.tardis", 7, z)))
    }

    @Test
    fun `falsche Version, fremde App, fremder Schluessel, unlesbare Datei werden abgewiesen`() {
        assertNotNull(einwand(ApkBefund("de.djouhri.tardis", 6, z)))
        // Hoeher als angekuendigt: scheitert NUR am Versionsvergleich, nicht am Downgrade-Schutz.
        assertNotNull(einwand(ApkBefund("de.djouhri.tardis", 8, z)))
        assertNotNull(einwand(ApkBefund("de.djouhri.tardis.debug", 7, z)))
        assertNotNull(einwand(ApkBefund("de.djouhri.tardis", 7, setOf("bad"))))
        assertNotNull(einwand(null))
    }

    @Test
    fun `nicht lesbare Signatur ist weder Einwand noch Zusage`() {
        assertNull(einwand(ApkBefund("de.djouhri.tardis", 7, emptySet())))
        assertNull(ApkPruefung.einwand(ApkBefund("de.djouhri.tardis", 7, z), "de.djouhri.tardis", 6, 7, emptySet()))
    }
}
