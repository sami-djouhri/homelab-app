package de.djouhri.cockpit.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.djouhri.cockpit.data.update.HashUrteil

/**
 * Meldet eine bereitliegende Version, wo man sie sieht: ueber allem, was die
 * App sonst zeigt.
 *
 * Der Hinweis erscheint nur, wenn es wirklich eine hoehere Nummer gibt und sie
 * nicht weggetippt wurde. Er nennt in jedem Schritt, was gerade Sache ist,
 * statt nur einen Knopf anzubieten: geladen wird sichtbar, die Pruefsumme
 * wird benannt, und wenn sie nicht verglichen werden konnte, steht genau das
 * da statt eines Haekchens.
 */
@Composable
fun UpdateBanner(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val version = state.angeboten ?: return

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "Version ${version.versionName} liegt bereit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Installiert ist ${state.installierteVersion}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            state.urteil?.let { urteil ->
                Spacer(Modifier.height(4.dp))
                Text(
                    when (urteil) {
                        HashUrteil.PASST -> "Geladen, Prüfsumme stimmt."
                        HashUrteil.OHNE_VERGLEICH ->
                            "Geladen. Das Gateway hat keine Prüfsumme genannt, verglichen wurde also nichts."
                        HashUrteil.ABWEICHUNG -> "Prüfsumme stimmt nicht, die Datei wurde verworfen."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            state.fehler?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    state.laedtGerade -> {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        Text("Wird geladen…", style = MaterialTheme.typography.bodySmall)
                    }
                    state.bereit != null -> Button(onClick = { viewModel.installieren() }) {
                        Text("Installieren")
                    }
                    else -> Button(onClick = { viewModel.holen() }) { Text("Herunterladen") }
                }
                TextButton(onClick = { viewModel.spaeter() }) { Text("Nicht jetzt") }
            }
        }
    }
}
