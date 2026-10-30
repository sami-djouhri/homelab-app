package de.djouhri.tardis

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import de.djouhri.tardis.navigation.TardisMainScreen
import de.djouhri.tardis.ui.components.LoadingIndicator
import de.djouhri.tardis.ui.pairing.PairingScreen
import de.djouhri.tardis.ui.theme.HomelabTheme

// FragmentActivity (statt ComponentActivity), damit BiometricPrompt die lokale
// Zusatzbestaetigung schreibender Aktionen anzeigen kann.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer dunkel, also helle Leistensymbole, unabhaengig vom System.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HomelabTheme {
                TardisRoot()
            }
        }
    }
}

@Composable
private fun TardisRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val target by rootViewModel.target.collectAsState()
    val demoActive by rootViewModel.demoActive.collectAsState()
    val sessionExpired by rootViewModel.sessionExpired.collectAsState()
    when (target) {
        RootTarget.LOADING -> LoadingIndicator()
        RootTarget.PAIRING -> PairingScreen()
        RootTarget.TARDIS -> TardisMainScreen(
            demoActive = demoActive,
            sessionExpired = sessionExpired,
        )
    }
}
