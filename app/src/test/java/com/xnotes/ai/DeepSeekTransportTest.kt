package com.xnotes.ai
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
class DeepSeekTransportTest {
    private class Fake(val status: Int = 200, val body: String = "{\"results\":[]}") : HttpURLConnection(URL(DeepSeekSearch.ENDPOINT)) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun disconnect() { disconnected = true }
        override fun connect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }
    @Test fun searchUsesOfficialEndpointAndSeparateKey() {
        val fake = Fake()
        val client = AiConnection { endpoint -> assertEquals(DeepSeekSearch.ENDPOINT, endpoint); fake }
        assertTrue(client.search("search-test-key", "example", "原问题").isEmpty())
        assertEquals("Bearer search-test-key", fake.getRequestProperty("Authorization"))
        assertFalse(fake.instanceFollowRedirects)
        assertEquals("POST", fake.requestMethod)
        assertTrue(fake.sent.toString().contains("example")); assertTrue(fake.sent.toString(Charsets.UTF_8).contains("原问题"))
        assertTrue(fake.disconnected)
    }
    @Test fun httpFailureDoesNotBecomeSearchSuccess() {
        val fake = Fake(401)
        assertThrows(IllegalStateException::class.java) { AiConnection { fake }.search("key", "query") }
        assertTrue(fake.disconnected)
    }
    @Test fun cancelledRequestNeverConnects() {
        val client = AiConnection { error("must not connect") }
        client.cancel()
        assertThrows(IllegalStateException::class.java) { client.search("key", "query") }
    }
    @Test fun invalidQueryNeverConnects() {
        val client = AiConnection { error("must not connect") }
        assertThrows(IllegalArgumentException::class.java) { client.search("key", "x".repeat(129)) }
    }
    @Test fun oversizedResponseRejectedAndClosed() {
        val fake = Fake(body = "x".repeat(1024 * 1024 + 1))
        assertThrows(IllegalStateException::class.java) { AiConnection { fake }.search("key", "query") }
        assertTrue(fake.disconnected)
    }
    @Test fun fetchUsesOfficialFetchEndpointAndSearchKey() {
        val fake = Fake(body = "{\"content\":\"# Title\"}")
        val client = AiConnection { endpoint -> assertEquals(DeepSeekSearch.FETCH_ENDPOINT, endpoint); fake }
        assertEquals("# Title", client.fetch("search-test-key", "https://example.com/a"))
        assertEquals("Bearer search-test-key", fake.getRequestProperty("Authorization"))
        assertFalse(fake.instanceFollowRedirects)
        assertTrue(fake.sent.toString().contains("https:\\/\\/example.com\\/a") || fake.sent.toString().contains("https://example.com/a"))
    }
    @Test fun unusableFetchUrlNeverConnects() {
        val client = AiConnection { error("must not connect") }
        assertThrows(IllegalArgumentException::class.java) { client.fetch("key", "file:///data/data/x") }
    }
}
