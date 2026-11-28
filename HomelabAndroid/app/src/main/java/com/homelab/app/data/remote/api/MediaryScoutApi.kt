package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.NO_FALLBACK_REPLAY_HEADER
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

interface MediaryScoutApi {
    @Headers("Accept: application/json")
    @GET
    suspend fun getLibrary(
        @Url url: String = "api/agent/library",
        @Header("X-Homelab-Instance-Id") instanceId: String? = null,
        @Header("Authorization") authorization: String? = null
    ): MediaryScoutLibrary

    @Headers("Accept: application/json")
    @GET("api/agent/activity")
    suspend fun getActivity(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("limit") limit: Int = 20
    ): MediaryScoutActivity

    @Headers("Accept: application/json")
    @GET("api/agent/search")
    suspend fun search(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("query") query: String,
        @Query("type") type: String?
    ): MediaryScoutSearchResult

    @Headers("Accept: application/json")
    @GET("api/agent/config")
    suspend fun getConfig(@Header("X-Homelab-Instance-Id") instanceId: String): MediaryScoutConfig

    @Headers("Accept: application/json", "$NO_FALLBACK_REPLAY_HEADER: true")
    @PUT("api/agent/config")
    suspend fun updateConfig(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body update: MediaryScoutConfigUpdate
    ): MediaryScoutConfigResponse

    @Headers("Accept: application/json", "$NO_FALLBACK_REPLAY_HEADER: true")
    @POST("api/agent/patrol")
    suspend fun runPatrol(@Header("X-Homelab-Instance-Id") instanceId: String)

    @Headers("Accept: application/json", "$NO_FALLBACK_REPLAY_HEADER: true")
    @POST("api/agent/acquire")
    suspend fun acquire(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: MediaryScoutAcquireRequest
    ): MediaryScoutAcquireResult
}

@Serializable
data class MediaryScoutAcquireRequest(
    val tmdbId: Long,
    val type: String,
    val season: Int? = null
)

@Serializable
data class MediaryScoutAcquireResult(
    val status: String,
    val message: String
)

@Serializable
data class MediaryScoutSearchResult(val items: List<MediaryScoutCandidate>)

@Serializable
data class MediaryScoutCandidate(
    val tmdbId: Long,
    val title: String,
    val year: Int? = null,
    val type: String,
    val originalTitle: String = "",
    val overview: String = "",
    val seasonNumbers: List<Int> = emptyList(),
    val posterPath: String? = null
)

@Serializable
data class MediaryScoutLibrary(val items: List<MediaryScoutEntry>)

@Serializable
data class MediaryScoutEntry(
    val title: String,
    val type: String,
    val year: Int? = null,
    val seasonNumber: Int,
    val status: String,
    val totalEpisodes: Int,
    val obtainedCount: Int,
    val missingAired: List<String>
)

@Serializable
data class MediaryScoutActivity(
    val active: List<MediaryScoutTask>,
    val recent: List<MediaryScoutNotification>
)

@Serializable
data class MediaryScoutTask(
    val workflowRunId: String,
    val title: String,
    val seasonNumber: Int,
    val status: String,
    val startedAt: String? = null
)

@Serializable
data class MediaryScoutNotification(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String? = null
)


@Serializable
data class MediaryScoutConfig(
    val qualityPreference: String? = null,
    val preferredLanguage: String? = null,
    val dailySweepTime: String = "",
    val storages: List<MediaryScoutStorage> = emptyList()
)

@Serializable
data class MediaryScoutStorage(val brand: String, val name: String? = null)

@Serializable
data class MediaryScoutConfigUpdate(
    val qualityPreference: String? = null,
    val preferredLanguage: String? = null,
    val dailySweepTime: String? = null
)

@Serializable
data class MediaryScoutConfigResponse(val config: MediaryScoutConfig)
