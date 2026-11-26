package com.homelab.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.LanguageMode
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.ThemeMode
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.AppIconManager
import com.homelab.app.util.AppIconOption
import com.homelab.app.util.ServiceType
import com.homelab.app.work.ServiceAlertScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val localPreferencesRepository: LocalPreferencesRepository,
    private val appIconManager: AppIconManager,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _appIconApplying = MutableStateFlow(false)
    val appIconApplying: StateFlow<Boolean> = _appIconApplying

    val instancesByType: StateFlow<Map<ServiceType, List<ServiceInstance>>> = servicesRepository.instancesByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val preferredInstanceIdByType: StateFlow<Map<ServiceType, String?>> = servicesRepository.preferredInstanceIdByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val themeMode: StateFlow<ThemeMode> = localPreferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val languageMode: StateFlow<LanguageMode> = localPreferencesRepository.languageMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageMode.ENGLISH)

    val hiddenServices: StateFlow<Set<String>> = localPreferencesRepository.hiddenServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val appIcon: StateFlow<AppIconOption> = localPreferencesRepository.appIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppIconOption.DEFAULT)

    val serviceOrder: StateFlow<List<ServiceType>> = localPreferencesRepository.serviceOrder
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ServiceType.entries.filter { it != ServiceType.UNKNOWN }
        )

    val biometricEnabled: StateFlow<Boolean> = localPreferencesRepository.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPinSet: StateFlow<Boolean> = localPreferencesRepository.isPinSet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val backgroundAlertsEnabled: StateFlow<Boolean> = localPreferencesRepository.backgroundAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            localPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            localPreferencesRepository.setLanguageMode(mode)
        }
        // Apply locale change on all API levels
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(mode.code)
        )
    }

    fun setAppIcon(icon: AppIconOption) {
        viewModelScope.launch {
            if (appIcon.value == icon) return@launch
            _appIconApplying.value = true
            localPreferencesRepository.setAppIcon(icon)
            try {
                appIconManager.apply(icon)
            } finally {
                _appIconApplying.value = false
            }
        }
    }

    fun toggleServiceVisibility(type: ServiceType) {
        viewModelScope.launch {
            localPreferencesRepository.toggleServiceVisibility(type.name)
        }
    }

    fun moveService(type: ServiceType, offset: Int) {
        viewModelScope.launch {
            localPreferencesRepository.moveService(type, offset)
        }
    }

    fun deleteInstance(instanceId: String) {
        viewModelScope.launch {
            servicesRepository.disconnectInstance(instanceId)
        }
    }

    fun setPreferredInstance(type: ServiceType, instanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(type, instanceId)
        }
    }

    fun setBackgroundAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            localPreferencesRepository.setBackgroundAlertsEnabled(enabled)
            if (enabled) {
                ServiceAlertScheduler.schedule(appContext)
            } else {
                ServiceAlertScheduler.cancel(appContext)
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            localPreferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun savePin(pin: String) {
        viewModelScope.launch {
            localPreferencesRepository.savePin(pin)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            localPreferencesRepository.verifyPin(pin) is com.homelab.app.data.security.PinVerificationResult.Success
        }
    }

    fun clearSecurity() {
        viewModelScope.launch {
            localPreferencesRepository.clearSecurity()
        }
    }

}
