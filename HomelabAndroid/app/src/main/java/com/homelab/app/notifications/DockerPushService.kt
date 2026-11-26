package com.homelab.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DockerPushService : Service() {
    @Inject lateinit var repository: DockerPushRepository
    @Inject lateinit var preferences: LocalPreferencesRepository
    @Inject lateinit var ntfy: NtfyClient
    @Inject lateinit var inbox: NotificationInbox

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var subscription: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            NotificationHelper.dockerConnectionNotification(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (subscription?.isActive == true && intent?.action != RESTART) return START_STICKY
        subscription?.cancel()
        subscription = scope.launch {
            try {
                subscribe()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                repository.setStatus(DockerPushStatus.REJECTED)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun subscribe() {
        val settings = repository.settings.first()
        val topic = parseDockerTopic(settings.topicUrl)
        if (!settings.enabled || topic == null || !NotificationHelper.canPostNotifications(this)) {
            stopSelf()
            return
        }

        val token = repository.token(settings.topicUrl)
        var backoff = 5_000L
        repository.setStatus(DockerPushStatus.CONNECTING)
        while (scope.isActive) {
            val cursor = repository.cursor(settings.topicUrl)
            if (cursor == null) {
                stopSelf()
                return
            }
            try {
                ntfy.subscribe(topic, token, cursor).collect { event ->
                    when (event.event) {
                        "open", "keepalive" -> {
                            backoff = 5_000L
                            repository.setStatus(DockerPushStatus.CONNECTED)
                        }
                        "message" -> {
                            if (event.id.isNotBlank() && event.id != repository.cursor(settings.topicUrl)) {
                                val context = preferences.languageMode.first().localizedContext(this)
                                inbox.record(InboxNotification(
                                    id = "docker:${event.id}",
                                    source = NotificationSource.DOCKER,
                                    title = event.title.ifBlank { context.getString(com.homelab.app.R.string.docker_push_title) },
                                    message = event.message
                                ))
                                if (NotificationHelper.notifyDocker(context, event.id, event.title, event.message)) {
                                    repository.advance(settings.topicUrl, event.id)
                                } else {
                                    stopSelf()
                                }
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: NtfySubscriptionRejected) {
                repository.setStatus(DockerPushStatus.REJECTED)
                stopSelf()
                return
            } catch (_: Exception) {
                // A lost network or ended stream resumes from the saved message ID.
            }
            repository.setStatus(DockerPushStatus.RECONNECTING)
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (repository.status.value != DockerPushStatus.REJECTED) {
            repository.setStatus(DockerPushStatus.STOPPED)
        }
        super.onDestroy()
    }

    companion object {
        private const val FOREGROUND_ID = 33102
        private const val RESTART = "com.homelab.app.RESTART_DOCKER_PUSH"

        fun start(context: Context, restart: Boolean = false) {
            val intent = Intent(context, DockerPushService::class.java)
            if (restart) intent.action = RESTART
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DockerPushService::class.java))
        }
    }
}
