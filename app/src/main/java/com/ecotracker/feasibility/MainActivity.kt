package com.wildlife.feasibility

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var markerStore: MarkerStore
    private lateinit var accountStore: AccountStore
    private lateinit var observationStore: ObservationStore
    private lateinit var usernameInput: EditText
    private lateinit var accountStatusText: TextView
    private lateinit var manageAccountButton: Button
    private lateinit var collectionStatusText: TextView
    private lateinit var statusText: TextView
    private lateinit var markerContainer: LinearLayout

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSafeSystemBars()
        markerStore = MarkerStore(this)
        accountStore = AccountStore(this)
        observationStore = ObservationStore(this)
        markers = markerStore.load()
        buildUi()
        refreshAccountUi()
        renderMarkers()
    }

    override fun onResume() {
        super.onResume()
        if (::usernameInput.isInitialized) {
            refreshAccountUi()
            refreshCollectionUi()
            if (hasPendingHandoffs()) scheduleRetry()
        }
    }

    override fun onPause() {
        retryHandler.removeCallbacks(retryRunnable)
        super.onPause()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            applySystemBarPadding()
            setBackgroundColor(COLOR_BACKGROUND)
        }

        root.addView(TextView(this).apply {
            text = "Wildlife"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_FOREST)
        })
        root.addView(TextView(this).apply {
            text = "Create one observation, finish it in iNaturalist, then confirm it here."
            textSize = 14f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(2), 0, dp(14))
        })

        root.addView(sectionTitle("1 · iNaturalist account"), matchWidth())
        usernameInput = EditText(this).apply {
            hint = "Your exact iNaturalist username"
            setSingleLine(true)
            setText(getPreferences(MODE_PRIVATE).getString(KEY_USERNAME, ""))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(Color.WHITE, strokeColor = COLOR_BORDER)
        }
        root.addView(usernameInput, matchWidth())
        accountStatusText = TextView(this).apply {
            textSize = 13f
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }
        root.addView(accountStatusText, matchWidth())
        manageAccountButton = button("Verify account", ::openAccountLink)
        root.addView(actionRow(
            manageAccountButton,
            button("View observations", ::browseUserObservations),
        ))

        collectionStatusText = TextView(this).apply {
            textSize = 14f
            setTextColor(COLOR_FOREST)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(COLOR_STATUS)
        }
        root.addView(collectionStatusText, matchWidth().apply { topMargin = dp(8) })
        root.addView(actionRow(
            primaryButton("Open collection", ::openCollection),
            button("Sync collection", ::syncCollection),
        ))

        root.addView(sectionTitle("2 · New observation"), matchWidth().apply { topMargin = dp(10) })
        root.addView(TextView(this).apply {
            text = "Choose one or more photos of the same sighting."
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(actionRow(
            button("Take a photo", ::capturePhoto),
            button("Choose photos", ::importPhotos),
        ))
        root.addView(primaryButton("Continue in iNaturalist", ::shareSelected), matchWidth())

        statusText = TextView(this).apply {
            text = "Ready"
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(COLOR_FOREST)
            background = roundedBackground(COLOR_STATUS)
        }
        root.addView(statusText, matchWidth().apply { topMargin = dp(8) })

        root.addView(actionRow(
            button("Check pending now", ::fetchMatches),
            button("Refresh list", ::renderMarkers),
        ))

        root.addView(sectionTitle("Your observations"), matchWidth().apply { topMargin = dp(8) })

        markerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply { addView(markerContainer, matchWidth()) }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        setContentView(root)
        refreshCollectionUi()
    }

    private fun capturePhoto() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                REQUEST_LOCATION,
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
            startActivityForResult(intent, REQUEST_CAMERA)
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
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    private fun createTestPhoto() {
        val capturedAt = System.currentTimeMillis()
        val directory = File(filesDir, "handoffs").apply { mkdirs() }
        val file = File(directory, "test-$capturedAt.jpg")
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(219, 238, 220))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 64, 32)
            textSize = 54f
        }
        canvas.drawText("Wildlife handoff test", 90f, 410f, paint)
        paint.textSize = 34f
        canvas.drawText(DateFormat.getDateTimeInstance().format(Date(capturedAt)), 90f, 475f, paint)

        runCatching {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            }
            bitmap.recycle()
            // Disposable validation photos deliberately contain no device location.
            PhotoMetadataReader.writeCaptureMetadata(file, capturedAt, null)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            addMarker(uri, "generated-test", capturedAt, null)
            showStatus("Disposable test photo created and selected.")
        }.onFailure { error ->
            bitmap.recycle()
            file.delete()
            showStatus("Could not create test photo: ${error.message}")
        }
    }

    @Deprecated("Used intentionally for this throwaway API-26 feasibility prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CAMERA -> completeCameraCapture(resultCode)
            REQUEST_IMPORT -> if (resultCode == RESULT_OK) importSelectedUris(data)
        }
    }

    private fun completeCameraCapture(resultCode: Int) {
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        if (resultCode != RESULT_OK || file == null || uri == null || !file.exists()) {
            file?.delete()
            showStatus("Capture cancelled.")
            return
        }

        val location = lastKnownLocation()
        runCatching {
            PhotoMetadataReader.writeCaptureMetadata(file, pendingCaptureTimeMs, location)
        }.onFailure { error ->
            showStatus("Photo saved, but EXIF update failed: ${error.message}")
        }
        addMarker(
            imageUri = uri,
            source = "camera",
            fallbackTimeMs = pendingCaptureTimeMs,
            preferredLocation = location,
        )
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
        showStatus("Imported ${uris.size} photo(s).")
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

    private fun shareSelected() {
        val chosen = markers.filter { it.id in selectedMarkerIds && it.state == MarkerState.CAPTURED }
        if (chosen.isEmpty()) {
            showStatus("Select at least one draft photo.")
            return
        }
        ObservationDraftValidator.problem(chosen)?.let { problem ->
            showStatus(problem)
            return
        }

        val uris = ArrayList(chosen.map { Uri.parse(it.imageUri) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris.single())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            type = "image/*"
            setPackage(INATURALIST_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "Wildlife photo", uris.first()).apply {
                uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
            }
        }

        try {
            startActivity(intent)
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
                } else {
                    marker
                }
            }
            selectedMarkerIds.removeAll(sharedIds)
            persistAndRender()
            showStatus("Handoff launched for one observation. Confirm submission when you return.")
        } catch (_: ActivityNotFoundException) {
            showStatus("The official iNaturalist Android app is not installed.")
        } catch (error: SecurityException) {
            showStatus("The receiving app could not read a photo URI: ${error.message}")
        }
    }

    private fun fetchMatches() {
        retryAttempt = 0
        fetchMatchesInternal(manual = true)
    }

    private fun fetchMatchesInternal(manual: Boolean) {
        if (matchFetchInFlight) return
        val verifiedAccount = accountStore.verified()
        val openMarkers = pendingRepresentatives()
        if (verifiedAccount == null) {
            if (manual) showStatus("Verify your iNaturalist account before matching observations.")
            return
        }
        if (openMarkers.isEmpty()) {
            if (manual) showStatus("There are no pending handoffs to match.")
            return
        }
        retryHandler.removeCallbacks(retryRunnable)
        matchFetchInFlight = true
        showStatus("Syncing the verified iNaturalist account…")

        thread(name = "wildlife-match-sync") {
            runCatching {
                val client = BackendClient()
                val user = INaturalistUser(verifiedAccount.userId, verifiedAccount.login)
                val sync = client.sync(verifiedAccount)
                observationStore.replaceSnapshot(user.id, sync)
                confirmPreviouslyMatched(client, verifiedAccount)
                val candidates = observationStore.candidates(user.id)
                Triple(user, sync, openMarkers.associate { marker ->
                    marker.id to CandidateMatcher.proposals(marker, candidates)
                })
            }.onSuccess { (user, sync, proposals) ->
                runOnUiThread {
                    matchFetchInFlight = false
                    proposalsByMarker = proposals
                    refreshCollectionUi()
                    renderMarkers()
                    val count = proposals.values.sumOf(List<MatchProposal>::size)
                    if (count > 0) {
                        retryAttempt = 0
                        showStatus(
                            "Found $count candidate match(es) for ${user.login} (#${user.id}). " +
                                "Confirmation is always explicit.",
                        )
                    } else {
                        if (manual && sync.observations.isNotEmpty()) {
                            showStatus("Collection synced. The submitted observation is not public yet.")
                        }
                        scheduleRetry()
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    matchFetchInFlight = false
                    showStatus("Match fetch failed: ${error.message}")
                    scheduleRetry()
                }
            }
        }
    }

    private fun syncCollection() {
        if (matchFetchInFlight) return
        val account = accountStore.verified()
        if (account == null) {
            showStatus("Verify your iNaturalist account before syncing the collection.")
            return
        }
        matchFetchInFlight = true
        showStatus("Syncing confirmed collection…")
        thread(name = "wildlife-collection-sync") {
            runCatching {
                val client = BackendClient()
                val result = client.sync(account)
                observationStore.replaceSnapshot(account.userId, result)
                confirmPreviouslyMatched(client, account)
                result
            }.onSuccess { result ->
                runOnUiThread {
                    matchFetchInFlight = false
                    refreshCollectionUi()
                    showStatus("Collection synced: ${result.observations.size} public observation(s) cached.")
                }
            }.onFailure { error ->
                runOnUiThread {
                    matchFetchInFlight = false
                    showStatus("Collection sync failed: ${error.message}")
                }
            }
        }
    }

    private fun confirmPreviouslyMatched(client: BackendClient, account: VerifiedAccount) {
        markers.asSequence()
            .filter { it.state == MarkerState.CONFIRMED }
            .mapNotNull(PendingMarker::matchedObservationUuid)
            .distinct()
            .forEach { uuid ->
                val result = client.confirm(account.userId, uuid)
                observationStore.updateConfirmation(account.userId, uuid, result.summary)
            }
    }

    private fun hasPendingHandoffs(): Boolean =
        markers.any(::isPending) && accountStore.verified() != null

    private fun isPending(marker: PendingMarker): Boolean =
        marker.state == MarkerState.PENDING

    private fun pendingRepresentatives(): List<PendingMarker> = markers
        .filter(::isPending)
        .groupBy(::groupKey)
        .values
        .map { group -> group.minBy(PendingMarker::capturedAtMs) }

    private fun groupKey(marker: PendingMarker): String = marker.handoffId ?: marker.id

    private fun scheduleRetry() {
        if (!hasPendingHandoffs() || isFinishing) return
        retryHandler.removeCallbacks(retryRunnable)
        val delayMs = MatchRetryPolicy.delayMs(retryAttempt++)
        showStatus("Observation not public yet. Checking again in ${delayMs / 1_000} seconds.")
        retryHandler.postDelayed(retryRunnable, delayMs)
    }

    private fun browseUserObservations() {
        val username = accountStore.verified()?.login ?: usernameInput.text.toString().trim()
        startActivity(
            Intent(this, UserObservationsActivity::class.java).apply {
                putExtra(UserObservationsActivity.EXTRA_USERNAME, username)
            },
        )
    }

    private fun openCollection() {
        if (accountStore.verified() == null) {
            showStatus("Verify your iNaturalist account before opening the collection.")
            return
        }
        startActivity(Intent(this, CollectionActivity::class.java))
    }

    private fun openAccountLink() {
        startActivity(
            Intent(this, AccountLinkActivity::class.java).apply {
                putExtra(AccountLinkActivity.EXTRA_USERNAME, usernameInput.text.toString().trim())
            },
        )
    }

    private fun refreshAccountUi() {
        if (!::accountStatusText.isInitialized) return
        val account = accountStore.verified()
        if (account == null) {
            usernameInput.isEnabled = true
            accountStatusText.text = "Not verified · matching and rewards are disabled"
            accountStatusText.setTextColor(COLOR_AMBER)
            manageAccountButton.text = "Verify account"
        } else {
            usernameInput.setText(account.login)
            usernameInput.isEnabled = false
            accountStatusText.text = "✓ Verified · iNaturalist user ID #${account.userId}"
            accountStatusText.setTextColor(COLOR_FOREST)
            manageAccountButton.text = "Manage account"
        }
        refreshCollectionUi()
    }

    private fun refreshCollectionUi() {
        if (!::collectionStatusText.isInitialized) return
        val account = accountStore.verified()
        if (account == null) {
            collectionStatusText.text = "Collection locked · verify your account to sync and earn XP"
            return
        }
        val summary = observationStore.summary(account.userId)
        val observations = observationStore.observations(account.userId)
        val speciesCount = CollectionProjection.species(observations).size
        collectionStatusText.text =
            "$speciesCount collection entries · ${observations.size} observations · ${summary.totalXp} XP"
    }

    private fun markHandoffSubmitted(handoffId: String) {
        markers = markers.map { marker ->
            if (groupKey(marker) == handoffId && marker.state == MarkerState.HANDED_OFF) {
                marker.copy(state = MarkerState.PENDING)
            } else {
                marker
            }
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
            } else {
                marker
            }
        }
        persistAndRender()
        showStatus("Observation returned to draft.")
    }

    private fun confirmMatch(proposal: MatchProposal) {
        val matchedMarker = markers.firstOrNull { it.id == proposal.markerId } ?: return
        val matchedGroup = groupKey(matchedMarker)
        markers = markers.map { marker ->
            if (groupKey(marker) == matchedGroup) {
                marker.copy(
                    state = MarkerState.CONFIRMED,
                    matchedObservationUuid = proposal.candidate.uuid,
                )
            } else {
                marker
            }
        }
        markers.filter { groupKey(it) == matchedGroup }.forEach { selectedMarkerIds -= it.id }
        proposalsByMarker = proposalsByMarker - proposal.markerId
        retryHandler.removeCallbacks(retryRunnable)
        retryAttempt = 0
        persistAndRender()
        showStatus("Observation confirmed. Recording collection and XP…")
        accountStore.verified()?.let { account ->
            thread(name = "wildlife-confirm-reward") {
                runCatching { BackendClient().confirm(account.userId, proposal.candidate.uuid) }
                    .onSuccess { result ->
                        observationStore.updateConfirmation(
                            account.userId,
                            proposal.candidate.uuid,
                            result.summary,
                        )
                        runOnUiThread {
                            refreshCollectionUi()
                            showStatus(
                                if (result.xpAwarded > 0) {
                                    "Observation confirmed · +${result.xpAwarded} XP"
                                } else {
                                    "Observation was already confirmed · no duplicate XP"
                                },
                            )
                        }
                    }
                    .onFailure { error ->
                        runOnUiThread {
                            showStatus("Confirmed locally. Reward will retry on sync: ${error.message}")
                        }
                    }
            }
        }
        if (hasPendingHandoffs()) scheduleRetry()
    }

    private fun renderMarkers() {
        markerContainer.removeAllViews()
        if (markers.isEmpty()) {
            markerContainer.addView(TextView(this).apply {
                text = "No observations yet\nTake or choose photos to create your first draft."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(COLOR_MUTED)
                setPadding(dp(18), dp(28), dp(18), dp(28))
                background = roundedBackground(Color.WHITE, strokeColor = COLOR_BORDER)
            })
            return
        }

        markers.groupBy(::groupKey).values
            .sortedByDescending { group -> group.maxOf(PendingMarker::capturedAtMs) }
            .forEach { unsortedGroup ->
            val group = unsortedGroup.sortedBy(PendingMarker::capturedAtMs)
            val leader = group.first()
            val groupId = groupKey(leader)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBackground(Color.WHITE, radiusDp = 16, strokeColor = COLOR_BORDER)
            }

            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = friendlyState(leader.state)
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(stateColor(leader.state))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = if (group.size == 1) "1 photo" else "${group.size} photos"
                    textSize = 13f
                    setTextColor(COLOR_MUTED)
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    background = roundedBackground(COLOR_CHIP, radiusDp = 20)
                })
            }, matchWidth())

            card.addView(TextView(this).apply {
                text = stateExplanation(leader.state)
                textSize = 13f
                setTextColor(COLOR_MUTED)
                setPadding(0, dp(3), 0, dp(8))
            })

            group.forEachIndexed { index, marker ->
                val photoRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                photoRow.addView(ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = roundedBackground(COLOR_CHIP, radiusDp = 10)
                    runCatching { setImageURI(Uri.parse(marker.imageUri)) }
                }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginEnd = dp(10) })

                photoRow.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    if (marker.state == MarkerState.CAPTURED) {
                        addView(CheckBox(this@MainActivity).apply {
                            text = if (group.size == 1) "Include this photo" else "Photo ${index + 1}"
                            isChecked = marker.id in selectedMarkerIds
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedMarkerIds += marker.id else selectedMarkerIds -= marker.id
                            }
                        }, matchWidth())
                    } else {
                        addView(TextView(this@MainActivity).apply {
                            text = "Photo ${index + 1}"
                            setTypeface(typeface, Typeface.BOLD)
                            textSize = 14f
                        })
                    }
                    addView(TextView(this@MainActivity).apply {
                        text = metadataSummary(marker)
                        textSize = 12f
                        setTextColor(COLOR_MUTED)
                    })
                    if (marker.state == MarkerState.CAPTURED &&
                        (!marker.capturedAtReliable || !marker.locationReliable)
                    ) {
                        addView(textButton("Add missing date/location") { editMetadata(marker) })
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                card.addView(photoRow, matchWidth())
            }

            if (leader.state == MarkerState.HANDED_OFF) {
                card.addView(TextView(this).apply {
                    text = "Did you submit this observation in iNaturalist?"
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(8), 0, dp(4))
                })
                card.addView(actionRow(
                    primaryButton("Yes, I submitted it") { markHandoffSubmitted(groupId) },
                    button("No, keep as draft") { markHandoffNotSubmitted(groupId) },
                ))
            }

            proposalsByMarker[leader.id].orEmpty().forEach { proposal ->
                card.addView(proposalView(proposal), matchWidth())
            }

            leader.matchedObservationUuid?.let { uuid ->
                card.addView(button("Open in iNaturalist") {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.inaturalist.org/observations/$uuid"),
                        ),
                    )
                }, matchWidth())
            }

            card.addView(dangerButton("Delete from Wildlife") { confirmDeleteGroup(groupId, leader.state) },
                matchWidth().apply { topMargin = dp(6) })

            markerContainer.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(10) },
            )
        }
    }

    private fun friendlyState(state: MarkerState): String = when (state) {
        MarkerState.CAPTURED -> "Draft observation"
        MarkerState.HANDED_OFF -> "Waiting for your answer"
        MarkerState.PENDING -> "Checking iNaturalist"
        MarkerState.CONFIRMED -> "Confirmed"
    }

    private fun stateExplanation(state: MarkerState): String = when (state) {
        MarkerState.CAPTURED -> "Select its photos, then continue in iNaturalist."
        MarkerState.HANDED_OFF -> "Tell Wildlife whether you completed the iNaturalist form."
        MarkerState.PENDING -> "Submitted; waiting for it to appear in the public API."
        MarkerState.CONFIRMED -> "Matched to a public iNaturalist observation."
    }

    private fun stateColor(state: MarkerState): Int = when (state) {
        MarkerState.CAPTURED -> COLOR_BLUE
        MarkerState.HANDED_OFF -> COLOR_AMBER
        MarkerState.PENDING -> COLOR_AMBER
        MarkerState.CONFIRMED -> COLOR_FOREST
    }

    private fun metadataSummary(marker: PendingMarker): String = buildString {
        if (marker.capturedAtReliable) {
            append(DateFormat.getDateTimeInstance().format(Date(marker.capturedAtMs)))
        } else {
            append("Original date/time needed")
        }
        append("\n")
        if (marker.locationReliable && marker.latitude != null && marker.longitude != null) {
            append("Location saved with Wildlife")
        } else {
            append("Original location not available")
        }
    }

    private fun confirmDeleteGroup(groupId: String, state: MarkerState) {
        val stateNote = if (state == MarkerState.CONFIRMED) {
            "The iNaturalist observation will remain online."
        } else {
            "Any matching checks for this observation will stop."
        }
        AlertDialog.Builder(this)
            .setTitle("Delete from Wildlife?")
            .setMessage(
                "$stateNote Imported original photos are not deleted. " +
                    "This action only removes Wildlife's local record and its private camera copies.",
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteGroup(groupId) }
            .show()
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
        if (marker.source != "camera" && marker.source != "generated-test") return
        val filename = Uri.parse(marker.imageUri).lastPathSegment ?: return
        runCatching {
            val directory = File(filesDir, "handoffs").canonicalFile
            val target = File(directory, filename).canonicalFile
            if (target.parentFile == directory && target.isFile) target.delete()
        }
    }

    private fun editMetadata(marker: PendingMarker) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        val dateInput = EditText(this).apply {
            hint = "Observed date/time: YYYY-MM-DD HH:MM"
            setText(if (marker.capturedAtReliable) formatter.format(Date(marker.capturedAtMs)) else "")
        }
        val latitudeInput = EditText(this).apply {
            hint = "Latitude (optional)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(if (marker.locationReliable) marker.latitude?.toString().orEmpty() else "")
        }
        val longitudeInput = EditText(this).apply {
            hint = "Longitude (optional)"
            inputType = latitudeInput.inputType
            setText(if (marker.locationReliable) marker.longitude?.toString().orEmpty() else "")
        }
        form.addView(dateInput, matchWidth())
        form.addView(latitudeInput, matchWidth())
        form.addView(longitudeInput, matchWidth())

        AlertDialog.Builder(this)
            .setTitle("Original observation metadata")
            .setMessage("Use where and when the photo was taken—not where or when it is uploaded.")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val observedAt = runCatching { formatter.parse(dateInput.text.toString())?.time }
                    .getOrNull()
                val latitude = latitudeInput.text.toString().toDoubleOrNull()
                val longitude = longitudeInput.text.toString().toDoubleOrNull()
                val locationBlank = latitudeInput.text.isBlank() && longitudeInput.text.isBlank()
                if (observedAt == null) {
                    showStatus("Enter a valid original date and time.")
                    return@setPositiveButton
                }
                if (!locationBlank &&
                    (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0)
                ) {
                    showStatus("Enter valid latitude/longitude, or leave both blank.")
                    return@setPositiveButton
                }
                markers = markers.map { existing ->
                    if (existing.id == marker.id) {
                        existing.copy(
                            capturedAtMs = observedAt,
                            latitude = latitude,
                            longitude = longitude,
                            capturedAtReliable = true,
                            locationReliable = !locationBlank,
                        )
                    } else {
                        existing
                    }
                }
                persistAndRender()
                showStatus("Original metadata saved in Wildlife.")
            }
            .show()
    }

    private fun proposalView(proposal: MatchProposal): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setBackgroundColor(Color.rgb(232, 245, 233))
        addView(TextView(this@MainActivity).apply {
            val distance = proposal.distanceKm?.let { "${(it * 10).roundToInt() / 10.0} km" }
                ?: "location unavailable/obscured"
            text = "${proposal.confidence.name.lowercase().replace('_', ' ')} · " +
                "${proposal.timeDeltaMinutes} min · $distance\n${proposal.candidate.uuid}"
            textSize = 12f
        })
        addView(actionRow(
            button("Open iNaturalist") {
                val url = Uri.parse(
                    "https://www.inaturalist.org/observations/${proposal.candidate.uuid}",
                )
                startActivity(Intent(Intent.ACTION_VIEW, url))
            },
            button("Confirm match") { confirmMatch(proposal) },
        ))
    }

    private fun lastKnownLocation(): Location? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null

        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                launchCamera()
            } else {
                showStatus("Location denied. You can import an existing geotagged photo instead.")
                launchCamera()
            }
        }
    }

    private fun persistAndRender() {
        markerStore.save(markers)
        renderMarkers()
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(COLOR_FOREST)
        backgroundTintList = ColorStateList.valueOf(COLOR_BUTTON)
        setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit) = button(label, action).apply {
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        backgroundTintList = ColorStateList.valueOf(COLOR_FOREST)
    }

    private fun dangerButton(label: String, action: () -> Unit) = button(label, action).apply {
        setTextColor(COLOR_DANGER)
        backgroundTintList = ColorStateList.valueOf(COLOR_DANGER_BACKGROUND)
    }

    private fun textButton(label: String, action: () -> Unit) = button(label, action).apply {
        minHeight = 0
        minimumHeight = 0
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        setTextColor(COLOR_BLUE)
    }

    private fun sectionTitle(label: String) = TextView(this).apply {
        text = label
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(COLOR_FOREST)
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int = 12,
        strokeColor: Int? = null,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun actionRow(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { view ->
            addView(
                view,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                },
            )
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val INATURALIST_PACKAGE = "org.inaturalist.android"
        private const val REQUEST_CAMERA = 100
        private const val REQUEST_IMPORT = 101
        private const val REQUEST_LOCATION = 102
        private const val KEY_USERNAME = "inat_username"

        private val COLOR_BACKGROUND = Color.rgb(244, 248, 244)
        private val COLOR_FOREST = Color.rgb(26, 86, 48)
        private val COLOR_MUTED = Color.rgb(88, 103, 92)
        private val COLOR_BORDER = Color.rgb(214, 225, 216)
        private val COLOR_BUTTON = Color.rgb(226, 239, 228)
        private val COLOR_STATUS = Color.rgb(230, 242, 233)
        private val COLOR_CHIP = Color.rgb(238, 243, 239)
        private val COLOR_BLUE = Color.rgb(35, 92, 150)
        private val COLOR_AMBER = Color.rgb(151, 94, 13)
        private val COLOR_DANGER = Color.rgb(156, 45, 45)
        private val COLOR_DANGER_BACKGROUND = Color.rgb(252, 235, 235)
    }
}
