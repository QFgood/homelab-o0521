package com.homelab.app.ui.mediaryscout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private var refreshJob: Job? = null
    private var selectionJob: Job? = null

    init {
        refresh()
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
