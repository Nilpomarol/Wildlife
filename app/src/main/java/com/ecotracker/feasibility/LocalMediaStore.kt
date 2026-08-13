package com.wildlife.feasibility

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Stores reusable reference media in Wildlife-owned private storage.
 *
 * These files are deliberately outside Android's cache directory, so normal cache eviction does
 * not remove catalogue silhouettes or reference thumbnails. Remote URLs remain in SQLite for
 * attribution and repair; this class only owns the durable display copy.
 */
class LocalMediaStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }

    init {
        cleanupStaleTemporaryFiles()
    }

    fun summary(): StoredMediaSummary {
        val files = root.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.toList()
        return StoredMediaSummary(
            fileCount = files.size,
            totalBytes = files.sumOf(File::length),
            capacityBytes = MAX_TOTAL_BYTES,
        )
    }

    fun clear() = synchronized(STORAGE_LOCK) {
        check(root.listFiles().orEmpty().all(File::deleteRecursively)) {
            "Some reusable media could not be deleted."
        }
    }

    fun isStored(localUri: String?): Boolean {
        val path = localUri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path ?: return false
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val ownedRoot = runCatching { root.canonicalFile }.getOrNull() ?: return false
        return file.isFile && file.length() > 0L && file.toPath().startsWith(ownedRoot.toPath())
    }

    fun storeSilhouette(asset: SilhouetteAsset): SilhouetteAsset = asset.copy(
        localUri = store(
            remoteUrl = asset.url,
            category = "silhouettes",
            stableKey = "${asset.matchRank}:${asset.taxonName}:${asset.url}",
            maxBytes = MAX_SILHOUETTE_BYTES,
        ),
    )

    fun storePhoto(remoteUrl: String, taxonId: Long): String? = store(
        remoteUrl = remoteUrl,
        category = "reference_photos",
        stableKey = "$taxonId:$remoteUrl",
        maxBytes = MAX_PHOTO_BYTES,
    )

    private fun store(
        remoteUrl: String,
        category: String,
        stableKey: String,
        maxBytes: Long,
    ): String? = synchronized(STORAGE_LOCK) {
        storeLocked(remoteUrl, category, stableKey, maxBytes)
    }

    private fun storeLocked(
        remoteUrl: String,
        category: String,
        stableKey: String,
        maxBytes: Long,
    ): String? {
        val directory = File(root, category).apply { mkdirs() }
        val target = File(directory, "${sha256(stableKey)}${extension(remoteUrl)}")
        if (target.isFile && validStoredAsset(target.length(), maxBytes)) {
            return Uri.fromFile(target).toString()
        }
        if (target.exists() && !target.delete()) return null
        val permittedBytes = permittedDownloadBytes(
            storedBytes = summary().totalBytes,
            assetLimitBytes = maxBytes,
            totalCapacityBytes = MAX_TOTAL_BYTES,
        )
        if (permittedBytes <= 0L) return null

        val temporary = File(
            directory,
            ".${target.name}.${Thread.currentThread().id}.${System.nanoTime()}.part",
        )
        return try {
            val connection = URL(remoteUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                WildlifeNetworkIdentity.REFERENCE_MEDIA_USER_AGENT,
            )
            try {
                val code = connection.responseCode
                if (code !in 200..299) return null
                if (!connection.contentType.orEmpty().lowercase().startsWith("image/")) return null
                val declaredLength = connection.contentLengthLong
                if (declaredLength > permittedBytes) return null
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > permittedBytes) return null
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (temporary.length() <= 0L) return null
                if (!temporary.renameTo(target) &&
                    !(target.isFile && validStoredAsset(target.length(), maxBytes))
                ) {
                    return null
                }
                Uri.fromFile(target).toString()
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun extension(url: String): String {
        val path = runCatching { URL(url).path.lowercase() }.getOrDefault("")
        return when {
            path.endsWith(".png") -> ".png"
            path.endsWith(".webp") -> ".webp"
            else -> ".jpg"
        }
    }

    private fun cleanupStaleTemporaryFiles(nowMs: Long = System.currentTimeMillis()) {
        root.walkTopDown()
            .filter { file ->
                file.isFile && file.name.endsWith(".part") &&
                    nowMs - file.lastModified() > STALE_TEMPORARY_FILE_MS
            }
            .forEach { it.delete() }
    }

    companion object {
        private const val DIRECTORY = "reference_media"
        private const val MAX_SILHOUETTE_BYTES = 4L * 1024L * 1024L
        private const val MAX_PHOTO_BYTES = 12L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 384L * 1024L * 1024L
        private const val STALE_TEMPORARY_FILE_MS = 24L * 60L * 60L * 1_000L
        private val STORAGE_LOCK = Any()
    }
}

data class StoredMediaSummary(
    val fileCount: Int,
    val totalBytes: Long,
    val capacityBytes: Long,
)

internal fun permittedDownloadBytes(
    storedBytes: Long,
    assetLimitBytes: Long,
    totalCapacityBytes: Long,
): Long = minOf(assetLimitBytes, (totalCapacityBytes - storedBytes).coerceAtLeast(0L))

internal fun validStoredAsset(length: Long, assetLimitBytes: Long): Boolean =
    length in 1..assetLimitBytes
