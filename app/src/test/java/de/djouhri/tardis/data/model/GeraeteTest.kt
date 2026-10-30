package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.GeraeteResponse
import de.djouhri.tardis.data.model.tardis.WiderrufResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft die Feldnamen gegen die echte Antwort des Gateways
 * (`mobile-api/app/security/geraete.py`, Funktion `liste`).
 *
 * Der Grund ist die Art, wie ein Fehler hier aussieht: schreibt man
 * `@SerialName` falsch, wirft kotlinx nichts, sondern nimmt den Vorgabewert.
 * Die Liste zeigt dann leere Namen und „widerrufen oder abgelaufen" fuer jedes
 * Geraet, also ein Bild, das wie ein Problem des Gateways aussieht.
 */
class GeraeteTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Wortlaut aus `geraete.liste()`, nicht nachgebaut. */
    private val echteAntwort = """
        {"geraete":[
          {"geraet_id":"dev-abc","jti_kurz":"1a2b3c4d","etikett":"Pixel",
           "ausgestellt_am":1790000000,"laeuft_ab_am":1792592000,
           "widerrufen_am":null,"zuletzt_gesehen":1791500000,"gueltig":true},
          {"geraet_id":"dev-alt","jti_kurz":"9f8e7d6c","etikett":null,
           "ausgestellt_am":1780000000,"laeuft_ab_am":1782592000,
           "widerrufen_am":1781000000,"zuletzt_gesehen":null,"gueltig":false}
        ]}
    """.trimIndent()

    @Test
    fun `liest die geraeteliste des gateways`() {
        val antwort = json.decodeFromString<GeraeteResponse>(echteAntwort)

        assertEquals(2, antwort.geraete.size)
        val erstes = antwort.geraete[0]
        assertEquals("dev-abc", erstes.geraetId)
        assertEquals("1a2b3c4d", erstes.jtiKurz)
        assertEquals("Pixel", erstes.etikett)
        assertEquals(1790000000L, erstes.ausgestelltAm)
        assertEquals(1792592000L, erstes.laeuftAbAm)
        assertNull(erstes.widerrufenAm)
        assertEquals(1791500000L, erstes.zuletztGesehen)
        assertTrue(erstes.gueltig)
    }

    @Test
    fun `ein widerrufenes geraet traegt seinen zeitpunkt und ist nicht gueltig`() {
        val zweites = json.decodeFromString<GeraeteResponse>(echteAntwort).geraete[1]

        assertEquals(1781000000L, zweites.widerrufenAm)
        assertFalse(zweites.gueltig)
        // `etikett` und `zuletzt_gesehen` duerfen fehlen, ohne das Parsen zu brechen.
        assertNull(zweites.etikett)
        assertNull(zweites.zuletztGesehen)
    }

    @Test
    fun `eine leere liste ist keine fehlende liste`() {
        assertEquals(0, json.decodeFromString<GeraeteResponse>("""{"geraete":[]}""").geraete.size)
        // Fehlt der Schluessel ganz, ist die Liste leer statt null: die
        // Oberflaeche unterscheidet „geladen und leer" von „nicht geladen"
        // ueber ein eigenes Kennzeichen, nicht ueber die Liste.
        assertEquals(0, json.decodeFromString<GeraeteResponse>("""{}""").geraete.size)
    }

    @Test
    fun `liest die antwort von unpair und widerrufen`() {
        val a = json.decodeFromString<WiderrufResponse>("""{"geraet_id":"dev-abc","widerrufen":2}""")
        assertEquals("dev-abc", a.geraetId)
        assertEquals(2, a.widerrufen)

        // 0 ist eine gueltige Antwort mit Status 200, kein Fehlerfall.
        val b = json.decodeFromString<WiderrufResponse>("""{"geraet_id":"dev-x","widerrufen":0}""")
        assertEquals(0, b.widerrufen)
    }
}
