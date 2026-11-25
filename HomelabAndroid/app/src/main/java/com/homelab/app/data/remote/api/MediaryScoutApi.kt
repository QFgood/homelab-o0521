package com.homelab.app.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
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
}

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
