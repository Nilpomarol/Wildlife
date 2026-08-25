package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** Shared initial-loading treatment. Existing content stays visible during later refreshes. */
@Composable
fun WildlifeLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WildlifeSpacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 280.dp),
            color = WildlifeTheme.colors.oliveStrong,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = WildlifeSpacing.Small),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1209, widthDp = 390, heightDp = 720)
@Preview(
    name = "Loading large text",
    showBackground = true,
    backgroundColor = 0xFF0E1209,
    widthDp = 390,
    heightDp = 720,
    fontScale = 2f,
)
@Composable
private fun WildlifeLoadingStatePreview() {
    WildlifeTheme { WildlifeLoadingState("Opening the regional field guide…") }
}
