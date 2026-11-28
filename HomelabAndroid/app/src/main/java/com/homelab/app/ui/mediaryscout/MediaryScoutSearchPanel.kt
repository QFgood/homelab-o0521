package com.homelab.app.ui.mediaryscout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
internal fun MediaryScoutSearchPanel(
    searchState: UiState<MediaryScoutSearchResult>,
    acquireState: UiState<MediaryScoutAcquireResult>,
    onSearch: (String, String?) -> Unit,
    onSubmit: (MediaryScoutCandidate, Int?) -> Unit,
    onFinish: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AcquireType.ANY) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf("") }
    val matches = (searchState as? UiState.Success)?.data?.items.orEmpty()
    val result = (acquireState as? UiState.Success)?.data
    val selected = matches.firstOrNull { it.tmdbId == selectedId && it.type == selectedType }
    val busy = searchState == UiState.Loading || acquireState == UiState.Loading
    val focus = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { if (it.length <= 256) query = it },
                        label = { Text(stringResource(R.string.mediary_acquire_query)) },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        focus.clearFocus()
                        onSearch(query, type.apiValue)
                    }, enabled = !busy && query.isNotBlank()) {
                        Text(stringResource(R.string.mediary_search_submit))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AcquireType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option, onClick = { type = option }, enabled = !busy,
                            label = { Text(stringResource(option.label)) }
                        )
                    }
                }
            }
        }
        when (searchState) {
            UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item { Text(searchState.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> if (matches.isEmpty()) item { Text(stringResource(R.string.mediary_search_empty)) }
            else -> Unit
        }
        items(matches.distinctBy { it.type to it.tmdbId }, key = { "${it.type}:${it.tmdbId}" }) { candidate ->
            CandidateCard(candidate, enabled = !busy) {
                selectedId = candidate.tmdbId
                selectedType = candidate.type
                val numbers = candidate.seasonNumbers
                season = (numbers.firstOrNull { it == 1 } ?: numbers.firstOrNull() ?: 1).toString()
            }
        }
    }

    if (selected != null) {
        val parsedSeason = season.toIntOrNull()
        val seasonValid = selected.type != "tv" || parsedSeason != null &&
            (selected.seasonNumbers.isEmpty() || parsedSeason in selected.seasonNumbers)
        val close = {
            selectedId = null
            selectedType = null
            onFinish()
        }
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            title = { Text(stringResource(R.string.mediary_acquire_submit)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result != null) Text(result.message) else {
                        CandidateCard(selected)
                        if (selected.type == "tv") {
                            OutlinedTextField(
                                value = season,
                                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) season = it },
                                label = { Text(stringResource(R.string.mediary_acquire_season)) },
                                supportingText = if (selected.seasonNumbers.isNotEmpty()) {
                                    { Text(selected.seasonNumbers.joinToString(", ")) }
                                } else null,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (busy) CircularProgressIndicator()
                        (acquireState as? UiState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                if (result != null) TextButton(onClick = close) { Text(stringResource(R.string.close)) }
                else Button(
                    onClick = { onSubmit(selected, parsedSeason.takeIf { selected.type == "tv" }) },
                    enabled = !busy && seasonValid
                ) { Text(stringResource(R.string.mediary_acquire_submit)) }
            },
            dismissButton = {
                if (result == null) TextButton(onClick = close, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun CandidateCard(
    candidate: MediaryScoutCandidate,
    enabled: Boolean = true,
    onAcquire: (() -> Unit)? = null
) {
    val kind = if (candidate.type == "movie") R.string.mediary_scout_movie else R.string.mediary_scout_tv
    val original = candidate.originalTitle.takeIf { it.isNotBlank() && it != candidate.title }
    val details = listOfNotNull(original, candidate.year?.toString(), stringResource(kind)).joinToString(" · ")
    val poster = candidate.posterPath?.takeIf { it.startsWith("/") && !it.startsWith("//") }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(8.dp)
            Surface(
                modifier = Modifier.size(width = 84.dp, height = 126.dp),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (poster != null) {
                        AsyncImage(
                            model = "https://image.tmdb.org/t/p/w342$poster",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize().clip(shape)
                        )
                    } else {
                        Text(stringResource(kind), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                Text(details, style = MaterialTheme.typography.bodySmall)
                if (onAcquire != null) {
                    Button(onClick = onAcquire, enabled = enabled) {
                        Text(stringResource(R.string.mediary_acquire_action))
                    }
                }
                if (candidate.overview.isNotBlank()) {
                    Text(
                        candidate.overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (onAcquire == null) 5 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
