package com.wildlife.feasibility

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.wildlife.feasibility.ui.navigation.WildlifeBottomBar
import com.wildlife.feasibility.ui.navigation.WildlifeDestination
import com.wildlife.feasibility.ui.screens.collection.CollectionScreen
import com.wildlife.feasibility.ui.screens.collection.CollectionViewModel
import com.wildlife.feasibility.ui.screens.explore.ExploreScreen
import com.wildlife.feasibility.ui.screens.explore.ExploreSection
import com.wildlife.feasibility.ui.screens.explore.ExploreViewModel
import com.wildlife.feasibility.ui.screens.observations.ObservationsScreen
import com.wildlife.feasibility.ui.screens.observations.ObservationsViewModel
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
    private val observationsViewModel by viewModels<ObservationsViewModel>()
    private var requestedDestination by mutableStateOf(WildlifeDestination.HOME)
    private var exploreSection by mutableStateOf(ExploreSection.GUIDE)
    private var observationsRequest by mutableStateOf<ObservationsRequest?>(null)
    private var observationSyncInFlight = false
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            requestCurrentLocation()
        } else {
            exploreViewModel.nearbyLocationFailed(
                "Location permission is needed only when you choose Check near me.",
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(8, 11, 9)),
        )
        requestedDestination = destinationFrom(intent)
        observationsRequest = observationsRequestFrom(intent)
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
        observationsRequest = observationsRequestFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        ObservationSyncScheduler.schedule(this)
        shellViewModel.refresh()
        collectionViewModel.refresh()
        exploreViewModel.refreshLocal()
        observationsViewModel.refresh()
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

        LaunchedEffect(observationsRequest) {
            observationsRequest?.let { request ->
                observationsViewModel.setTaxonFilter(request.taxonId)
                navController.navigate(OBSERVATIONS_ROUTE) { launchSingleTop = true }
                observationsRequest = null
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
                    onMyMap = {
                        exploreSection = ExploreSection.MAP
                        navController.openDestination(WildlifeDestination.EXPLORE)
                    },
                    onObservations = { openObservations(navController) },
                    onOpenSpecies = { taxonId ->
                        startActivity(
                            SpeciesDetailActivity.intent(
                                this@MainActivity,
                                taxonId,
                                shellViewModel.uiState.latestDiscovery?.label,
                            ),
                        )
                    },
                    mappedObservationCount = exploreViewModel.uiState.personalMap.mappedObservationCount,
                    nearby = exploreViewModel.uiState.nearby,
                    onDiscoverNearby = ::beginNearbyDiscovery,
                    onSeeAllNearby = {
                        exploreSection = ExploreSection.NEARBY
                        navController.openDestination(WildlifeDestination.EXPLORE)
                    },
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
                    onRefresh = exploreViewModel::refreshLocal,
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
                    onDiscoverNearby = ::beginNearbyDiscovery,
                    onSelectRegion = ::selectRegion,
                    onOpenObservation = { uuid ->
                        openExternal("https://www.inaturalist.org/observations/$uuid")
                    },
                    onMapVisibilityChanged = exploreViewModel::setObservationMapVisible,
                    bottomBar = bottomBar,
                    selectedSection = exploreSection,
                    onSectionChange = { exploreSection = it },
                )
            }
            composable(OBSERVATIONS_ROUTE) {
                ObservationsScreen(
                    state = observationsViewModel.uiState,
                    onBack = { navController.popBackStack() },
                    onSync = observationsViewModel::sync,
                    onSubmitted = observationsViewModel::markSubmitted,
                    onNotSubmitted = observationsViewModel::markNotSubmitted,
                    onConfirm = observationsViewModel::confirm,
                    onOpenObservation = { uuid ->
                        openExternal("https://www.inaturalist.org/observations/$uuid")
                    },
                    onOpenINaturalist = { openExternal("https://www.inaturalist.org") },
                    onDeleteLocal = observationsViewModel::deleteLocalGroup,
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
                    onOpenObservations = { openObservations(navController) },
                    onCopyTestReport = ::copyTestReport,
                    onDeleteLocalData = ::deleteLocalData,
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

    /** Observations is a focused route inside the shell, not a sixth bottom destination. */
    private fun openObservations(navController: NavHostController) {
        observationsViewModel.setTaxonFilter(null)
        navController.navigate(OBSERVATIONS_ROUTE) { launchSingleTop = true }
    }

    /** One region choice, applied to every projection that reads it. */
    private fun selectRegion(regionKey: String) {
        exploreViewModel.selectRegion(regionKey)
        collectionViewModel.selectRegion(regionKey)
        shellViewModel.refresh()
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

    private fun syncObservationsOnDevice(force: Boolean = false) {
        val account = AccountStore(this).verified() ?: return
        if (observationSyncInFlight) return
        observationSyncInFlight = true
        shellViewModel.observationSyncStarted()
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(this@MainActivity).use { repository ->
                        val result = repository.syncObservations(account, force = force)
                        MarkerStore(this@MainActivity).load()
                            .asSequence()
                            .filter { it.state == MarkerState.CONFIRMED }
                            .mapNotNull { it.matchedObservationUuid }
                            .distinct()
                            .forEach { uuid -> repository.confirmObservation(account, uuid) }
                        result
                    }
                }
            }.onSuccess {
                shellViewModel.observationSyncSucceeded()
                collectionViewModel.refresh()
                exploreViewModel.refreshLocal()
            }.onFailure { error ->
                shellViewModel.observationSyncFailed(
                    error.message ?: "Could not update public iNaturalist observations.",
                )
            }
            observationSyncInFlight = false
        }
    }

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun copyTestReport(report: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Wildlife test report", report))
        Toast.makeText(this, "Privacy-safe test report copied.", Toast.LENGTH_SHORT).show()
    }

    private fun deleteLocalData() {
        if (observationSyncInFlight) {
            Toast.makeText(this, "Wait for the current update to finish, then try again.", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            ObservationSyncScheduler.cancel(this@MainActivity)
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalDataManager(this@MainActivity).clearAllLocalData() }
            }
            shellViewModel.refresh()
            collectionViewModel.refresh()
            exploreViewModel.refreshLocal()
            val message = if (result.isSuccess) {
                "Wildlife local data deleted. iNaturalist was not changed."
            } else {
                "Some local data could not be deleted. Try again before continuing the test."
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun beginNearbyDiscovery() {
        exploreViewModel.nearbyLocationStarted()
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            requestCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    @Suppress("MissingPermission")
    private fun requestCurrentLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            exploreViewModel.nearbyLocationFailed(
                "Turn on device location, then try Check near me again.",
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(provider, null, mainExecutor) { location ->
                if (location == null) {
                    exploreViewModel.nearbyLocationFailed(
                        "A current location was not available. Try again outdoors or with location enabled.",
                    )
                } else {
                    exploreViewModel.discoverNearby(location.latitude, location.longitude)
                }
            }
        } else {
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (location == null) {
                exploreViewModel.nearbyLocationFailed(
                    "A recent location was not available. Open Maps once or try again outdoors.",
                )
            } else {
                exploreViewModel.discoverNearby(location.latitude, location.longitude)
            }
        }
    }

    private fun observationsRequestFrom(intent: Intent): ObservationsRequest? {
        if (intent.getStringExtra(EXTRA_DESTINATION) != OBSERVATIONS_ROUTE) return null
        val taxonId = intent.getLongExtra(EXTRA_TAXON_ID, NO_TAXON).takeIf { it != NO_TAXON }
        return ObservationsRequest(taxonId)
    }

    private fun destinationFrom(intent: Intent): WildlifeDestination =
        intent.getStringExtra(EXTRA_DESTINATION)
            ?.let { route -> WildlifeDestination.entries.firstOrNull { it.route == route } }
            ?.takeUnless { it == WildlifeDestination.CAPTURE }
            ?: WildlifeDestination.HOME

    /** A pending request to open the observations route, optionally filtered to one species. */
    private data class ObservationsRequest(val taxonId: Long?)

    companion object {
        private const val EXTRA_DESTINATION = "wildlife_destination"
        private const val EXTRA_TAXON_ID = "wildlife_observations_taxon_id"
        private const val NO_TAXON = Long.MIN_VALUE
        const val OBSERVATIONS_ROUTE = "observations"

        fun intent(context: Context, destination: WildlifeDestination) =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination.route)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        /** Species Detail opens the shell's observations route filtered to one species. */
        fun observationsIntent(context: Context, taxonId: Long?) =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, OBSERVATIONS_ROUTE)
                taxonId?.let { putExtra(EXTRA_TAXON_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
