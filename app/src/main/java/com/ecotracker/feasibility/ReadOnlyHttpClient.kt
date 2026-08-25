package com.wildlife.feasibility

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ReadOnlyHttpClient internal constructor(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) {
    internal fun getJson(url: URL, policy: ReadOnlyRequestPolicy): JSONObject {
        var lastNetworkError: IOException? = null
        repeat(policy.maxAttempts) { attempt ->
            RequestGate.reserve(policy.rateLimitKey, policy.minimumIntervalMs, clock, sleeper)
            var connection: HttpURLConnection? = null
            try {
                connection = connectionFactory(url).apply {
                    requestMethod = "GET"
                    connectTimeout = policy.connectTimeoutMs
                    readTimeout = policy.readTimeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", policy.userAgent)
                    policy.headers.forEach(::setRequestProperty)
                }
                val status = connection.responseCode
                if (status in 200..299) {
                    val declaredBytes = connection.contentLengthLong
                    if (declaredBytes > policy.maxResponseBytes) {
                        throw RemoteDataException.ResponseTooLarge(
                            policy.serviceName,
                            policy.maxResponseBytes,
                        )
                    }
                    val body = connection.inputStream.use { input ->
                        input.readUtf8Bounded(policy.serviceName, policy.maxResponseBytes)
                    }
                    return try {
                        JSONObject(body)
                    } catch (error: Exception) {
                        throw RemoteDataException.InvalidResponse(policy.serviceName, error)
                    }
                }
                val retryable = status in policy.retryableStatusCodes
                if (!retryable || attempt == policy.maxAttempts - 1) {
                    throw RemoteDataException.HttpFailure(
                        service = policy.serviceName,
                        statusCode = status,
                        retryable = retryable,
                    )
                }
                sleeper(retryDelayMs(connection, attempt, policy.maxRetryDelayMs))
            } catch (error: RemoteDataException) {
                throw error
            } catch (error: IOException) {
                lastNetworkError = error
                if (attempt == policy.maxAttempts - 1) {
                    throw RemoteDataException.NetworkFailure(policy.serviceName, error)
                }
                sleeper(minOf((attempt + 1) * 750L, policy.maxRetryDelayMs))
            } finally {
                connection?.disconnect()
            }
        }
        throw RemoteDataException.NetworkFailure(
            policy.serviceName,
            lastNetworkError ?: IOException("Request failed without a response"),
        )
    }

    private fun retryDelayMs(
        connection: HttpURLConnection,
        attempt: Int,
        maxDelayMs: Long,
    ): Long = connection.getHeaderField("Retry-After")?.toLongOrNull()
        ?.times(1_000L)?.coerceAtMost(maxDelayMs)
        ?: minOf((attempt + 1) * 750L, maxDelayMs)
}

internal data class ReadOnlyRequestPolicy(
    val serviceName: String,
    val userAgent: String,
    val rateLimitKey: String,
    val minimumIntervalMs: Long,
    val connectTimeoutMs: Int = 10_000,
    val readTimeoutMs: Int = 20_000,
    val maxAttempts: Int = 3,
    val maxRetryDelayMs: Long = 5_000L,
    val maxResponseBytes: Long = 8L * 1024L * 1024L,
    val retryableStatusCodes: Set<Int> = setOf(429, 500, 502, 503, 504),
    val headers: Map<String, String> = emptyMap(),
) {
    init {
        require(maxResponseBytes > 0L) { "Response limit must be positive." }
    }
}

sealed class RemoteDataException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class HttpFailure(
        val service: String,
        val statusCode: Int,
        val retryable: Boolean,
    ) : RemoteDataException("$service returned HTTP $statusCode")

    class NetworkFailure(
        val service: String,
        cause: IOException,
    ) : RemoteDataException("$service could not be reached", cause)

    class InvalidResponse(
        val service: String,
        cause: Throwable,
    ) : RemoteDataException("$service returned an invalid response", cause)

    class ResponseTooLarge(
        val service: String,
        val maximumBytes: Long,
    ) : RemoteDataException("$service response exceeded $maximumBytes bytes")
}

private fun InputStream.readUtf8Bounded(service: String, maximumBytes: Long): String {
    val initialCapacity = minOf(maximumBytes, DEFAULT_BUFFER_SIZE.toLong()).toInt()
    val output = ByteArrayOutputStream(initialCapacity)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maximumBytes) {
            throw RemoteDataException.ResponseTooLarge(service, maximumBytes)
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal fun urlWithParameters(endpoint: String, parameters: Map<String, String>): URL {
    if (parameters.isEmpty()) return URL(endpoint)
    val query = parameters.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, Charsets.UTF_8.name())}=" +
            URLEncoder.encode(value, Charsets.UTF_8.name())
    }
    return URL("$endpoint?$query")
}

private object RequestGate {
    private val nextRequestAtMs = mutableMapOf<String, Long>()

    fun reserve(
        key: String,
        minimumIntervalMs: Long,
        clock: () -> Long,
        sleeper: (Long) -> Unit,
    ) {
        val now = clock()
        val waitMs = synchronized(this) {
            val reservedAt = maxOf(now, nextRequestAtMs[key] ?: now)
            nextRequestAtMs[key] = reservedAt + minimumIntervalMs
            reservedAt - now
        }
        if (waitMs > 0L) sleeper(waitMs)
    }
}
