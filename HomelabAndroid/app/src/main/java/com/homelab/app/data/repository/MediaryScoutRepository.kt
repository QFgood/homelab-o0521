package com.homelab.app.data.repository

import com.homelab.app.data.remote.HtmlResponseException
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.MediaryScoutAcquireRequest
import com.homelab.app.data.remote.api.MediaryScoutAcquireResult
import com.homelab.app.data.remote.api.MediaryScoutActivity
import com.homelab.app.data.remote.api.MediaryScoutApi
import com.homelab.app.data.remote.api.MediaryScoutConfigUpdate
import com.homelab.app.data.remote.api.MediaryScoutEntry
import com.homelab.app.data.remote.api.MediaryScoutSearchResult
import com.homelab.app.domain.model.ServiceInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import retrofit2.Retrofit

data class MediaryScoutDashboardData(
    val entries: List<MediaryScoutEntry>,
    val activity: MediaryScoutActivity
)

class MediaryScoutApiUnavailable(cause: HttpException) : Exception(cause)

class MediaryScoutSearchUnavailable(cause: Exception) : Exception(cause)

@Singleton
class MediaryScoutRepository @Inject constructor(
    private val api: MediaryScoutApi,
    private val retrofit: Retrofit,
    private val tlsClientSelector: TlsClientSelector
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

    suspend fun search(instanceId: String, query: String, type: String?): MediaryScoutSearchResult {
        try {
            val results = api.search(instanceId, query, type)
            return MediaryScoutSearchResult(results.items.filter { it.tmdbId > 0 && it.type in setOf("movie", "tv") })
        } catch (error: HttpException) {
            if (error.code() == 404) throw MediaryScoutSearchUnavailable(error)
            throw error
        } catch (error: HtmlResponseException) {
            if (error.statusCode == 404) throw MediaryScoutSearchUnavailable(error)
            throw error
        }
    }

    suspend fun acquire(instanceId: String, request: MediaryScoutAcquireRequest): MediaryScoutAcquireResult =
        checkAgentApi { api.acquire(instanceId, request) }

    suspend fun getConfig(instanceId: String) = checkAgentApi { api.getConfig(instanceId) }

    suspend fun saveConfig(instanceId: String, update: MediaryScoutConfigUpdate) =
        checkAgentApi { api.updateConfig(instanceId, update).config }

    suspend fun runPatrol(instanceId: String) = checkAgentApi { api.runPatrol(instanceId) }

    private suspend fun <T> checkAgentApi(request: suspend () -> T): T {
        try {
            return request()
        } catch (error: HttpException) {
            if (error.code() == 404) throw MediaryScoutApiUnavailable(error)
            throw error
        }
    }
}
