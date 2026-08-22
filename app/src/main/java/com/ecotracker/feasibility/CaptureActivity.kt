package com.wildlife.feasibility

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.wildlife.feasibility.ui.navigation.WildlifeDestination
import com.wildlife.feasibility.ui.screens.capture.CaptureProjection
import com.wildlife.feasibility.ui.screens.capture.CaptureRewardUi
import com.wildlife.feasibility.ui.screens.capture.CaptureScreen
import com.wildlife.feasibility.ui.screens.capture.CaptureUiState
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class CaptureActivity : ComponentActivity() {
    private lateinit var markerStore: MarkerStore
    private lateinit var accountStore: AccountStore
    private lateinit var observationStore: ObservationStore

    private var markers: List<PendingMarker> = emptyList()
    private val selectedMarkerIds = linkedSetOf<String>()
    private var proposalsByMarker: Map<String, List<MatchProposal>> = emptyMap()
    private var pendingCaptureFile: File? = null
    private var pendingCaptureUri: Uri? = null
    private var pendingCaptureTimeMs: Long = 0L
    private val retryHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = Runnable { fetchMatchesInternal(manual = false) }
    private var retryAttempt = 0
    private var matchFetchInFlight = false
    private var statusMessage = "Add photos to start one observation."
    private var reward: CaptureRewardUi? = null
    private var handoffUnavailable = false
    private var openObservationsAfterHandoff = false
    private var uiState by mutableStateOf(CaptureUiState())

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> completeCameraCapture(result.resultCode) }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) importSelectedUris(result.data)
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.none { it }) {
            showStatus("Location permission denied. You can add metadata manually later.")
        }
        launchCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(8, 11, 9)),
        )
        markerStore = MarkerStore(this)
        accountStore = AccountStore(this)
        observationStore = ObservationStore(this)
        markers = markerStore.load()
        render()
        setContent {
            WildlifeTheme {
                CaptureScreen(
                    state = uiState,
                    onBack = ::finish,
                    onTakePhoto = ::capturePhoto,
                    onChoosePhotos = ::importPhotos,
                    onTogglePhoto = ::togglePhoto,
                    onContinueInINaturalist = ::shareSelected,
                    onSubmitted = ::markHandoffSubmitted,
                    onNotSubmitted = ::markHandoffNotSubmitted,
                    onCheckNow = ::fetchMatches,
                    onConfirmMatch = ::confirmMatch,
                    onOpenObservation = ::openObservation,
                    onDelete = ::deleteGroup,
                    onSaveMetadata = ::saveMetadata,
                    onLinkAccount = ::openAccountLink,
                    onDismissReward = {
                        reward = null
                        render()
                    },
                    onOpenCollection = ::openCollection,
                    onDismissHandoffUnavailable = {
                        handoffUnavailable = false
                        render()
                    },
                    onOpenINaturalistWeb = {
                        openWebUploader()
                    },
                    onOpenINaturalistStore = {
                        handoffUnavailable = false
                        render()
                        openINaturalistStore()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        retryHandler.removeCallbacks(retryRunnable)
        if (::observationStore.isInitialized) observationStore.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::accountStore.isInitialized) {
            markers = markerStore.load()
            render()
            if (openObservationsAfterHandoff) {
                openObservationsAfterHandoff = false
                startActivity(MainActivity.observationsIntent(this, null))
                finish()
            }
        }
    }

    override fun onPause() {
        retryHandler.removeCallbacks(retryRunnable)
        super.onPause()
    }

    private fun capturePhoto() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        val directory = File(filesDir, "handoffs").apply { mkdirs() }
        val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "Wildlife capture", uri)
        }
        try {
            pendingCaptureFile = file
            pendingCaptureUri = uri
            pendingCaptureTimeMs = System.currentTimeMillis()
            cameraLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showStatus("No camera app is available.")
        }
    }

    private fun importPhotos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        importLauncher.launch(intent)
    }

    private fun completeCameraCapture(resultCode: Int) {
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        pendingCaptureFile = null
        pendingCaptureUri = null
        if (resultCode != Activity.RESULT_OK || file == null || uri == null || !file.exists()) {
            file?.delete()
            showStatus("Capture cancelled.")
            return
        }
        val location = lastKnownLocation()
        runCatching {
            PhotoMetadataReader.writeCaptureMetadata(file, pendingCaptureTimeMs, location)
        }
        addMarker(uri, "camera", pendingCaptureTimeMs, location)
        showStatus("Photo added. Add more only if they show the same sighting.")
    }

    private fun importSelectedUris(data: Intent?) {
        if (data == null) return
        val uris = buildList {
            data.clipData?.let { clips ->
                for (index in 0 until clips.itemCount) add(clips.getItemAt(index).uri)
            }
            data.data?.let(::add)
        }.distinct()
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            addMarker(uri, "import", System.currentTimeMillis(), null)
        }
        showStatus("Imported ${uris.size} photo(s). Select only photos of the same sighting.")
    }

    private fun addMarker(
        imageUri: Uri,
        source: String,
        fallbackTimeMs: Long,
        preferredLocation: Location?,
    ) {
        val metadata = runCatching {
            PhotoMetadataReader.read(contentResolver, imageUri, fallbackTimeMs)
        }.getOrElse {
            PhotoMetadata(
                fallbackTimeMs,
                preferredLocation?.latitude,
                preferredLocation?.longitude,
                capturedAtReliable = source != "import",
                locationReliable = preferredLocation != null,
            )
        }
        val marker = PendingMarker(
            id = UUID.randomUUID().toString(),
            imageUri = imageUri.toString(),
            source = source,
            capturedAtMs = metadata.capturedAtMs,
            latitude = metadata.latitude ?: preferredLocation?.latitude,
            longitude = metadata.longitude ?: preferredLocation?.longitude,
            capturedAtReliable = metadata.capturedAtReliable || source != "import",
            locationReliable = metadata.locationReliable || preferredLocation != null,
        )
        markers = markers + marker
        selectedMarkerIds += marker.id
        persistAndRender()
    }

    private fun togglePhoto(markerId: String, selected: Boolean) {
        val marker = markers.firstOrNull { it.id == markerId } ?: return
        if (marker.state != MarkerState.CAPTURED) return
        if (selected) selectedMarkerIds += markerId else selectedMarkerIds -= markerId
        render()
    }

    private fun shareSelected() {
        val chosen = markers.filter { it.id in selectedMarkerIds && it.state == MarkerState.CAPTURED }
        ObservationDraftValidator.problem(chosen)?.let {
            showStatus(it)
            return
        }
        val uris = ArrayList(chosen.map { Uri.parse(it.imageUri) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }.apply {
            type = "image/*"
            setPackage(INATURALIST_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "Wildlife photo", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
        try {
            startActivity(intent)
            markAsHandedOff(chosen)
            openObservationsAfterHandoff = true
            showStatus("iNaturalist opened for one observation. Review its status when you return.")
        } catch (_: ActivityNotFoundException) {
            handoffUnavailable = true
            showStatus("The official iNaturalist Android app is not installed.")
        } catch (error: SecurityException) {
            showStatus("iNaturalist could not read one of the selected photos: ${error.message}")
        }
    }

    private fun openWebUploader() {
        val chosen = markers.filter { it.id in selectedMarkerIds && it.state == MarkerState.CAPTURED }
        ObservationDraftValidator.problem(chosen)?.let {
            handoffUnavailable = false
            showStatus(it)
            return
        }
        handoffUnavailable = false
        markAsHandedOff(chosen)
        openObservationsAfterHandoff = true
        openExternal("https://www.inaturalist.org/observations/upload")
        showStatus("Web uploader opened. Review its status when you return.")
    }

    private fun markAsHandedOff(chosen: List<PendingMarker>) {
        val sharedIds = chosen.mapTo(hashSetOf()) { it.id }
        val now = System.currentTimeMillis()
        val handoffId = UUID.randomUUID().toString()
        markers = markers.map { marker ->
            if (marker.id in sharedIds) {
                marker.copy(
                    state = MarkerState.HANDED_OFF,
                    sharedAtMs = now,
                    handoffId = handoffId,
                )
            } else marker
        }
        selectedMarkerIds.removeAll(sharedIds)
        persistAndRender()
    }

    private fun markHandoffSubmitted(handoffId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == handoffId && marker.state == MarkerState.HANDED_OFF) {
                marker.copy(state = MarkerState.PENDING)
            } else marker
        }
        persistAndRender()
        retryAttempt = 0
        showStatus("Submission recorded. Waiting for iNaturalist's public API.")
        scheduleRetry()
    }

    private fun markHandoffNotSubmitted(handoffId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == handoffId && marker.state == MarkerState.HANDED_OFF) {
                selectedMarkerIds += marker.id
                marker.copy(state = MarkerState.CAPTURED, sharedAtMs = null, handoffId = null)
            } else marker
        }
        persistAndRender()
        showStatus("Observation returned to draft.")
    }

    private fun fetchMatches() {
        retryAttempt = 0
        fetchMatchesInternal(manual = true)
    }

    private fun fetchMatchesInternal(manual: Boolean) {
        if (matchFetchInFlight) return
        val account = accountStore.verified()
        val openMarkers = pendingRepresentatives()
        if (account == null) {
            if (manual) showStatus("Link your iNaturalist account before matching observations.")
            return
        }
        if (openMarkers.isEmpty()) {
            if (manual) showStatus("There are no pending observations to check.")
            return
        }
        retryHandler.removeCallbacks(retryRunnable)
        matchFetchInFlight = true
        showStatus("Checking the verified iNaturalist account…")
        thread(name = "wildlife-match-sync") {
            runCatching {
                OnDeviceWildlifeRepository(this).use { repository ->
                    val sync = repository.syncObservations(account)
                    confirmPreviouslyMatched(repository, account)
                    val alreadyMatched = markers.asSequence()
                        .filter { it.state == MarkerState.CONFIRMED }
                        .mapNotNull(PendingMarker::matchedObservationUuid)
                        .toSet()
                    val candidates = observationStore.candidates(account.userId)
                        .filterNot { it.uuid in alreadyMatched }
                    sync to openMarkers.associate { marker ->
                        marker.id to CandidateMatcher.proposals(marker, candidates)
                    }
                }
            }.onSuccess { (sync, proposals) ->
                runOnUiThread {
                    matchFetchInFlight = false
                    proposalsByMarker = proposals
                    render()
                    val count = proposals.values.sumOf(List<MatchProposal>::size)
                    if (count > 0) {
                        retryAttempt = 0
                        showStatus("Found $count possible match(es). Inspect and confirm the correct one.")
                    } else {
                        if (manual && sync.observations.isNotEmpty()) {
                            showStatus("Collection synced. This observation is not public yet.")
                        }
                        scheduleRetry()
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    matchFetchInFlight = false
                    showStatus("Could not check iNaturalist: ${error.message}")
                    scheduleRetry()
                }
            }
        }
    }

    private fun confirmPreviouslyMatched(
        repository: OnDeviceWildlifeRepository,
        account: VerifiedAccount,
    ) {
        markers.asSequence()
            .filter { it.state == MarkerState.CONFIRMED }
            .mapNotNull(PendingMarker::matchedObservationUuid)
            .distinct()
            .forEach { uuid ->
                repository.confirmObservation(account, uuid)
            }
    }

    private fun confirmMatch(proposal: MatchProposal) {
        val matchedMarker = markers.firstOrNull { it.id == proposal.markerId } ?: return
        val matchedGroup = groupKey(matchedMarker)
        val rewardPhoto = markers.firstOrNull { groupKey(it) == matchedGroup }?.imageUri
        markers = markers.map { marker ->
            if (groupKey(marker) == matchedGroup) {
                marker.copy(
                    state = MarkerState.CONFIRMED,
                    matchedObservationUuid = proposal.candidate.uuid,
                )
            } else marker
        }
        markers.filter { groupKey(it) == matchedGroup }.forEach { selectedMarkerIds -= it.id }
        proposalsByMarker = proposalsByMarker - proposal.markerId
        retryHandler.removeCallbacks(retryRunnable)
        retryAttempt = 0
        persistAndRender()
        showStatus("Match confirmed. Recording the collection reward…")
        val account = accountStore.verified()
        if (account == null) {
            showStatus("Matched locally. Link the account again to record the reward.")
            return
        }
        thread(name = "wildlife-confirm-reward") {
            runCatching {
                OnDeviceWildlifeRepository(this).use {
                    it.confirmObservation(account, proposal.candidate.uuid)
                }
            }
                .onSuccess { result ->
                    val label = observationStore.observations(account.userId)
                        .firstOrNull { it.uuid == proposal.candidate.uuid }
                        ?.label
                        .orEmpty()
                        .ifBlank { "Public iNaturalist observation" }
                    runOnUiThread {
                        reward = CaptureRewardUi(label, result.xpAwarded, rewardPhoto)
                        showStatus(
                            if (result.xpAwarded > 0) {
                                "Observation confirmed · +${result.xpAwarded} XP"
                            } else {
                                "Observation was already rewarded."
                            },
                        )
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        showStatus("Confirmed locally. Reward will retry on sync: ${error.message}")
                    }
                }
        }
        if (hasPendingHandoffs()) scheduleRetry()
    }

    private fun saveMetadata(markerId: String, date: String, latitudeText: String, longitudeText: String) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
        val observedAt = runCatching { formatter.parse(date)?.time }.getOrNull()
        val latitude = latitudeText.toDoubleOrNull()
        val longitude = longitudeText.toDoubleOrNull()
        val locationBlank = latitudeText.isBlank() && longitudeText.isBlank()
        if (observedAt == null) {
            showStatus("Enter a valid original date and time.")
            return
        }
        if (!locationBlank &&
            (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0)
        ) {
            showStatus("Enter valid latitude/longitude, or leave both blank.")
            return
        }
        markers = markers.map { marker ->
            if (marker.id == markerId) {
                marker.copy(
                    capturedAtMs = observedAt,
                    latitude = latitude,
                    longitude = longitude,
                    capturedAtReliable = true,
                    locationReliable = !locationBlank,
                )
            } else marker
        }
        persistAndRender()
        showStatus("Original observation metadata saved.")
    }

    private fun deleteGroup(groupId: String) {
        val deleting = markers.filter { groupKey(it) == groupId }
        val deletingIds = deleting.mapTo(hashSetOf()) { it.id }
        deleting.forEach(::deletePrivatePhotoIfOwned)
        markers = LocalObservationRecords.removeGroup(markers, groupId)
        selectedMarkerIds.removeAll(deletingIds)
        proposalsByMarker = proposalsByMarker - deletingIds
        retryHandler.removeCallbacks(retryRunnable)
        retryAttempt = 0
        persistAndRender()
        showStatus("Removed from Wildlife. Nothing was deleted from iNaturalist.")
        if (hasPendingHandoffs()) scheduleRetry()
    }

    private fun deletePrivatePhotoIfOwned(marker: PendingMarker) {
        if (marker.source != "camera") return
        val filename = Uri.parse(marker.imageUri).lastPathSegment ?: return
        runCatching {
            val directory = File(filesDir, "handoffs").canonicalFile
            val target = File(directory, filename).canonicalFile
            if (target.parentFile == directory && target.isFile) target.delete()
        }
    }

    private fun pendingRepresentatives(): List<PendingMarker> = markers
        .filter { it.state == MarkerState.PENDING }
        .groupBy(::groupKey)
        .values
        .map { it.minBy(PendingMarker::capturedAtMs) }

    private fun groupKey(marker: PendingMarker): String = marker.handoffId ?: marker.id

    private fun hasPendingHandoffs(): Boolean =
        markers.any { it.state == MarkerState.PENDING } && accountStore.verified() != null

    private fun scheduleRetry() {
        if (!hasPendingHandoffs() || isFinishing) return
        retryHandler.removeCallbacks(retryRunnable)
        val delayMs = MatchRetryPolicy.delayMs(retryAttempt++)
        showStatus("Observation not public yet. Checking again in ${delayMs / 1_000} seconds.")
        retryHandler.postDelayed(retryRunnable, delayMs)
    }

    private fun lastKnownLocation(): Location? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull(Location::getTime)
    }

    private fun openObservation(uuid: String) {
        openExternal("https://www.inaturalist.org/observations/$uuid")
    }

    private fun openINaturalistStore() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$INATURALIST_PACKAGE")))
        }.onFailure {
            openExternal("https://play.google.com/store/apps/details?id=$INATURALIST_PACKAGE")
        }
    }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { showStatus("No app is available to open this link.") }
    }

    private fun openAccountLink() {
        startActivity(
            Intent(this, AccountLinkActivity::class.java).apply {
                putExtra(AccountLinkActivity.EXTRA_USERNAME, accountStore.verified()?.login.orEmpty())
            },
        )
    }

    private fun openCollection() {
        reward = null
        startActivity(MainActivity.intent(this, WildlifeDestination.COLLECTION))
        finish()
    }

    private fun persistAndRender() {
        markerStore.save(markers)
        render()
    }

    private fun showStatus(message: String) {
        statusMessage = message
        render()
    }

    private fun render() {
        uiState = CaptureProjection.build(
            markers = markers.filter { it.state == MarkerState.CAPTURED },
            selectedMarkerIds = selectedMarkerIds,
            proposalsByMarker = proposalsByMarker,
            account = accountStore.verified(),
            statusMessage = statusMessage,
            busy = matchFetchInFlight,
            reward = reward,
            handoffUnavailable = handoffUnavailable,
        )
    }

    companion object {
        private const val INATURALIST_PACKAGE = "org.inaturalist.android"
    }
}
