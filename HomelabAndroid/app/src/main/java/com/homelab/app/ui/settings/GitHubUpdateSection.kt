package com.homelab.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homelab.app.BuildConfig
import com.homelab.app.R
import com.homelab.app.data.update.GitHubUpdate
import com.homelab.app.data.update.GitHubUpdateException
import com.homelab.app.data.update.GitHubUpdates
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Checked(val update: GitHubUpdate) : AppUpdateState
    data object Downloading : AppUpdateState
    data class Ready(val file: File) : AppUpdateState
    data class Error(@StringRes val message: Int) : AppUpdateState
}

@HiltViewModel
class GitHubUpdateViewModel @Inject constructor(private val updates: GitHubUpdates) : ViewModel() {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state = _state.asStateFlow()

    fun check() = runUpdate(AppUpdateState.Checking) { AppUpdateState.Checked(updates.check()) }

    fun download(update: GitHubUpdate.Available) =
        runUpdate(AppUpdateState.Downloading) { AppUpdateState.Ready(updates.download(update)) }

    fun installerFailed() {
        _state.value = AppUpdateState.Error(R.string.github_update_install_error)
    }

    private fun runUpdate(busy: AppUpdateState, block: suspend () -> AppUpdateState) {
        if (_state.value == AppUpdateState.Checking || _state.value == AppUpdateState.Downloading) return
        _state.value = busy
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: GitHubUpdateException) {
                AppUpdateState.Error(error.messageRes)
            } catch (_: Exception) {
                AppUpdateState.Error(R.string.github_update_error)
            }
        }
    }
}

@Composable
fun GitHubUpdateSection(viewModel: GitHubUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.github_update_title), style = MaterialTheme.typography.titleMedium)
            Text(BuildConfig.GITHUB_UPDATE_REPOSITORY, style = MaterialTheme.typography.bodySmall)
            when (val current = state) {
                AppUpdateState.Idle -> Unit
                AppUpdateState.Checking -> Text(stringResource(R.string.github_update_checking))
                AppUpdateState.Downloading -> Text(stringResource(R.string.github_update_downloading))
                is AppUpdateState.Error -> Text(stringResource(current.message), color = MaterialTheme.colorScheme.error)
                is AppUpdateState.Checked -> when (val update = current.update) {
                    GitHubUpdate.UpToDate -> Text(stringResource(R.string.github_update_current))
                    GitHubUpdate.NoRelease -> Text(stringResource(R.string.github_update_no_release))
                    is GitHubUpdate.Available -> {
                        Text(stringResource(R.string.github_update_found, update.tag))
                        Button(onClick = { viewModel.download(update) }) {
                            Text(stringResource(R.string.github_update_download))
                        }
                    }
                }
                is AppUpdateState.Ready -> {
                    Button(onClick = {
                        try {
                            if (!context.packageManager.canRequestPackageInstalls()) {
                                context.startActivity(Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")
                                ))
                            } else {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", current.file)
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            }
                        } catch (_: Exception) {
                            viewModel.installerFailed()
                        }
                    }) {
                        Text(stringResource(R.string.github_update_install))
                    }
                    Text(stringResource(R.string.github_update_install_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = viewModel::check,
                enabled = state != AppUpdateState.Checking && state != AppUpdateState.Downloading
            ) {
                Text(stringResource(R.string.github_update_check))
            }
        }
    }
}
