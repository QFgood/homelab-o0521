package com.homelab.app.ui.mediaryscout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.api.MediaryScoutAcquireRequest
import com.homelab.app.data.remote.api.MediaryScoutAcquireResult
import com.homelab.app.data.remote.api.MediaryScoutCandidate
import com.homelab.app.data.remote.api.MediaryScoutConfig
import com.homelab.app.data.remote.api.MediaryScoutConfigUpdate
import com.homelab.app.data.remote.api.MediaryScoutSearchResult
import com.homelab.app.data.repository.MediaryScoutDashboardData
import com.homelab.app.data.repository.MediaryScoutRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaryScoutViewModel @Inject constructor(
    private val repository: MediaryScoutRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    val instances = servicesRepository.instancesByType
        .map { it[ServiceType.MEDIARY_SCOUT].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<UiState<MediaryScoutDashboardData>>(UiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _searchState = MutableStateFlow<UiState<MediaryScoutSearchResult>>(UiState.Idle)
    val searchState = _searchState.asStateFlow()
    private val _acquireState = MutableStateFlow<UiState<MediaryScoutAcquireResult>>(UiState.Idle)
    val acquireState = _acquireState.asStateFlow()
    private val _configState = MutableStateFlow<UiState<MediaryScoutConfig>>(UiState.Idle)
    val configState = _configState.asStateFlow()
    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState = _saveState.asStateFlow()
    private val _patrolState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val patrolState = _patrolState.asStateFlow()
    private var refreshJob: Job? = null
    private var selectionJob: Job? = null

    init {
        refresh()
    }

    fun search(query: String, type: String?) {
        val name = query.trim()
        if (name.isBlank() || name.length > 256 ||
            _searchState.value == UiState.Loading || _acquireState.value == UiState.Loading
        ) return
        _acquireState.value = UiState.Idle
        _searchState.value = UiState.Loading
        viewModelScope.launch {
            try {
                _searchState.value = UiState.Success(repository.search(instanceId, name, type))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _searchState.value = UiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun acquire(candidate: MediaryScoutCandidate, season: Int?) {
        val results = (_searchState.value as? UiState.Success)?.data ?: return
        val state = _acquireState.value
        if (candidate !in results.items || state == UiState.Loading || state is UiState.Success) return
        if (candidate.type == "tv" && season != null &&
            (season < 0 || (candidate.seasonNumbers.isNotEmpty() && season !in candidate.seasonNumbers))
        ) return
        _acquireState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.acquire(instanceId, MediaryScoutAcquireRequest(
                    tmdbId = candidate.tmdbId, type = candidate.type,
                    season = season.takeIf { candidate.type == "tv" }
                ))
                _acquireState.value = UiState.Success(result)
                if (result.status in setOf("requested", "reserved", "already_tracked")) refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _acquireState.value = UiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun clearAcquisition() {
        if (_acquireState.value != UiState.Loading) _acquireState.value = UiState.Idle
    }

    fun loadConfig() {
        if (_configState.value == UiState.Loading || _saveState.value == UiState.Loading) return
        _saveState.value = UiState.Idle
        runRequest(_configState) { repository.getConfig(instanceId) }
    }

    fun saveConfig(update: MediaryScoutConfigUpdate) {
        if (_configState.value !is UiState.Success) return
        runRequest(_saveState) {
            _configState.value = UiState.Success(repository.saveConfig(instanceId, update))
        }
    }

    fun runPatrol() {
        runRequest(_patrolState) {
            repository.runPatrol(instanceId)
            refresh()
        }
    }

    private fun <T> runRequest(state: MutableStateFlow<UiState<T>>, request: suspend () -> T) {
        if (state.value == UiState.Loading) return
        state.value = UiState.Loading
        viewModelScope.launch {
            try {
                state.value = UiState.Success(request())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = UiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val data = repository.getDashboard(instanceId)
                servicesRepository.markInstanceReachable(instanceId)
                _uiState.value = UiState.Success(data)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = UiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun selectInstance(id: String, onSelected: (String) -> Unit) {
        if (id == instanceId || selectionJob?.isActive == true) return

        selectionJob = viewModelScope.launch {
            try {
                servicesRepository.setPreferredInstance(ServiceType.MEDIARY_SCOUT, id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = UiState.Error(ErrorHandler.getMessage(context, error))
                return@launch
            }
            onSelected(id)
        }
    }
}
