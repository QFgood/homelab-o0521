package com.homelab.app.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.notifications.InboxNotification
import com.homelab.app.notifications.NotificationInbox
import com.homelab.app.notifications.NotificationSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class NotificationInboxViewModel @Inject constructor(inbox: NotificationInbox) : ViewModel() {
    val notifications = inbox.notifications
}

@Composable
fun NotificationInboxScreen(viewModel: NotificationInboxViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle(initialValue = emptyList())
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.nav_notifications), style = MaterialTheme.typography.headlineMedium)
        }
        if (notifications.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.inbox_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(notifications, key = { it.id }) { entry ->
            NotificationCard(entry, formatter.format(Instant.ofEpochMilli(entry.receivedAt)))
        }
    }
}

@Composable
private fun NotificationCard(entry: InboxNotification, timestamp: String) {
    val source = stringResource(when (entry.source) {
        NotificationSource.DOCKER -> R.string.docker_push_title
        NotificationSource.SERVICE -> R.string.alerts_section_title
    })
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$source · $timestamp", style = MaterialTheme.typography.labelMedium)
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            if (entry.message.isNotBlank()) Text(entry.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
