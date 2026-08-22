package com.wildlife.feasibility

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.time.Instant

data class LocalDataInventory(
    val appVersionName: String = "unknown",
    val appVersionCode: Long = 0,
    val androidSdk: Int = 0,
    val accountLinked: Boolean = false,
    val cachedObservations: Int = 0,
    val hiddenMapObservations: Int = 0,
    val catalogueSpecies: Int = 0,
    val draftCaptures: Int = 0,
    val pendingHandoffs: Int = 0,
    val readyToReview: Int = 0,
    val privateCaptureFiles: Int = 0,
    val privateCaptureBytes: Long = 0,
    val referenceMediaFiles: Int = 0,
    val referenceMediaBytes: Long = 0,
    val lastObservationSyncAtMs: Long? = null,
) {
    fun privacySafeTestReport(observationSyncErrorPresent: Boolean): String = buildString {
        appendLine("Wildlife test report")
        appendLine("App: $appVersionName ($appVersionCode)")
        appendLine("Android SDK: $androidSdk")
        appendLine("Account linked: ${yesNo(accountLinked)}")
        appendLine("Last observation sync: ${lastObservationSyncAtMs?.let(Instant::ofEpochMilli) ?: "never"}")
        appendLine("Observation sync error present: ${yesNo(observationSyncErrorPresent)}")
        appendLine("Cached observations: $cachedObservations")
        appendLine("Map-hidden observations: $hiddenMapObservations")
        appendLine("Catalogue species: $catalogueSpecies")
        appendLine("Draft captures: $draftCaptures")
        appendLine("Pending handoffs: $pendingHandoffs")
        appendLine("Ready to review: $readyToReview")
        appendLine("Private capture files: $privateCaptureFiles (${formatBytes(privateCaptureBytes)})")
        appendLine("Reference media files: $referenceMediaFiles (${formatBytes(referenceMediaBytes)})")
        append("Generated: ${Instant.now()}")
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}

class LocalDataManager(context: Context) {
    private val appContext = context.applicationContext

    fun inventory(): LocalDataInventory {
        val account = AccountStore(appContext).verified()
        val observationStore = ObservationStore(appContext)
        val observations = account?.let { observationStore.observations(it.userId) }.orEmpty()
        val markers = MarkerStore(appContext).load()
        val pendingStatus = account?.let {
            ObservationLifecyclePolicy.pendingStatus(
                markers = markers,
                candidates = observationStore.candidates(it.userId),
            )
        }
        val captureFiles = fileSummary(File(appContext.filesDir, HANDOFF_DIRECTORY))
        val media = LocalMediaStore(appContext).summary()
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val inventory = LocalDataInventory(
            appVersionName = packageInfo.versionName ?: "unknown",
            appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            androidSdk = Build.VERSION.SDK_INT,
            accountLinked = account != null,
            cachedObservations = observations.size,
            hiddenMapObservations = account?.let {
                observationStore.mapHiddenObservationUuids(it.userId).size
            } ?: 0,
            catalogueSpecies = CatalogueStore(appContext).use {
                it.load()?.species?.size ?: 0
            },
            draftCaptures = markers.count { it.state == MarkerState.CAPTURED },
            pendingHandoffs = markers.count {
                it.state == MarkerState.HANDED_OFF || it.state == MarkerState.PENDING
            },
            readyToReview = pendingStatus?.readyToReview ?: 0,
            privateCaptureFiles = captureFiles.first,
            privateCaptureBytes = captureFiles.second,
            referenceMediaFiles = media.fileCount,
            referenceMediaBytes = media.totalBytes,
            lastObservationSyncAtMs = account?.let { observationStore.summary(it.userId).lastSyncedAtMs },
        )
        observationStore.close()
        return inventory
    }

    fun clearAllLocalData() {
        ObservationSyncScheduler.cancel(appContext)
        check(AccountStore(appContext).unlink()) { "Account preferences could not be cleared." }
        check(MarkerStore(appContext).clear()) { "Handoff markers could not be cleared." }
        check(ProgressionStore(appContext).clear()) {
            "Progression preferences could not be cleared."
        }
        ObservationStore(appContext).use(ObservationStore::clearAllLocalData)
        CatalogueStore(appContext).use(CatalogueStore::clearAllLocalData)
        check(RegionalCatalogueAssetStore(appContext).removeInstalledBasePack()) {
            "The installed regional content bundle could not be removed."
        }
        LocalMediaStore(appContext).clear()
        clearDirectory(File(appContext.filesDir, HANDOFF_DIRECTORY))
        appContext.cacheDir.listFiles().orEmpty().forEach(File::deleteRecursively)
    }

    private fun clearDirectory(directory: File) {
        check(directory.listFiles().orEmpty().all(File::deleteRecursively)) {
            "Some private capture files could not be deleted."
        }
    }

    private fun fileSummary(directory: File): Pair<Int, Long> {
        val files = directory.walkTopDown().filter(File::isFile).toList()
        return files.size to files.sumOf(File::length)
    }

    companion object {
        private const val HANDOFF_DIRECTORY = "handoffs"
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "${bytes / 1_024} KiB"
    else -> "${bytes / (1_024 * 1_024)} MiB"
}
