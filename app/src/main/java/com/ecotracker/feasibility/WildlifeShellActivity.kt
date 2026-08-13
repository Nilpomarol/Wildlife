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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.wildlife.feasibility.ui.navigation.WildlifeBottomBar
import com.wildlife.feasibility.ui.navigation.WildlifeDestination
import com.wildlife.feasibility.ui.screens.collection.CollectionScreen
import com.wildlife.feasibility.ui.screens.collection.CollectionViewModel
import com.wildlife.feasibility.ui.screens.explore.ExploreScreen
import com.wildlife.feasibility.ui.screens.explore.ExploreViewModel
import com.wildlife.feasibility.ui.screens.shell.HomeScreen
import com.wildlife.feasibility.ui.screens.shell.ProfileScreen
import com.wildlife.feasibility.ui.screens.shell.ShellViewModel
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val shellViewModel by viewModels<ShellViewModel>()
    private val collectionViewModel by viewModels<CollectionViewModel>()
    private val exploreViewModel by viewModels<ExploreViewModel>()
    private var requestedDestination by mutableStateOf(WildlifeDestination.HOME)
    private var observationSyncInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(8, 11, 9)),
        )
        requestedDestination = destinationFrom(intent)
        setContent {
            WildlifeTheme {
                WildlifeShell(requestedDestination)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination = destinationFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        shellViewModel.refresh()
        collectionViewModel.refresh()
        exploreViewModel.refreshLocal()
        syncObservationsOnDevice()
    }

    @Composable
    private fun WildlifeShell(requestedDestination: WildlifeDestination) {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val selected = WildlifeDestination.entries.firstOrNull { it.route == currentRoute }
            ?: WildlifeDestination.HOME
        val bottomBar: @Composable () -> Unit = {
            WildlifeBottomBar(selected = selected) { destination ->
                if (destination == WildlifeDestination.CAPTURE) {
                    startActivity(Intent(this, CaptureActivity::class.java))
                } else {
                    navController.openDestination(destination)
                }
            }
        }

        LaunchedEffect(requestedDestination) {
            if (requestedDestination != WildlifeDestination.HOME) {
                navController.openDestination(requestedDestination)
            }
        }

        NavHost(
            navController = navController,
            startDestination = WildlifeDestination.HOME.route,
        ) {
            composable(WildlifeDestination.HOME.route) {
                HomeScreen(
                    state = shellViewModel.uiState,
                    onCapture = { startActivity(Intent(this@MainActivity, CaptureActivity::class.java)) },
                    onCollection = { navController.openDestination(WildlifeDestination.COLLECTION) },
                    onExplore = { navController.openDestination(WildlifeDestination.EXPLORE) },
                    onLinkAccount = ::openAccountManagement,
                    bottomBar = bottomBar,
                )
            }
            composable(WildlifeDestination.COLLECTION.route) {
                CollectionScreen(
                    state = collectionViewModel.uiState,
                    onBack = null,
                    onOpenSpecies = { species ->
                        species.taxonId?.let { taxonId ->
                            startActivity(
                                SpeciesDetailActivity.intent(
                                    this@MainActivity, taxonId, species.label,
                                ),
                            )
                        } ?: openExternal(
                            "https://www.inaturalist.org/observations/${species.latestObservationUuid}",
                        )
                    },
                    onLinkAccount = ::openAccountManagement,
                    onRetry = collectionViewModel::refresh,
                    bottomBar = bottomBar,
                )
            }
            composable(WildlifeDestination.EXPLORE.route) {
                ExploreScreen(
                    state = exploreViewModel.uiState,
                    onBack = null,
                    onSync = exploreViewModel::syncCatalogue,
                    onOpenTaxon = { taxonId ->
                        val species = exploreViewModel.uiState.entries.firstOrNull {
                            it.taxonId == taxonId
                        }
                        startActivity(
                            SpeciesDetailActivity.intent(
                                this@MainActivity,
                                taxonId,
                                species?.commonName ?: species?.scientificName,
                            ),
                        )
                    },
                    bottomBar = bottomBar,
                )
            }
            composable(WildlifeDestination.PROFILE.route) {
                ProfileScreen(
                    state = shellViewModel.uiState,
                    onManageAccount = ::openAccountManagement,
                    onOpenPublicProfile = { login ->
                        openExternal("https://www.inaturalist.org/people/$login")
                    },
                    onSelectProgressionTitle = shellViewModel::selectProgressionTitle,
                    bottomBar = bottomBar,
                )
            }
        }
    }

    private fun NavHostController.openDestination(destination: WildlifeDestination) {
        navigate(destination.route) {
            popUpTo(WildlifeDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    private fun openAccountManagement() {
        startActivity(
            Intent(this, AccountLinkActivity::class.java).apply {
                shellViewModel.uiState.account?.login?.let {
                    putExtra(AccountLinkActivity.EXTRA_USERNAME, it)
                }
            },
        )
    }

    private fun syncObservationsOnDevice() {
        val account = AccountStore(this).verified() ?: return
        if (observationSyncInFlight) return
        observationSyncInFlight = true
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(this@MainActivity).syncObservations(account)
                }
            }.onSuccess {
                shellViewModel.refresh()
                collectionViewModel.refresh()
                exploreViewModel.refreshLocal()
            }
            observationSyncInFlight = false
        }
    }

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun destinationFrom(intent: Intent): WildlifeDestination =
        intent.getStringExtra(EXTRA_DESTINATION)
            ?.let { route -> WildlifeDestination.entries.firstOrNull { it.route == route } }
            ?.takeUnless { it == WildlifeDestination.CAPTURE }
            ?: WildlifeDestination.HOME

    companion object {
        private const val EXTRA_DESTINATION = "wildlife_destination"

        fun intent(context: Context, destination: WildlifeDestination) =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination.route)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
