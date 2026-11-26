package com.homelab.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.notifications.DockerPushRepository
import com.homelab.app.notifications.DockerPushService
import com.homelab.app.notifications.DockerPushStatus
import com.homelab.app.notifications.parseDockerTopic
import com.homelab.app.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DockerPushViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DockerPushRepository
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val status = repository.status
    var busy by mutableStateOf(false)
        private set
    @get:StringRes
    var error by mutableStateOf<Int?>(null)
        private set

    suspend fun loadToken(url: String): String? = try {
        repository.token(url)
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        error = R.string.docker_push_error
        null
    }

    fun showPermissionError() {
        error = R.string.alerts_permission_denied
    }

    fun connect(rawUrl: String, rawToken: String) {
        val topic = parseDockerTopic(rawUrl)
        if (topic == null) {
            error = R.string.docker_push_invalid_url
            return
        }
        val token = rawToken.trim()
        if (token.length > 1024 || token.any { it !in '!'..'~' }) {
            error = R.string.error_invalid_credentials
            return
        }
        change {
            repository.enable(topic, token)
            DockerPushService.start(context, restart = true)
        }
    }

    fun disconnect() = change {
        repository.disable()
        DockerPushService.stop(context)
        repository.setStatus(DockerPushStatus.STOPPED)
    }

    private fun change(action: suspend () -> Unit) {
        if (busy) return
        error = null
        busy = true
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                error = R.string.docker_push_error
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun DockerPushSection(viewModel: DockerPushViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val config = settings ?: return
    val context = LocalContext.current
    var topic by remember { mutableStateOf(config.topicUrl) }
    var token by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(config.topicUrl) {
        loaded = false
        topic = config.topicUrl
        token = if (config.topicUrl.isEmpty()) "" else viewModel.loadToken(config.topicUrl) ?: return@LaunchedEffect
        loaded = true
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && NotificationHelper.canPostNotifications(context)) {
            viewModel.connect(topic, token)
        } else {
            viewModel.showPermissionError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.docker_push_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.docker_push_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = topic, onValueChange = { topic = it; token = "" },
                label = { Text(stringResource(R.string.docker_push_topic)) },
                placeholder = { Text("https://ntfy.example.com/docker") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text(stringResource(R.string.docker_push_token)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            val statusLabel = when (status) {
                DockerPushStatus.STOPPED -> R.string.docker_push_stopped
                DockerPushStatus.CONNECTING -> R.string.docker_push_connecting
                DockerPushStatus.CONNECTED -> R.string.docker_push_connected
                DockerPushStatus.RECONNECTING -> R.string.docker_push_reconnecting
                DockerPushStatus.REJECTED -> R.string.docker_push_rejected
            }
            Text(stringResource(statusLabel))
            viewModel.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                if (NotificationHelper.canPostNotifications(context)) {
                    viewModel.connect(topic, token)
                } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            }, enabled = loaded && !viewModel.busy && topic.isNotBlank()) {
                Text(stringResource(R.string.docker_push_connect))
            }
            if (config.enabled) {
                OutlinedButton(onClick = viewModel::disconnect, enabled = !viewModel.busy) {
                    Text(stringResource(R.string.docker_push_disconnect))
                }
            }
        }
    }
}
