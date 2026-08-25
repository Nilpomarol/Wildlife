package com.wildlife.feasibility

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.WorkInfo
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class MediaPrefetchJob(
    val generationId: String,
    val regionKey: String,
    val assetId: String,
    val variant: String,
    val priority: Int,
    val attemptCount: Int,
    val nextRetryAtMs: Long,
)

data class MediaPrefetchSummary(
    val queued: Int,
    val running: Int,
    val completed: Int,
    val failed: Int,
)

data class MediaPrefetchDiagnostics(
    val summary: MediaPrefetchSummary,
    val detailQueued: Int,
    val detailRunning: Int,
    val nextRetryAtMs: Long?,
    val failureCodes: Map<String, Int>,
)

internal class MediaPrefetchStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE, null, VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE media_prefetch_job (
                generation_id TEXT NOT NULL, region_key TEXT NOT NULL,
                asset_id TEXT NOT NULL, variant TEXT NOT NULL, priority INTEGER NOT NULL,
                state TEXT NOT NULL, attempt_count INTEGER NOT NULL,
                next_retry_at_ms INTEGER NOT NULL, failure_code TEXT,
                created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY(generation_id, asset_id, variant))""".trimIndent(),
        )
        db.execSQL("CREATE INDEX media_prefetch_ready ON media_prefetch_job(generation_id, state, next_retry_at_ms, priority, created_at_ms)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun reconcileGeneration(generationId: String) = writableDatabase.transaction {
        delete("media_prefetch_job", "generation_id != ?", arrayOf(generationId))
    }

    fun enqueue(
        jobs: Collection<MediaPrefetchJob>,
        nowMs: Long = System.currentTimeMillis(),
        resetFailed: Boolean = false,
    ): Boolean = writableDatabase.transactionResult {
        var shouldRun = false
        jobs.forEach { job ->
            rawQuery(
                "SELECT priority, state, region_key FROM media_prefetch_job WHERE generation_id=? AND asset_id=? AND variant=?",
                arrayOf(job.generationId, job.assetId, job.variant),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val currentPriority = cursor.getInt(0)
                    val state = cursor.getString(1)
                    val currentRegion = cursor.getString(2)
                    when {
                        state == STATE_FAILED && resetFailed -> {
                            shouldRun = true
                            update(
                                "media_prefetch_job",
                                ContentValues().apply {
                                    put("region_key", job.regionKey)
                                    put("priority", minOf(currentPriority, job.priority))
                                    put("state", STATE_QUEUED)
                                    put("attempt_count", 0)
                                    put("next_retry_at_ms", 0)
                                    putNull("failure_code")
                                    put("updated_at_ms", nowMs)
                                },
                                "generation_id=? AND asset_id=? AND variant=?",
                                arrayOf(job.generationId, job.assetId, job.variant),
                            )
                        }
                        state == STATE_QUEUED || state == STATE_RUNNING -> {
                            shouldRun = true
                            val improvedPriority = minOf(currentPriority, job.priority)
                            if (currentRegion != job.regionKey || improvedPriority != currentPriority) {
                                update(
                                    "media_prefetch_job",
                                    ContentValues().apply {
                                        put("region_key", job.regionKey)
                                        put("priority", improvedPriority)
                                        put("updated_at_ms", nowMs)
                                    },
                                    "generation_id=? AND asset_id=? AND variant=?",
                                    arrayOf(job.generationId, job.assetId, job.variant),
                                )
                            }
                        }
                    }
                } else {
                    insertOrThrow("media_prefetch_job", null, job.values(nowMs))
                    shouldRun = true
                }
            }
        }
        shouldRun
    }

    fun activateRegion(generationId: String, regionKey: String) {
        writableDatabase.update(
            "media_prefetch_job",
            ContentValues().apply { put("priority", PRIORITY_INACTIVE_REGION) },
            "generation_id=? AND region_key!=? AND state=?",
            arrayOf(generationId, regionKey, STATE_QUEUED),
        )
    }

    /** Claims one item transactionally; failed prefixes cannot block later ready rows. */
    fun claimNext(generationId: String, nowMs: Long = System.currentTimeMillis()): MediaPrefetchJob? = writableDatabase.transactionResult {
        recoverExpiredClaims(this, nowMs)
        rawQuery(
            """SELECT generation_id,region_key,asset_id,variant,priority,attempt_count,next_retry_at_ms
                FROM media_prefetch_job WHERE generation_id=? AND state=? AND next_retry_at_ms<=?
                ORDER BY priority, next_retry_at_ms, created_at_ms LIMIT 1""".trimIndent(),
            arrayOf(generationId, STATE_QUEUED, nowMs.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@transactionResult null
            val job = MediaPrefetchJob(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5), cursor.getLong(6))
            update("media_prefetch_job", ContentValues().apply { put("state", STATE_RUNNING); put("updated_at_ms", nowMs) }, "generation_id=? AND asset_id=? AND variant=? AND state=?", arrayOf(job.generationId, job.assetId, job.variant, STATE_QUEUED))
            job
        }
    }

    fun complete(job: MediaPrefetchJob, nowMs: Long = System.currentTimeMillis()) = updateJob(job, STATE_COMPLETE, job.attemptCount, 0, null, nowMs)

    fun release(job: MediaPrefetchJob, nowMs: Long = System.currentTimeMillis()) {
        writableDatabase.update(
            "media_prefetch_job",
            ContentValues().apply {
                put("state", STATE_QUEUED)
                put("next_retry_at_ms", nowMs)
                put("updated_at_ms", nowMs)
            },
            "generation_id=? AND asset_id=? AND variant=? AND state=?",
            arrayOf(job.generationId, job.assetId, job.variant, STATE_RUNNING),
        )
    }

    fun fail(job: MediaPrefetchJob, failureCode: String, retryable: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val attempts = job.attemptCount + 1
        val retry = retryable && attempts < MAX_ATTEMPTS
        val delay = (BASE_RETRY_MS * (1L shl (attempts - 1))).coerceAtMost(MAX_RETRY_MS)
        updateJob(job, if (retry) STATE_QUEUED else STATE_FAILED, attempts, if (retry) nowMs + delay else Long.MAX_VALUE, failureCode, nowMs)
    }

    /** Exact next time at which queued work or an abandoned running lease can be claimed. */
    fun nextDueAtMs(generationId: String, nowMs: Long = System.currentTimeMillis()): Long? = writableDatabase.transactionResult {
        recoverExpiredClaims(this, nowMs)
        rawQuery(
            """SELECT MIN(CASE WHEN state=? THEN next_retry_at_ms ELSE updated_at_ms+? END)
                FROM media_prefetch_job WHERE generation_id=? AND state IN (?,?)""".trimIndent(),
            arrayOf(STATE_QUEUED, RUNNING_LEASE_MS.toString(), generationId, STATE_QUEUED, STATE_RUNNING),
        ).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }
    }

    fun summary(generationId: String, regionKey: String? = null): MediaPrefetchSummary {
        val counts = mutableMapOf<String, Int>()
        val regionClause = if (regionKey == null) "" else " AND region_key=?"
        val arguments = if (regionKey == null) arrayOf(generationId) else arrayOf(generationId, regionKey)
        readableDatabase.rawQuery("SELECT state,COUNT(*) FROM media_prefetch_job WHERE generation_id=?$regionClause GROUP BY state", arguments).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
        }
        return MediaPrefetchSummary(counts[STATE_QUEUED] ?: 0, counts[STATE_RUNNING] ?: 0, counts[STATE_COMPLETE] ?: 0, counts[STATE_FAILED] ?: 0)
    }

    /** Privacy-safe queue state: no species labels, URLs, provider IDs or local paths. */
    fun diagnostics(generationId: String, nowMs: Long = System.currentTimeMillis()): MediaPrefetchDiagnostics {
        val summary = summary(generationId)
        var detailQueued = 0
        var detailRunning = 0
        var nextRetryAtMs: Long? = null
        val failureCodes = linkedMapOf<String, Int>()
        readableDatabase.rawQuery(
            "SELECT state,priority,next_retry_at_ms,failure_code,COUNT(*) FROM media_prefetch_job WHERE generation_id=? GROUP BY state,priority,next_retry_at_ms,failure_code",
            arrayOf(generationId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val state = cursor.getString(0)
                val priority = cursor.getInt(1)
                val retryAt = cursor.getLong(2)
                val failure = cursor.getString(3)
                val count = cursor.getInt(4)
                if (priority == PRIORITY_DETAIL && state == STATE_QUEUED) detailQueued += count
                if (priority == PRIORITY_DETAIL && state == STATE_RUNNING) detailRunning += count
                if (state == STATE_QUEUED && retryAt > nowMs) {
                    nextRetryAtMs = minOf(nextRetryAtMs ?: retryAt, retryAt)
                }
                if (!failure.isNullOrBlank()) {
                    failureCodes[failure] = (failureCodes[failure] ?: 0) + count
                }
            }
        }
        return MediaPrefetchDiagnostics(
            summary, detailQueued, detailRunning, nextRetryAtMs, failureCodes.toSortedMap(),
        )
    }

    fun clear() { writableDatabase.delete("media_prefetch_job", null, null) }

    private fun recoverExpiredClaims(db: SQLiteDatabase, nowMs: Long) {
        db.update(
            "media_prefetch_job",
            ContentValues().apply {
                put("state", STATE_QUEUED)
                put("next_retry_at_ms", nowMs)
                put("updated_at_ms", nowMs)
            },
            "state=? AND updated_at_ms<=?",
            arrayOf(STATE_RUNNING, (nowMs - RUNNING_LEASE_MS).toString()),
        )
    }
    private fun updateJob(job: MediaPrefetchJob, state: String, attempts: Int, next: Long, failure: String?, now: Long) {
        writableDatabase.update("media_prefetch_job", ContentValues().apply { put("state", state); put("attempt_count", attempts); put("next_retry_at_ms", next); put("failure_code", failure); put("updated_at_ms", now) }, "generation_id=? AND asset_id=? AND variant=?", arrayOf(job.generationId, job.assetId, job.variant))
    }

    private fun MediaPrefetchJob.values(now: Long) = ContentValues().apply {
        put("generation_id", generationId); put("region_key", regionKey); put("asset_id", assetId); put("variant", variant); put("priority", priority)
        put("state", STATE_QUEUED); put("attempt_count", 0); put("next_retry_at_ms", 0); putNull("failure_code"); put("created_at_ms", now); put("updated_at_ms", now)
    }

    companion object {
        const val PRIORITY_PINNED = 10
        const val PRIORITY_ACHIEVEMENT = 20
        const val PRIORITY_OBSERVED = 30
        const val PRIORITY_VISIBLE = 40
        const val PRIORITY_REGION = 50
        // The open hero always outranks catalogue-wide preparation and achievement thumbnails.
        const val PRIORITY_DETAIL = 0
        const val PRIORITY_INACTIVE_REGION = 90
        private const val DATABASE = "media-prefetch.db"
        private const val VERSION = 1
        private const val STATE_QUEUED = "queued"
        private const val STATE_RUNNING = "running"
        private const val STATE_COMPLETE = "complete"
        private const val STATE_FAILED = "failed"
        private const val MAX_ATTEMPTS = 5
        private const val BASE_RETRY_MS = 30_000L
        private const val MAX_RETRY_MS = 6L * 60L * 60L * 1_000L
        private const val RUNNING_LEASE_MS = 15L * 60L * 1_000L
    }
}

class MediaPrefetchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val generationId = inputData.getString(KEY_GENERATION) ?: return@withContext Result.failure()
        val lane = inputData.getString(KEY_LANE) ?: LANE_IMMEDIATE
        val content = PublishedContentRepositories.application(applicationContext)
        if (content.generation().generationId != generationId) return@withContext Result.success()
        if (lane == LANE_RETRY) {
            MediaPrefetchScheduler.scheduleFollowUp(applicationContext, generationId, System.currentTimeMillis(), lane)
            return@withContext Result.success()
        }
        val media = LocalMediaStore(applicationContext)
        MediaPrefetchStore(applicationContext).use { store ->
            var activeJob: MediaPrefetchJob? = null
            try {
                var processed = 0
                while (processed < MAX_ITEMS_PER_RUN) {
                    val job = store.claimNext(generationId) ?: break
                    activeJob = job
                    val asset = content.mediaAsset(job.assetId)
                    if (asset == null) {
                        store.fail(job, "asset_removed", retryable = false)
                    } else {
                        val result = media.downloadNow(
                            generationId,
                            asset,
                            job.variant,
                            pinned = job.priority <= MediaPrefetchStore.PRIORITY_ACHIEVEMENT,
                        )
                        if (result.localUri != null) store.complete(job)
                        else store.fail(job, result.failureCode ?: "unknown", isRetryable(result.failureCode))
                        activeJob = null
                        if (result.localUri != null && !result.fromCache) delay(PROVIDER_PACING_MS)
                    }
                    activeJob = null
                    processed++
                }
            } catch (cancelled: CancellationException) {
                activeJob?.let(store::release)
                throw cancelled
            }
            MediaPrefetchScheduler.scheduleFollowUp(
                applicationContext,
                generationId,
                store.nextDueAtMs(generationId),
                lane,
            )
        }
        Result.success()
    }

    private fun isRetryable(code: String?) = code == "network_failure" || code == "storage_full" || code?.let { it == "http_408" || it == "http_429" || it.startsWith("http_5") } == true

    companion object {
        const val KEY_GENERATION = "generation_id"
        const val KEY_LANE = "lane"
        const val LANE_IMMEDIATE = "immediate"
        const val LANE_RETRY = "retry"
        private const val MAX_ITEMS_PER_RUN = 24
        private const val PROVIDER_PACING_MS = 250L
    }
}

object MediaPrefetchScheduler {
    private const val TAG = "wildlife-media-prefetch"

    /** Background coverage belongs only to the location-derived current region. */
    fun scheduleCurrentRegion(context: Context) {
        val content = PublishedContentRepositories.application(context)
        val generation = content.generation().generationId
        val catalogues = content.catalogues()
        MediaPrefetchStore(context).use { it.reconcileGeneration(generation) }
        val region = RegionContextStore(context).currentRegionKey(
            catalogues.mapTo(mutableSetOf()) { it.regionKey },
        ) ?: run {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
            return
        }
        val achievementIds = content.achievements(region).flatMapTo(mutableSetOf()) { it.taxonIds }
        val observedIds = observedTaxonIds(context, region)
        val jobs = content.mediaByTaxon(region).flatMap { (taxonId, assets) ->
            val priority = when (taxonId) {
                in achievementIds -> MediaPrefetchStore.PRIORITY_ACHIEVEMENT
                in observedIds -> MediaPrefetchStore.PRIORITY_OBSERVED
                else -> MediaPrefetchStore.PRIORITY_REGION
            }
            assets.mapNotNull { asset -> asset.variant("thumbnail")?.let { MediaPrefetchJob(generation, region, asset.assetId, "thumbnail", priority, 0, 0) } }
        }
        val shouldStart = MediaPrefetchStore(context).use { store ->
            store.activateRegion(generation, region)
            store.enqueue(jobs)
        }
        if (shouldStart) startImmediateWorker(context, generation)
    }

    fun enqueueDetail(context: Context, regionKey: String, assetId: String) {
        val content = PublishedContentRepositories.application(context)
        val generation = content.generation().generationId
        val shouldStart = MediaPrefetchStore(context).use { store ->
            store.reconcileGeneration(generation)
            store.enqueue(
                listOf(MediaPrefetchJob(generation, regionKey, assetId, "detail", MediaPrefetchStore.PRIORITY_DETAIL, 0, 0)),
                resetFailed = true,
            )
        }
        if (shouldStart) startImmediateWorker(context, generation)
    }

    /** A browsed region gets only bounded, visible-row preparation; it never becomes active. */
    fun prioritizeBrowsedVisible(context: Context, regionKey: String, taxonIds: Set<Long>) {
        if (taxonIds.isEmpty()) return
        val content = PublishedContentRepositories.application(context)
        val generation = content.generation().generationId
        val jobs = content.mediaByTaxon(regionKey)
            .filterKeys { it in taxonIds }
            .values.flatten()
            .mapNotNull { asset ->
                asset.variant("thumbnail")?.let {
                    MediaPrefetchJob(generation, regionKey, asset.assetId, "thumbnail", MediaPrefetchStore.PRIORITY_VISIBLE, 0, 0)
                }
            }
        val shouldStart = MediaPrefetchStore(context).use { it.enqueue(jobs) }
        if (shouldStart) startImmediateWorker(context, generation)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)

    fun workInfos(context: Context): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context.applicationContext).getWorkInfosByTagLiveData(TAG)

    internal fun scheduleFollowUp(context: Context, generation: String, dueAtMs: Long?, currentLane: String) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (dueAtMs == null) {
            if (currentLane != MediaPrefetchWorker.LANE_RETRY) manager.cancelUniqueWork(retryName(generation))
            return
        }
        val delayMs = (dueAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        if (delayMs == 0L) {
            if (currentLane == MediaPrefetchWorker.LANE_IMMEDIATE) appendImmediateWorker(context, generation)
            else startImmediateWorker(context, generation)
        } else {
            manager.enqueueUniqueWork(
                retryName(generation),
                ExistingWorkPolicy.REPLACE,
                request(generation, MediaPrefetchWorker.LANE_RETRY, delayMs),
            )
        }
    }

    private fun startImmediateWorker(context: Context, generation: String) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            immediateName(generation),
            ExistingWorkPolicy.KEEP,
            request(generation, MediaPrefetchWorker.LANE_IMMEDIATE, 0),
        )
    }

    private fun appendImmediateWorker(context: Context, generation: String) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            immediateName(generation),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(generation, MediaPrefetchWorker.LANE_IMMEDIATE, 0),
        )
    }

    private fun request(generation: String, lane: String, delayMs: Long) = OneTimeWorkRequestBuilder<MediaPrefetchWorker>()
        .setInputData(
            Data.Builder()
                .putString(MediaPrefetchWorker.KEY_GENERATION, generation)
                .putString(MediaPrefetchWorker.KEY_LANE, lane)
                .build(),
        )
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .addTag(TAG)
        .build()

    private fun immediateName(generation: String) = "$TAG:immediate:$generation"
    private fun retryName(generation: String) = "$TAG:retry:$generation"

    private fun observedTaxonIds(context: Context, region: String): Set<Long> {
        val account = AccountStore(context).verified() ?: return emptySet()
        return ObservationStore(context).use { store ->
            store.observations(account.userId).mapNotNull { observation ->
                val assigned = store.observationRegion(account.userId, observation.uuid)
                (observation.collectionTaxonId ?: observation.taxonId).takeIf { assigned?.earnsRegionalProgress == true && assigned.regionKey == region }
            }.toSet()
        }
    }
}

private inline fun <T> SQLiteDatabase.transactionResult(block: SQLiteDatabase.() -> T): T {
    beginTransaction(); return try { val result = block(); setTransactionSuccessful(); result } finally { endTransaction() }
}
private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) = transactionResult { block(); Unit }
