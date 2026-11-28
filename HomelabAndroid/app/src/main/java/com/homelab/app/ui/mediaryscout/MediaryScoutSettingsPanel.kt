package com.homelab.app.ui.mediaryscout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.api.MediaryScoutConfig
import com.homelab.app.data.remote.api.MediaryScoutConfigUpdate
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.util.UiState

private val sweepTimePattern = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")

@Composable
internal fun MediaryScoutSettingsPanel(
    state: UiState<MediaryScoutConfig>,
    saveState: UiState<Unit>,
    onRetry: () -> Unit,
    onSave: (MediaryScoutConfigUpdate) -> Unit
) {
    val config = when (state) {
        is UiState.Success -> state.data
        is UiState.Error -> {
            ErrorScreen(message = state.message, onRetry = onRetry)
            return
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
    }
    var quality by rememberSaveable(config.qualityPreference) { mutableStateOf(config.qualityPreference) }
    var language by rememberSaveable(config.preferredLanguage) { mutableStateOf(config.preferredLanguage.orEmpty()) }
    var sweepTime by rememberSaveable(config.dailySweepTime) { mutableStateOf(config.dailySweepTime) }
    val loading = saveState == UiState.Loading
    val update = MediaryScoutConfigUpdate(
        qualityPreference = quality.takeIf { it != config.qualityPreference },
        preferredLanguage = language.trim().takeIf { it != config.preferredLanguage.orEmpty() },
        dailySweepTime = sweepTime.takeIf { it != config.dailySweepTime }
    )
    val canSave = update != MediaryScoutConfigUpdate() &&
        (update.dailySweepTime == null || sweepTimePattern.matches(sweepTime))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.mediary_scout_quality), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = quality == "high", onClick = { quality = "high" }, enabled = !loading,
                label = { Text(stringResource(R.string.mediary_scout_high)) }
            )
            FilterChip(
                selected = quality == "medium", onClick = { quality = "medium" }, enabled = !loading,
                label = { Text(stringResource(R.string.mediary_scout_medium)) }
            )
        }
        OutlinedTextField(
            value = language, onValueChange = { language = it }, enabled = !loading, singleLine = true,
            label = { Text(stringResource(R.string.mediary_scout_language)) }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = sweepTime, onValueChange = { if (it.length <= 5) sweepTime = it },
            isError = update.dailySweepTime != null && !sweepTimePattern.matches(sweepTime),
            enabled = !loading, singleLine = true,
            label = { Text(stringResource(R.string.mediary_scout_sweep_time)) }, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSave(update) }, enabled = !loading && canSave) {
            Text(stringResource(R.string.save))
        }
        when (saveState) {
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(saveState.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> if (!canSave) Text(stringResource(R.string.pangolin_saved))
            else -> Unit
        }
        if (config.storages.isNotEmpty()) {
            Text(stringResource(R.string.mediary_scout_storage), style = MaterialTheme.typography.titleMedium)
            config.storages.forEach { storage -> Text(storage.name?.takeIf(String::isNotBlank) ?: storage.brand) }
        }
    }
}
