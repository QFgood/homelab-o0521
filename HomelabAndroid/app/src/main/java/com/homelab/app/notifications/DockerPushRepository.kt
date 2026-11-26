package com.homelab.app.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homelab.app.data.repository.dataStore
import com.homelab.app.data.security.InstanceCredentials
import com.homelab.app.data.security.SecureCredentialsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class DockerPushStatus { STOPPED, CONNECTING, CONNECTED, RECONNECTING, REJECTED }

data class DockerPushSettings(val topicUrl: String = "", val enabled: Boolean = false)

internal fun parseDockerTopic(raw: String): HttpUrl? {
    val url = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
    return url.takeIf {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null &&
            Regex("[A-Za-z0-9_-]{1,64}").matches(it.pathSegments.last())
    }
}

@Singleton
class DockerPushRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    private val credentials: SecureCredentialsStore
) {
    private val preferences = context.dataStore
    private val _status = MutableStateFlow(DockerPushStatus.STOPPED)
    val status = _status.asStateFlow()
    val settings = preferences.data.map {
        DockerPushSettings(it[TOPIC].orEmpty(), it[ENABLED] == true)
    }.distinctUntilChanged()

    suspend fun enable(topic: HttpUrl, token: String) = withContext(Dispatchers.IO) {
        val url = topic.toString()
        val oldUrl = preferences.data.first()[TOPIC]
        if (token.isEmpty()) {
            credentials.deleteCredentials(credentialKey(url))
        } else {
            credentials.saveCredentials(credentialKey(url), InstanceCredentials(token = token))
        }
        preferences.edit {
            if (it[TOPIC] != url || it[ENABLED] != true) {
                it[SINCE] = (System.currentTimeMillis() / 1000).toString()
            }
            it[TOPIC] = url
            it[ENABLED] = true
        }
        if (oldUrl != null && oldUrl != url) credentials.deleteCredentials(credentialKey(oldUrl))
    }

    suspend fun disable() {
        preferences.edit { it[ENABLED] = false }
    }

    suspend fun token(url: String): String = withContext(Dispatchers.IO) {
        credentials.getCredentials(credentialKey(url))?.token.orEmpty()
    }

    suspend fun cursor(url: String): String? = preferences.data.first().let {
        if (it[TOPIC] == url && it[ENABLED] == true) it[SINCE] else null
    }

    suspend fun advance(url: String, id: String) {
        preferences.edit {
            if (it[TOPIC] == url && it[ENABLED] == true) it[SINCE] = id
        }
    }

    fun setStatus(status: DockerPushStatus) {
        _status.value = status
    }

    private fun credentialKey(url: String): String = "docker-push:$url"

    private companion object {
        val TOPIC = stringPreferencesKey("docker_push_topic")
        val ENABLED = booleanPreferencesKey("docker_push_enabled")
        val SINCE = stringPreferencesKey("docker_push_cursor")
    }
}
