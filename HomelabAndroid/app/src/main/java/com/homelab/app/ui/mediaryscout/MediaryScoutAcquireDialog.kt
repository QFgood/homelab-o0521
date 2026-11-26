package com.homelab.app.ui.mediaryscout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.api.MediaryScoutAcquireResult
import com.homelab.app.data.remote.api.MediaryScoutCandidate
import com.homelab.app.data.remote.api.MediaryScoutSearchResult
import com.homelab.app.util.UiState

private enum class AcquireType(val apiValue: String?, @StringRes val label: Int) {
    ANY(null, R.string.all),
    MOVIE("movie", R.string.mediary_scout_movie),
    TV("tv", R.string.mediary_scout_tv)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaryScoutAcquireDialog(
    searchState: UiState<MediaryScoutSearchResult>,
    acquireState: UiState<MediaryScoutAcquireResult>,
    onSearch: (String, String?) -> Unit,
    onSubmit: (MediaryScoutCandidate, Int?) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AcquireType.ANY) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf("") }
    val busy = searchState == UiState.Loading || acquireState == UiState.Loading
    val matches = (searchState as? UiState.Success)?.data?.items
    val result = (acquireState as? UiState.Success)?.data
    val selected = matches?.firstOrNull { it.tmdbId == selectedId && it.type == selectedType }
    val parsedSeason = season.toIntOrNull()
    val seasonValid = selected?.type != "tv" || parsedSeason != null &&
        (selected.seasonNumbers.isEmpty() || parsedSeason in selected.seasonNumbers)

    fun clearSelection() {
        selectedId = null
        selectedType = null
        season = ""
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.mediary_acquire_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    result != null -> Text(result.message)
                    matches != null -> {
                        if (matches.isEmpty()) Text(stringResource(R.string.mediary_search_empty))
                        else Text(stringResource(R.string.mediary_acquire_hint))
                        Column(Modifier.selectableGroup()) {
                            matches.forEach { candidate ->
                                val isSelected = candidate == selected
                                Row(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = isSelected, enabled = !busy, role = Role.RadioButton,
                                        onClick = {
                                            selectedId = candidate.tmdbId
                                            selectedType = candidate.type
                                            season = if (candidate.type == "tv") {
                                                val numbers = candidate.seasonNumbers
                                                (numbers.firstOrNull { it == 1 } ?: numbers.firstOrNull() ?: 1).toString()
                                            } else ""
                                        }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = null, enabled = !busy)
                                    CandidateDetails(candidate, isSelected)
                                }
                            }
                        }
                        if (selected?.type == "tv") {
                            OutlinedTextField(
                                value = season,
                                onValueChange = { value ->
                                    if (value.length <= 4 && value.all { it in '0'..'9' }) season = value
                                },
                                label = { Text(stringResource(R.string.mediary_acquire_season)) },
                                supportingText = if (selected.seasonNumbers.isNotEmpty()) {
                                    { Text(selected.seasonNumbers.joinToString(", ")) }
                                } else null,
                                isError = season.isNotEmpty() && !seasonValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !busy,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { if (it.length <= 256) query = it },
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
                    }
                }
                if (busy) CircularProgressIndicator()
                (searchState as? UiState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                (acquireState as? UiState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                result != null -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                matches == null -> TextButton(
                    onClick = { clearSelection(); onSearch(query, type.apiValue) },
                    enabled = !busy && query.isNotBlank()
                ) { Text(stringResource(R.string.mediary_search_submit)) }
                else -> TextButton(
                    onClick = { selected?.let { onSubmit(it, parsedSeason) } },
                    enabled = !busy && selected != null && seasonValid
                ) { Text(stringResource(R.string.mediary_acquire_submit)) }
            }
        },
        dismissButton = {
            if (matches != null || result != null) {
                TextButton(onClick = { clearSelection(); onEdit() }, enabled = !busy) {
                    Text(stringResource(R.string.mediary_search_again))
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun CandidateDetails(candidate: MediaryScoutCandidate, selected: Boolean) {
    val kind = if (candidate.type == "movie") R.string.mediary_scout_movie else R.string.mediary_scout_tv
    val original = candidate.originalTitle.takeIf { it.isNotBlank() && it != candidate.title }
    val details = listOfNotNull(original, candidate.year?.toString(), stringResource(kind)).joinToString(" · ")
    Column {
        Text(candidate.title, style = MaterialTheme.typography.titleSmall)
        Text(details, style = MaterialTheme.typography.bodySmall)
        if (candidate.overview.isNotBlank()) {
            Text(
                candidate.overview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (selected) 5 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
