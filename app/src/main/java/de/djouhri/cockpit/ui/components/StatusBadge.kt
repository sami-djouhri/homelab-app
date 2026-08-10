package de.djouhri.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.ui.theme.StatusDown
import de.djouhri.cockpit.ui.theme.StatusIdle
import de.djouhri.cockpit.ui.theme.StatusUp
import de.djouhri.cockpit.ui.theme.StatusWarn

/** Ampelfarbe fuer einen Container-Status/-Health. */
fun serviceStatusColor(service: ServiceSummary): Color = when {
    service.isUnhealthy -> StatusWarn
    service.isRunning -> StatusUp
    service.status.equals("restarting", ignoreCase = true) -> StatusWarn
    service.status.equals("created", ignoreCase = true) -> StatusIdle
    else -> StatusDown
}

/** Ampelfarbe fuer einen Auslastungswert (Prozent). */
fun usageColor(percent: Double?): Color = when {
    percent == null -> StatusIdle
    percent >= 90 -> StatusDown
    percent >= 75 -> StatusWarn
    else -> StatusUp
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
