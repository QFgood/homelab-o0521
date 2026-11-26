package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.MediaryScoutAcquireRequest
import com.homelab.app.data.remote.api.MediaryScoutAcquireResult
import com.homelab.app.data.remote.api.MediaryScoutActivity
import com.homelab.app.data.remote.api.MediaryScoutApi
import com.homelab.app.data.remote.api.MediaryScoutEntry
import com.homelab.app.domain.model.ServiceInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Retrofit

data class MediaryScoutDashboardData(
    val entries: List<MediaryScoutEntry>,
    val activity: MediaryScoutActivity
)

class MediaryScoutApiUnavailable(cause: HttpException) : Exception(cause)

@Singleton
class MediaryScoutRepository @Inject constructor(
    private val api: MediaryScoutApi,
    private val retrofit: Retrofit,
    private val tlsClientSelector: TlsClientSelector,
    private val json: Json
) {
    suspend fun authenticate(instance: ServiceInstance) {
        val token = requireNotNull(instance.apiKey)
        val loginApi = retrofit.newBuilder()
            .client(tlsClientSelector.clientForInstance(instance))
            .build()
            .create(MediaryScoutApi::class.java)

        var failure: Exception? = null
        for (baseUrl in listOfNotNull(instance.url, instance.fallbackUrl).distinct()) {
            try {
                checkAgentApi {
                    loginApi.getLibrary(
                        url = "${baseUrl.trimEnd('/')}/api/agent/library",
                        authorization = "Bearer $token"
                    )
                }
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (failure == null) failure = error
            }
        }
        throw checkNotNull(failure)
    }

    suspend fun getTrackedCount(instanceId: String): Int =
        checkAgentApi { api.getLibrary(instanceId = instanceId) }.items.size

    suspend fun getDashboard(instanceId: String): MediaryScoutDashboardData = coroutineScope {
        val library = async { checkAgentApi { api.getLibrary(instanceId = instanceId) } }
        val activity = async { checkAgentApi { api.getActivity(instanceId = instanceId) } }
        MediaryScoutDashboardData(library.await().items, activity.await())
    }

    suspend fun acquire(instanceId: String, request: MediaryScoutAcquireRequest): MediaryScoutAcquireResult =
        checkAgentApi {
            val response = api.acquire(instanceId, request)
            if (response.isSuccessful) return@checkAgentApi checkNotNull(response.body())
            val error = withContext(Dispatchers.IO) {
                response.errorBody()?.use { body ->
                    if (response.code() != 409 && response.code() != 404) return@use null
                    try {
                        json.decodeFromString<MediaryScoutAcquireResult>(body.string())
                    } catch (_: SerializationException) {
                        null
                    }
                }
            }
            if (response.code() == 409 && error?.status == "ambiguous" ||
                response.code() == 404 && error?.status == "not_found"
            ) {
                return@checkAgentApi checkNotNull(error)
            }
            throw HttpException(response)
        }

    private suspend fun <T> checkAgentApi(request: suspend () -> T): T {
        try {
            return request()
        } catch (error: HttpException) {
            if (error.code() == 404) throw MediaryScoutApiUnavailable(error)
            throw error
        }
    }
}
