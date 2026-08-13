package com.wildlife.feasibility

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyHttpClientTest {
    @Test
    fun `retries rate limiting then returns the successful response`() {
        val statuses = ArrayDeque(listOf(429, 200))
        val sleeps = mutableListOf<Long>()
        val client = ReadOnlyHttpClient(
            connectionFactory = { FakeConnection(it, statuses.removeFirst()) },
            clock = { 10_000L },
            sleeper = sleeps::add,
        )

        client.getJson(URL("https://example.test/data"), policy("retry-test"))

        assertEquals(listOf(750L), sleeps)
    }

    @Test
    fun `does not retry a permanent HTTP response`() {
        var requests = 0
        val client = ReadOnlyHttpClient(
            connectionFactory = {
                requests++
                FakeConnection(it, 404)
            },
            clock = { 20_000L },
            sleeper = {},
        )

        val error = runCatching {
            client.getJson(URL("https://example.test/missing"), policy("permanent-test"))
        }.exceptionOrNull()

        assertTrue(error is RemoteDataException.HttpFailure)
        assertEquals(404, (error as RemoteDataException.HttpFailure).statusCode)
        assertEquals(1, requests)
    }

    @Test
    fun `query parameters are encoded once by the shared URL builder`() {
        val url = urlWithParameters(
            "https://example.test/search",
            linkedMapOf("q" to "Erithacus rubecula", "ids" to "1|2"),
        )

        assertEquals(
            "https://example.test/search?q=Erithacus+rubecula&ids=1%7C2",
            url.toString(),
        )
    }

    @Test
    fun `pacing is shared per service without delaying a different service`() {
        val sleeps = mutableListOf<Long>()
        val client = ReadOnlyHttpClient(
            connectionFactory = { FakeConnection(it, 200) },
            clock = { 30_000L },
            sleeper = sleeps::add,
        )
        val firstPolicy = policy("paced-a").copy(minimumIntervalMs = 1_000L)
        val otherPolicy = policy("paced-b").copy(minimumIntervalMs = 1_000L)

        client.getJson(URL("https://example.test/one"), firstPolicy)
        client.getJson(URL("https://example.test/two"), firstPolicy)
        client.getJson(URL("https://other.test/one"), otherPolicy)

        assertEquals(listOf(1_000L), sleeps)
    }

    private fun policy(key: String) = ReadOnlyRequestPolicy(
        serviceName = "Test",
        userAgent = "Wildlife-Test/1",
        rateLimitKey = key,
        minimumIntervalMs = 0,
    )

    private class FakeConnection(url: URL, private val status: Int) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream("{}".toByteArray())
    }
}
