package de.djouhri.cockpit.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.data.demo.DemoModeManager
import de.djouhri.cockpit.data.repository.PairingRepository
import de.djouhri.cockpit.ui.components.QrScannerView
import de.djouhri.cockpit.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val scanning: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val demoModeManager: DemoModeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state = _state.asStateFlow()

    /** Startet den oeffentlichen Offline-Demo-Modus (kein Gateway/Pairing noetig). */
    fun startDemo() = demoModeManager.enable()

    fun startScan() {
        _state.value = PairingUiState(scanning = true)
    }

    fun cancelScan() {
        _state.value = _state.value.copy(scanning = false)
    }

    fun onQrDetected(qrJson: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(scanning = false, loading = true, error = null)
        viewModelScope.launch {
            val label = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Cockpit"
            val result = pairingRepository.pair(qrJson, label)
            if (result.isFailure) {
                _state.value = PairingUiState(
                    error = result.exceptionOrNull()?.userMessage() ?: "Pairing fehlgeschlagen",
                )
            }
            // Erfolg: isPaired-Flow kippt → Root wechselt automatisch ins Cockpit.
        }
    }
}

@Composable
fun PairingScreen(viewModel: PairingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (granted) viewModel.startScan()
    }

    if (state.scanning && hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScannerView(onQrDetected = viewModel::onQrDetected)
            OutlinedButton(
                onClick = viewModel::cancelScan,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            ) {
                Text("Abbrechen")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.height(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Cockpit koppeln",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Oeffne die Pairing-Seite im internen Netz und scanne den QR-Code. " +
                "Das Geraet erzeugt dabei einen eigenen Schluessel im Hardware-Keystore.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        if (state.loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Koppelt…", style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        viewModel.startScan()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  QR-Code scannen")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::startDemo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Demo ansehen (ohne Gateway)")
            }
        }

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
