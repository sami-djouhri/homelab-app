package de.djouhri.cockpit

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import de.djouhri.cockpit.navigation.CockpitMainScreen
import de.djouhri.cockpit.ui.components.LoadingIndicator
import de.djouhri.cockpit.ui.pairing.PairingScreen
import de.djouhri.cockpit.ui.theme.HomelabTheme

// FragmentActivity (statt ComponentActivity), damit BiometricPrompt die lokale
// Zusatzbestaetigung schreibender Aktionen anzeigen kann.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomelabTheme {
                CockpitRoot()
            }
        }
    }
}

@Composable
private fun CockpitRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val target by rootViewModel.target.collectAsState()
    val demoActive by rootViewModel.demoActive.collectAsState()
    val sessionExpired by rootViewModel.sessionExpired.collectAsState()
    when (target) {
        RootTarget.LOADING -> LoadingIndicator()
        RootTarget.PAIRING -> PairingScreen()
        RootTarget.COCKPIT -> CockpitMainScreen(
            demoActive = demoActive,
            sessionExpired = sessionExpired,
        )
    }
}
