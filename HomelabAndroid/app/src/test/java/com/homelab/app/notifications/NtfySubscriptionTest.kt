package com.homelab.app.notifications

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfySubscriptionTest {
    @Test
    fun `stream keeps proxy prefix and requests messages since the saved ID`() {
        val topic = checkNotNull(parseDockerTopic(" https://notify.example.test/proxy/docker-events/ "))
        val request = ntfyStreamUrl(topic, "lastMessageId")
        assertEquals("/proxy/docker-events/json", request.encodedPath)
        assertEquals("lastMessageId", request.queryParameter("since"))
    }

    @Test
    fun `topic disallows cleartext embedded credentials and extra URL components`() {
        listOf(
            "http://notify.example.test/docker",
            "https://user:pass@notify.example.test/docker",
            "https://notify.example.test/docker?token=secret",
            "https://notify.example.test/docker#fragment",
            "https://notify.example.test/",
            "https://notify.example.test/one,two"
        ).forEach { assertNull(it, parseDockerTopic(it)) }
    }

    @Test
    fun `decodes incoming ntfy message and heartbeat envelopes`() {
        val json = Json { ignoreUnknownKeys = true }
        val message = json.decodeFromString<NtfyEvent>(
            """{"id":"hXEeMfKsvs","time":1635528757,"event":"message","topic":"docker",
                "title":"Docker","message":"die plex"}"""
        )
        assertEquals(NtfyEvent("message", "hXEeMfKsvs", "Docker", "die plex"), message)
        assertEquals("keepalive", json.decodeFromString<NtfyEvent>(
            """{"id":"hb","event":"keepalive","topic":"docker"}"""
        ).event)
    }
}
