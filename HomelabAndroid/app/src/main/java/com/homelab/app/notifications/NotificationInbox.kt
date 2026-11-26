package com.homelab.app.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.notificationInboxStore by preferencesDataStore(name = "notification_inbox")

@Serializable
enum class NotificationSource { DOCKER, SERVICE }

@Serializable
data class InboxNotification(
    val id: String,
    val source: NotificationSource,
    val title: String,
    val message: String,
    val receivedAt: Long = System.currentTimeMillis()
)

@Singleton
class NotificationInbox @Inject constructor(
    @param:ApplicationContext context: Context,
    private val json: Json
) {
    private val store = context.notificationInboxStore
    val notifications = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decode(it[RECORDS]) }

    suspend fun record(notification: InboxNotification) {
        store.edit { preferences ->
            val current = decode(preferences[RECORDS])
            if (current.any { it.id == notification.id }) return@edit
            val entry = notification.copy(
                title = notification.title.take(160),
                message = notification.message.take(4096)
            )
            preferences[RECORDS] = json.encodeToString((listOf(entry) + current).take(200))
        }
    }

    private fun decode(raw: String?): List<InboxNotification> {
        if (raw == null) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private companion object {
        val RECORDS = stringPreferencesKey("notification_history")
    }
}
