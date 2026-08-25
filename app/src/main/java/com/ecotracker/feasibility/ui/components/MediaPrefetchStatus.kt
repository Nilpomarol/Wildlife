package com.wildlife.feasibility.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wildlife.feasibility.MediaPrefetchSummary
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** Compact functional status used while the local guide itself remains fully browsable. */
@Composable
fun MediaPrefetchStatus(summary: MediaPrefetchSummary, modifier: Modifier = Modifier) {
    val message = when {
        summary.failed > 0 -> "${summary.failed} reference ${if (summary.failed == 1) "image needs" else "images need"} repair. Species information remains available offline."
        summary.queued + summary.running > 0 -> "Preparing ${summary.queued + summary.running} reference images in the background."
        else -> null
    }
    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = WildlifeTheme.colors.mutedText,
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun MediaPrefetchStatusPreview() {
    WildlifeTheme {
        MediaPrefetchStatus(MediaPrefetchSummary(queued = 12, running = 1, completed = 4, failed = 0))
    }
}

@Preview
@Composable
private fun MediaRepairStatusPreview() {
    WildlifeTheme {
        MediaPrefetchStatus(MediaPrefetchSummary(queued = 0, running = 0, completed = 14, failed = 1))
    }
}
