package com.wildlife.feasibility

import android.content.ContentResolver
import android.location.Location
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PhotoMetadata(
    val capturedAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAtReliable: Boolean,
    val locationReliable: Boolean,
)

object PhotoMetadataReader {
    private val exifDateFormats = listOf(
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
    )

    fun read(contentResolver: ContentResolver, uri: Uri, fallbackTimeMs: Long): PhotoMetadata {
        val exif = contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor)
        } ?: return PhotoMetadata(fallbackTimeMs, null, null, false, false)
        return fromExif(exif, fallbackTimeMs)
    }

    fun writeCaptureMetadata(file: File, capturedAtMs: Long, location: Location?) {
        val exif = ExifInterface(file)
        val formatter = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formatter.format(Date(capturedAtMs)))
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formatter.format(Date(capturedAtMs)))
        location?.let(exif::setGpsInfo)
        exif.saveAttributes()
    }

    private fun fromExif(exif: ExifInterface, fallbackTimeMs: Long): PhotoMetadata {
        val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val parsedCapturedAt = rawDate?.let { value ->
            exifDateFormats.firstNotNullOfOrNull { format ->
                synchronized(format) { runCatching { format.parse(value)?.time }.getOrNull() }
            }
        }
        val latLong = exif.latLong
        return PhotoMetadata(
            capturedAtMs = parsedCapturedAt ?: fallbackTimeMs,
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1),
            capturedAtReliable = parsedCapturedAt != null,
            locationReliable = latLong?.size == 2,
        )
    }
}
