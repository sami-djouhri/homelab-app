package de.djouhri.cockpit.ui.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.cockpit.BuildConfig
import de.djouhri.cockpit.data.local.SettingsStore
import de.djouhri.cockpit.data.model.cockpit.AppVersion
import de.djouhri.cockpit.data.repository.UpdateRepository
import de.djouhri.cockpit.data.update.HashUrteil
import de.djouhri.cockpit.data.update.UpdateRegeln
import de.djouhri.cockpit.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUiState(
    val prueftGerade: Boolean = false,
    val geprueft: Boolean = false,
    val angeboten: AppVersion? = null,
    /** Fortschritt in Worten, nicht in Prozent: die Datei kommt am Stueck. */
    val laedtGerade: Boolean = false,
    val bereit: File? = null,
    val urteil: HashUrteil? = null,
    val fehler: String? = null,
) {
    val installierteVersion: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

/**
 * Kuemmert sich selbst um Updates: sehen, holen, pruefen, uebergeben.
 *
 * Geteilt zwischen Banner und Einstellungen, damit beide denselben Stand
 * zeigen. Die letzte Handlung bleibt beim Nutzer, denn mehr erlaubt Android
 * einer App, die nicht Geraeteverwalter ist, auch nicht: der Paket-Installer
 * fragt selbst noch einmal.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UpdateRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()

    init {
        pruefeWennFaellig()
    }

    /**
     * Selbsttaetige Pruefung, hoechstens alle paar Stunden.
     *
     * Ohne den Abstand baut jeder App-Start eine Verbindung auf, die niemand
     * angefordert hat; ohne die Pruefung ueberhaupt merkt niemand, dass eine
     * neue Version bereitliegt. Die alte App tat Letzteres.
     */
    fun pruefeWennFaellig() {
        viewModelScope.launch {
            val letzte = settingsStore.letzteUpdatePruefung()
            if (!UpdateRegeln.darfPruefen(System.currentTimeMillis(), letzte)) return@launch
            pruefe(stillBeiFehler = true, achteAufUebersprungen = true)
        }
    }

    /**
     * @param stillBeiFehler true fuer die selbsttaetige Pruefung: wer die App
     *   oeffnet und gerade kein WireGuard anhat, soll keine Fehlermeldung
     *   sehen, die er nicht angefordert hat.
     * @param achteAufUebersprungen nur fuer die selbsttaetige Pruefung. Wer von
     *   Hand nachsieht, will die Antwort auch dann, wenn er dieselbe Version
     *   vorher weggetippt hat: sonst antwortet die App "Aktuell", obwohl sie es
     *   nicht ist, und das waere die schlimmere Auskunft.
     */
    fun pruefe(stillBeiFehler: Boolean = false, achteAufUebersprungen: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(prueftGerade = true, fehler = null) }
            repository.neuesteVersion().fold(
                onSuccess = { version ->
                    settingsStore.setLetzteUpdatePruefung(System.currentTimeMillis())
                    val uebersprungen =
                        if (achteAufUebersprungen) settingsStore.uebersprungeneVersion.first() else 0
                    val neu = repository.istNeuer(version) && version.versionCode != uebersprungen
                    _state.update {
                        it.copy(
                            prueftGerade = false,
                            geprueft = true,
                            angeboten = if (neu) version else null,
                        )
                    }
                },
                onFailure = { fehler ->
                    _state.update {
                        it.copy(
                            prueftGerade = false,
                            geprueft = !stillBeiFehler,
                            fehler = if (stillBeiFehler) null else fehler.userMessage(),
                        )
                    }
                },
            )
        }
    }

    fun holen() {
        val version = _state.value.angeboten ?: return
        viewModelScope.launch {
            _state.update { it.copy(laedtGerade = true, fehler = null) }
            repository.herunterladen(version).fold(
                onSuccess = { geladen ->
                    _state.update {
                        it.copy(laedtGerade = false, bereit = geladen.datei, urteil = geladen.urteil)
                    }
                },
                onFailure = { fehler ->
                    _state.update { it.copy(laedtGerade = false, fehler = fehler.userMessage()) }
                },
            )
        }
    }

    /** Merkt sich, dass genau diese Nummer nicht mehr gemeldet werden soll. */
    fun spaeter() {
        val version = _state.value.angeboten ?: return
        viewModelScope.launch {
            settingsStore.setUebersprungeneVersion(version.versionCode)
            _state.update { it.copy(angeboten = null, bereit = null) }
        }
    }

    /**
     * Uebergibt die geladene Datei an Androids Paket-Installer.
     *
     * Ab Android 8 braucht das die Erlaubnis "Unbekannte Apps installieren" fuer
     * genau diese App. Fehlt sie, fuehrt der Systemdialog selbst dorthin; ohne
     * den Hinweis unten sieht ein Abbruch aber aus, als sei nichts passiert.
     */
    fun installieren(): Result<Unit> = runCatching {
        val datei = _state.value.bereit ?: error("Es liegt keine geladene Datei bereit.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            error(
                "Android erlaubt dieser App das Installieren noch nicht. " +
                    "Der nächste Dialog führt zur Einstellung; danach hier erneut tippen.",
            )
        }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.updates", datei)
        val absicht = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(absicht)
    }.onFailure { fehler ->
        _state.update { it.copy(fehler = fehler.message ?: fehler.userMessage()) }
    }.map { }
}
