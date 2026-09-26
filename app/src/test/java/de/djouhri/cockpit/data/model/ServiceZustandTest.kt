package de.djouhri.cockpit.data.model

import de.djouhri.cockpit.data.demo.DemoData
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.data.model.cockpit.ServiceZustand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Einordnung eines Containers ist der Kern dieser App: sie entscheidet,
 * was auf der Uebersicht rot wird.
 *
 * Vorher galt „laeuft nicht" als Problem. Auf host waren das dauerhaft elf
 * Container, und alle elf sind Absicht (Rueckfall-Instanzen, Dienste auf Abruf,
 * ein Einmal-Job). Eine Liste, in der nichts stimmt, liest niemand mehr, und
 * dann faellt der eine echte Befund darin nicht mehr auf.
 */
class ServiceZustandTest {

    private fun svc(
        status: String,
        health: String? = null,
        expected: String? = null,
        inMap: Boolean? = null,
    ) = ServiceSummary(
        host = "host",
        name = "beispiel",
        status = status,
        health = health,
        expectedState = expected,
        inControlMap = inMap,
    )

    @Test
    fun `laufender container ohne befund ist in ordnung`() {
        val s = svc("running", health = "healthy", expected = "", inMap = true)
        assertEquals(ServiceZustand.LAEUFT, s.zustand)
        assertFalse(s.istProblem)
    }

    @Test
    fun `laufender container ohne healthcheck ist in ordnung`() {
        // Kein Healthcheck heisst nicht krank. Sonst waeren die sieben
        // host-Container ohne Probe dauerhaft auffaellig.
        assertEquals(ServiceZustand.LAEUFT, svc("running", health = null, expected = "", inMap = true).zustand)
    }

    @Test
    fun `laufender container mit unhealthy ist der echte alarm`() {
        val s = svc("running", health = "unhealthy", expected = "", inMap = true)
        assertEquals(ServiceZustand.AUFFAELLIG, s.zustand)
        assertTrue(s.istProblem)
    }

    @Test
    fun `bewusst gestoppter container ist kein problem`() {
        // `ai-llm` ist der Rueckfall fuer das ausgelagerte Modell, `schach`
        // laeuft nur auf Abruf. Beide sollen still stehen.
        for (erwartung in listOf("stopped", "manual", "migrated", "job", "planned", "on_demand")) {
            val s = svc("exited", expected = erwartung, inMap = true)
            assertEquals(erwartung, ServiceZustand.BEWUSST_AUS, s.zustand)
            assertFalse(erwartung, s.istProblem)
        }
    }

    @Test
    fun `gestoppter container der laufen soll ist ein ausfall`() {
        // Die Gegenprobe: ohne sie bestuende auch eine Fassung, die
        // grundsaetzlich nichts mehr meldet.
        val s = svc("exited", expected = "", inMap = true)
        assertEquals(ServiceZustand.AUSGEFALLEN, s.zustand)
        assertTrue(s.istProblem)
    }

    @Test
    fun `alter health befund eines gestoppten containers aendert nichts`() {
        // Docker behaelt `unhealthy` nach dem Stoppen. Das Gateway raeumt das
        // schon weg; kaeme es doch durch, darf es die Einordnung nicht kippen.
        val s = svc("exited", health = "unhealthy", expected = "stopped", inMap = true)
        assertEquals(ServiceZustand.BEWUSST_AUS, s.zustand)
    }

    @Test
    fun `container ohne control-map-eintrag wird nicht zum ausfall erklaert`() {
        val s = svc("exited", expected = null, inMap = false)
        assertEquals(ServiceZustand.UNBEWERTET, s.zustand)
        assertFalse(s.istProblem)
    }

    @Test
    fun `remote-container ohne erwartung bleibt unbewertet`() {
        // Auf node1 fuehrt die control-map keine Erwartung. 89 Container dort
        // als Ausfall zu melden waere die schlechteste Variante von allen.
        val s = svc("exited", expected = null, inMap = null)
        assertEquals(ServiceZustand.UNBEWERTET, s.zustand)
        assertFalse(s.istProblem)
    }

    @Test
    fun `neustartschleife bleibt ein befund`() {
        val s = svc("restarting", expected = "stopped", inMap = true)
        assertEquals(ServiceZustand.AUFFAELLIG, s.zustand)
        assertTrue(s.istProblem)
    }

    @Test
    fun `der demo-zaehlstand stimmt mit den demo-diensten ueberein`() {
        // Die Demo ist das oeffentliche Schaufenster der App. Zwei Wahrheiten
        // darin wuerden genau den Fehler vorfuehren, den sie loswerden soll.
        DemoData.hosts.filter { it.erreichbar }.forEach { host ->
            val dienste = DemoData.services.filter { it.host == host.name }
            assertEquals(host.name, dienste.size, host.gesamt)
            assertEquals(
                host.name,
                dienste.count { it.istProblem },
                host.problemZahl,
            )
            assertEquals(
                host.name,
                host.gesamt,
                listOfNotNull(host.laufend, host.auffaellig, host.bewusstAus, host.ausgefallen, host.unbewertet).sum(),
            )
        }
    }

    @Test
    fun `die demo zeigt jeden zustand mindestens einmal`() {
        val gezeigt = DemoData.services.map { it.zustand }.toSet()
        ServiceZustand.entries.forEach { z ->
            assertTrue("Zustand $z fehlt in der Demo", z in gezeigt)
        }
    }
}
