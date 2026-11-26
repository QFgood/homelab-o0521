package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.MediaryScoutApi
import com.homelab.app.data.remote.api.MediaryScoutAcquireRequest
import com.homelab.app.data.security.ServiceUrlNormalizer
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MediaryScoutRepositoryTest {
    private val instance = ServiceInstance(
        id = "test-instance",
        type = ServiceType.MEDIARY_SCOUT,
        label = "Scout",
        url = "https://scout.example.test/mediary",
        apiKey = "test-token"
    )

    @Test
    fun `dashboard decodes library progress, running tasks and notifications`() = runTest {
        val requests = ConcurrentLinkedQueue<Request>()
        val repository = repository { request ->
            requests.add(request)
            assertEquals("GET", request.method)
            assertEquals(instance.id, request.header("X-Homelab-Instance-Id"))
            when (request.url.encodedPath) {
                "/api/agent/library" -> 200 to LIBRARY_RESPONSE
                "/api/agent/activity" -> {
                    assertEquals("20", request.url.queryParameter("limit"))
                    200 to ACTIVITY_RESPONSE
                }
                else -> error("Unexpected request URL")
            }
        }

        val dashboard = repository.getDashboard(instance.id)

        assertEquals(2, requests.size)
        assertEquals(3, dashboard.entries.single().obtainedCount)
        assertEquals(listOf("S02E04"), dashboard.entries.single().missingAired)
        assertEquals("running", dashboard.activity.active.single().status)
        assertEquals("找到新资源", dashboard.activity.recent.single().title)
    }

    @Test
    fun `authentication uses bearer token and preserves the deployment prefix`() = runTest {
        val repository = repository { request ->
            assertEquals("scout.example.test", request.url.host)
            assertEquals("/mediary/api/agent/library", request.url.encodedPath)
            assertEquals("Bearer test-token", request.header("Authorization"))
            200 to """{"count":0,"items":[]}"""
        }

        repository.authenticate(instance)
    }

    @Test
    fun `disabled agent API and invalid tokens reject connection`() {
        val disabled = repository { 404 to """{"error":"not found"}""" }
        val invalid = repository { 401 to """{"error":"unauthorized"}""" }

        assertThrows(MediaryScoutApiUnavailable::class.java) {
            runTest { disabled.authenticate(instance) }
        }
        val unauthorized = assertThrows(HttpException::class.java) {
            runTest { invalid.authenticate(instance) }
        }
        assertEquals(401, unauthorized.code())
    }

    @Test
    fun `normalizes API URLs without dropping a custom reverse proxy prefix`() {
        assertEquals(
            instance.url,
            ServiceUrlNormalizer.normalizeUrl(
                "${instance.url}/api/agent/activity?limit=5",
                ServiceType.MEDIARY_SCOUT
            )
        )
    }

    @Test
    fun `acquisition returns ambiguous candidates and submits an explicit selection`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val repository = repository { request ->
            assertEquals("/api/agent/acquire", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals(instance.id, request.header("X-Homelab-Instance-Id"))
            val buffer = Buffer()
            requireNotNull(request.body).writeTo(buffer)
            val input = json.decodeFromString<MediaryScoutAcquireRequest>(buffer.readUtf8())
            assertEquals(2, input.season)
            if (input.tmdbId == null) {
                409 to """{"status":"ambiguous","message":"Choose","candidates":[
                    {"tmdbId":12,"title":"Example","year":2026,"type":"tv","score":150}]}"""
            } else {
                assertEquals(12L, input.tmdbId)
                assertEquals("tv", input.type)
                200 to """{"status":"requested","message":"Queued","workflowRunId":"run"}"""
            }
        }
        val input = MediaryScoutAcquireRequest(query = "Example", season = 2)
        val candidates = repository.acquire(instance.id, input).candidates
        val selection = candidates.single()
        assertEquals("Example", selection.title)
        assertEquals("requested", repository.acquire(
            instance.id, input.copy(tmdbId = selection.tmdbId, type = selection.type)
        ).status)
    }

    @Test
    fun `acquisition distinguishes an empty search from a disabled endpoint`() = runTest {
        val empty = repository { 404 to """{"status":"not_found","message":"Missing"}""" }
        val disabled = repository { 404 to """{"error":"not found"}""" }
        val request = MediaryScoutAcquireRequest(query = "Missing")
        assertEquals("not_found", empty.acquire(instance.id, request).status)
        val failure = runCatching { disabled.acquire(instance.id, request) }.exceptionOrNull()
        assertEquals(MediaryScoutApiUnavailable::class.java, failure?.javaClass)
    }

    private fun repository(respond: (Request) -> Pair<Int, String>): MediaryScoutRepository {
        val jsonType = "application/json".toMediaType()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body) = respond(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Test response")
                .body(body.toResponseBody(jsonType))
                .build()
        }.build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl("https://placeholder.local/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(jsonType))
            .build()
        val tlsClientSelector = mockk<TlsClientSelector>()
        every { tlsClientSelector.clientForInstance(instance) } returns client

        return MediaryScoutRepository(retrofit.create(MediaryScoutApi::class.java), retrofit, tlsClientSelector, json)
    }

    private companion object {
        val LIBRARY_RESPONSE = """
            {"count":1,"items":[{"title":"Example","tmdbId":null,"source":"anilist","anilistId":123,
             "type":"anime","year":2026,"seasonNumber":2,"status":"active","totalEpisodes":12,
             "obtainedCount":3,"missingAired":["S02E04"],"connectedStorageId":null}]}
        """.trimIndent()
        val ACTIVITY_RESPONSE = """
            {"active":[{"workflowRunId":"run","title":"Example","kind":"type3_monitor","status":"running",
             "seasonNumber":2,"startedAt":"2026-11-25T01:00:00.000Z","connectedStorageId":null}],
             "recent":[{"id":"notice","kind":"resource_found","title":"找到新资源","body":"S02E03",
             "createdAt":"2026-11-25T01:01:00.000Z"}]}
        """.trimIndent()
    }
}
