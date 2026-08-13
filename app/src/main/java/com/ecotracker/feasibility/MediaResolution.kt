package com.wildlife.feasibility

internal data class MediaResolution<T>(
    val asset: T?,
    val failures: List<MediaLookupFailure> = emptyList(),
) {
    val hadFailure: Boolean get() = failures.isNotEmpty()
    val hadTemporaryFailure: Boolean get() = failures.any(MediaLookupFailure::retryable)
}

internal data class MediaLookupFailure(val retryable: Boolean)

internal fun <Input, Asset> resolveMedia(
    input: Input,
    resolver: (Input) -> Asset?,
): MediaResolution<Asset> = try {
    MediaResolution(resolver(input))
} catch (error: Exception) {
    MediaResolution(null, listOf(error.toMediaLookupFailure()))
}

internal fun <Input, Asset> resolveFirstMedia(
    candidates: List<Input>,
    resolver: (Input) -> Asset?,
): MediaResolution<Asset> {
    val failures = mutableListOf<MediaLookupFailure>()
    candidates.forEach { candidate ->
        val resolution = resolveMedia(candidate, resolver)
        failures += resolution.failures
        if (resolution.asset != null) return MediaResolution(resolution.asset, failures)
    }
    return MediaResolution(null, failures)
}

private fun Exception.toMediaLookupFailure(): MediaLookupFailure = MediaLookupFailure(
    retryable = when (this) {
        is RemoteDataException.HttpFailure -> retryable
        else -> true
    },
)
