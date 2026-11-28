package com.homelab.app.ui.mediaryscout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.api.MediaryScoutEntry
import com.homelab.app.data.remote.api.MediaryScoutTask
import com.homelab.app.data.repository.MediaryScoutDashboardData
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class DashboardTab(@StringRes val label: Int, val icon: ImageVector) {
    SEARCH(R.string.mediary_search_submit, Icons.Default.Search),
    LIBRARY(R.string.mediary_scout_library, Icons.Default.VideoLibrary),
    NOTIFICATIONS(R.string.mediary_scout_notifications, Icons.Default.Notifications),
    TASKS(R.string.mediary_scout_activity, Icons.Default.Timeline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaryScoutDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: MediaryScoutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DashboardTab.SEARCH) }
    val tabState = rememberSaveableStateHolder()
    val acquireState by viewModel.acquireState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val patrolState by viewModel.patrolState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                DashboardTab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label), maxLines = 1) }
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ServiceIcon(type = ServiceType.MEDIARY_SCOUT, size = 32.dp, iconSize = 24.dp)
                        Text(stringResource(R.string.service_mediary_scout))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = state != UiState.Loading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = viewModel.instanceId,
                onInstanceSelected = { instance -> viewModel.selectInstance(instance.id, onNavigateToInstance) },
                label = stringResource(R.string.service_instance_label),
                showAddInstance = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            tabState.SaveableStateProvider(tab.name) {
                when (tab) {
                    DashboardTab.SEARCH -> MediaryScoutSearchPanel(
                        searchState = searchState,
                        acquireState = acquireState,
                        onSearch = viewModel::search,
                        onSubmit = viewModel::acquire,
                        onFinish = {
                            val requested = (acquireState as? UiState.Success)?.data?.status == "requested"
                            viewModel.clearAcquisition()
                            if (requested) tab = DashboardTab.TASKS
                        }
                    )
                    else -> {
                        if (tab == DashboardTab.TASKS) {
                            FilledTonalButton(
                                onClick = viewModel::runPatrol,
                                enabled = patrolState != UiState.Loading,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) { Text(stringResource(R.string.mediary_scout_patrol)) }
                            when (val patrol = patrolState) {
                                UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                is UiState.Error -> Text(patrol.message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                                is UiState.Success -> Text(stringResource(R.string.mediary_scout_patrol_done), Modifier.padding(horizontal = 16.dp))
                                else -> Unit
                            }
                        }
                        when (val current = state) {
                            UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            UiState.Offline -> ErrorScreen(
                                message = stringResource(R.string.error_network), onRetry = viewModel::refresh, isOffline = true
                            )
                            is UiState.Error -> ErrorScreen(message = current.message, onRetry = viewModel::refresh)
                            is UiState.Success -> MediaryScoutContent(data = current.data, tab = tab)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaryScoutContent(data: MediaryScoutDashboardData, tab: DashboardTab) {
    val libraryState = rememberLazyListState()
    val tasksState = rememberLazyListState()
    val notificationsState = rememberLazyListState()
    val (count, listState, emptyMessage) = when (tab) {
        DashboardTab.LIBRARY -> Triple(data.entries.size, libraryState, R.string.mediary_scout_no_entries)
        DashboardTab.TASKS -> Triple(data.activity.active.size, tasksState, R.string.mediary_scout_no_tasks)
        DashboardTab.NOTIFICATIONS -> Triple(data.activity.recent.size, notificationsState, R.string.mediary_scout_no_notifications)
        else -> return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (count == 0) {
            item { DashboardCard { Text(stringResource(emptyMessage)) } }
        } else {
            when (tab) {
                DashboardTab.LIBRARY -> items(data.entries) { entry -> LibraryEntry(entry) }
                DashboardTab.TASKS -> items(data.activity.active, key = { it.workflowRunId }) { task ->
                    TaskEntry(task)
                }
                DashboardTab.NOTIFICATIONS -> items(data.activity.recent, key = { it.id }) { notification ->
                    DashboardCard {
                        EntryTitle(notification.title)
                        if (notification.body.isNotBlank()) Text(notification.body)
                        notification.createdAt?.let { EntryDetail(formatTimestamp(it)) }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun LibraryEntry(entry: MediaryScoutEntry) {
    DashboardCard {
        EntryTitle(entry.title)
        val mediaType = when (entry.type) {
            "movie" -> stringResource(R.string.mediary_scout_movie)
            "tv" -> stringResource(R.string.mediary_scout_tv)
            "anime" -> stringResource(R.string.mediary_scout_anime)
            else -> entry.type
        }
        val status = when (entry.status) {
            "active" -> stringResource(R.string.mediary_scout_active)
            "completed" -> stringResource(R.string.media_qb_filter_completed)
            else -> entry.status
        }
        val season = if (entry.type != "movie") {
            stringResource(R.string.mediary_scout_season, entry.seasonNumber)
        } else {
            null
        }
        EntryDetail(listOfNotNull(mediaType, entry.year?.takeIf { it > 0 }?.toString(), season, status).joinToString(" · "))

        if (entry.totalEpisodes > 0) {
            Text(stringResource(R.string.mediary_scout_progress, entry.obtainedCount, entry.totalEpisodes))
            LinearProgressIndicator(
                progress = { (entry.obtainedCount.toFloat() / entry.totalEpisodes).coerceIn(0f, 1f) },
                color = ServiceType.MEDIARY_SCOUT.primaryColor,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(stringResource(R.string.mediary_scout_obtained, entry.obtainedCount))
        }

        if (entry.missingAired.isNotEmpty()) {
            EntryDetail(stringResource(R.string.mediary_scout_missing, entry.missingAired.joinToString(", ")))
        }
    }
}

@Composable
private fun TaskEntry(task: MediaryScoutTask) {
    DashboardCard {
        EntryTitle(task.title)
        val status = when (task.status) {
            "queued" -> stringResource(R.string.mediary_scout_queued)
            "running" -> stringResource(R.string.proxmox_running_task)
            else -> task.status
        }
        val season = task.seasonNumber.takeIf { it > 0 }?.let { stringResource(R.string.mediary_scout_season, it) }
        EntryDetail(listOfNotNull(season, status, task.startedAt?.let { formatTimestamp(it) }).joinToString(" · "))
    }
}

@Composable
private fun DashboardCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun EntryTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EntryDetail(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun formatTimestamp(value: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(value, locale) {
        runCatching {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(value))
        }.getOrDefault(value)
    }
}
