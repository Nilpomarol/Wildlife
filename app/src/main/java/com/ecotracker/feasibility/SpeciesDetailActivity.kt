package com.wildlife.feasibility

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.wildlife.feasibility.ui.screens.speciesdetail.SpeciesDetailScreen
import com.wildlife.feasibility.ui.screens.speciesdetail.SpeciesDetailViewModel
import com.wildlife.feasibility.ui.screens.speciesdetail.TAXON_ID_KEY
import com.wildlife.feasibility.ui.screens.speciesdetail.TAXON_LABEL_KEY
import com.wildlife.feasibility.ui.screens.speciesdetail.REGION_KEY
import com.wildlife.feasibility.ui.theme.WildlifeTheme

class SpeciesDetailActivity : ComponentActivity() {
    private val viewModel by viewModels<SpeciesDetailViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(8, 11, 9)),
        )
        setContent {
            WildlifeTheme {
                SpeciesDetailScreen(
                    state = viewModel.uiState,
                    onBack = ::finish,
                    onOpenTaxon = { taxonId ->
                        open("https://www.inaturalist.org/taxa/$taxonId")
                    },
                    onOpenObservation = { uuid ->
                        open("https://www.inaturalist.org/observations/$uuid")
                    },
                    onSeeAllObservations = {
                        startActivity(MainActivity.observationsIntent(this, viewModel.uiState.taxonId))
                    },
                    onOpenUrl = ::open,
                    onRetryMedia = viewModel::retryMedia,
                    onRequestAlternativeImage = viewModel::requestAlternativeImage,
                    onRetryObservationDensity = viewModel::retryObservationDensity,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun open(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object {
        fun intent(context: Context, taxonId: Long, fallbackLabel: String?, regionKey: String? = null) =
            Intent(context, SpeciesDetailActivity::class.java).apply {
                putExtra(TAXON_ID_KEY, taxonId)
                putExtra(TAXON_LABEL_KEY, fallbackLabel)
                regionKey?.let { putExtra(REGION_KEY, it) }
            }
    }
}
