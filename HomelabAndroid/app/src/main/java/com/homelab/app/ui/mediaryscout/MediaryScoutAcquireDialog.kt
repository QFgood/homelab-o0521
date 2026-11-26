package com.homelab.app.ui.mediaryscout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.api.MediaryScoutAcquireRequest
import com.homelab.app.data.remote.api.MediaryScoutAcquireResult
import com.homelab.app.util.UiState

private enum class AcquireType(val apiValue: String?, @StringRes val label: Int) {
    ANY(null, R.string.all),
    MOVIE("movie", R.string.mediary_scout_movie),
    TV("tv", R.string.mediary_scout_tv)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaryScoutAcquireDialog(
    state: UiState<MediaryScoutAcquireResult>,
    onSubmit: (MediaryScoutAcquireRequest) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AcquireType.ANY) }
    var season by rememberSaveable { mutableStateOf("") }
    val busy = state == UiState.Loading
    val result = (state as? UiState.Success)?.data
    val parsedSeason = season.toIntOrNull()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.mediary_acquire_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (result != null) {
                    Text(result.message)
                    if (result.status == "ambiguous") {
                        result.candidates.forEach { candidate ->
                            val kind = if (candidate.type == "movie") R.string.mediary_scout_movie else R.string.mediary_scout_tv
                            val name = listOfNotNull(candidate.title, candidate.year?.toString(), stringResource(kind))
                                .joinToString(" · ")
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onSubmit(MediaryScoutAcquireRequest(
                                        query.trim(), candidate.type,
                                        parsedSeason.takeIf { candidate.type == "tv" },
                                        candidate.tmdbId
                                    ))
                                }
                            ) {
                                Text(stringResource(R.string.mediary_acquire_candidate, name))
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.mediary_acquire_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.mediary_acquire_query)) },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AcquireType.entries.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                enabled = !busy,
                                label = { Text(stringResource(option.label)) }
                            )
                        }
                    }
                    if (type != AcquireType.MOVIE) {
                        OutlinedTextField(
                            value = season,
                            onValueChange = { value ->
                                if (value.length <= 4 && value.all { it in '0'..'9' }) season = value
                            },
                            label = { Text(stringResource(R.string.mediary_acquire_season)) },
                            placeholder = { Text("1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !busy,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (busy) CircularProgressIndicator()
                    if (state is UiState.Error) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                TextButton(
                    onClick = {
                        onSubmit(MediaryScoutAcquireRequest(
                            query.trim(), type.apiValue, parsedSeason.takeIf { type != AcquireType.MOVIE }
                        ))
                    },
                    enabled = !busy && query.isNotBlank()
                ) { Text(stringResource(R.string.mediary_acquire_submit)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            if (result != null) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.back)) }
            } else {
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
