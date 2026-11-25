package com.homelab.app.notifications

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
data class NtfyEvent(
    val event: String,
    val id: String = "",
    val title: String = "",
    val message: String = ""
)

class NtfySubscriptionRejected : IOException()

internal fun ntfyStreamUrl(topic: HttpUrl, cursor: String?): HttpUrl =
    topic.newBuilder().addPathSegment("json")
        .apply { if (cursor != null) addQueryParameter("since", cursor) }
        .build()

@Singleton
class NtfyClient @Inject constructor(private val json: Json) {
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun subscribe(topic: HttpUrl, token: String, cursor: String?): Flow<NtfyEvent> = callbackFlow {
        val request = Request.Builder().url(ntfyStreamUrl(topic, cursor))
            .header("Accept", "application/x-ndjson")
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .build()
        val call = streamClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (it.code == 401 || it.code == 403 || it.code == 404 || it.isRedirect ||
                            it.header("Content-Type")?.startsWith("text/html", ignoreCase = true) == true
                        ) {
                            throw NtfySubscriptionRejected()
                        }
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val source = it.body?.source() ?: throw IOException("Empty event stream")
                        while (!source.exhausted()) {
                            val line = source.readUtf8LineStrict(64 * 1024L)
                            val event = try {
                                json.decodeFromString<NtfyEvent>(line)
                            } catch (_: SerializationException) {
                                continue
                            }
                            if (trySendBlocking(event).isFailure) break
                        }
                    }
                    close()
                } catch (error: Exception) {
                    close(error)
                }
            }
        })
        awaitClose { call.cancel() }
    }
}
