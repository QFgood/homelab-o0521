package com.homelab.app.ui.proxmox
import com.homelab.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxConfigScreen(
    node: String,
    vmid: Int,
    isQemu: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val guestConfigState by viewModel.guestConfigState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var cores by remember { mutableStateOf("") }
    var memory by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(node, vmid, isQemu) {
        viewModel.fetchGuestConfig(node, vmid, isQemu)
    }

    val nameKey = if (isQemu) "name" else "hostname"
    val nameLabel = stringResource(if (isQemu) R.string.proxmox_name else R.string.proxmox_hostname)
    val nameHelp = stringResource(
        if (isQemu) R.string.proxmox_guest_name_help else R.string.proxmox_container_hostname_help
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_edit_config_guest_title, if (isQemu) "VM" else "CT", vmid)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSaving = true
                            saveError = null
                            val configMap = mutableMapOf<String, String>()
                            if (name.isNotBlank()) configMap[nameKey] = name.trim()
                            if (cores.isNotBlank()) configMap["cores"] = cores.trim()
                            if (memory.isNotBlank()) configMap["memory"] = memory.trim()
                            viewModel.updateGuestConfig(
                                node = node,
                                vmid = vmid,
                                isQemu = isQemu,
                                config = configMap,
                                onSuccess = { onNavigateBack() }
                            )
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = guestConfigState) {
                is UiState.Idle,
                is UiState.Loading,
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.proxmox_loading_config), color = Color.Gray)
                        }
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.proxmox_error_message, state.message), color = Color.Red)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchGuestConfig(node, vmid, isQemu) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val rawConfig = state.data

                    // Populate fields from raw config on first load
                    LaunchedEffect(rawConfig) {
                        name = rawConfig[nameKey] ?: ""
                        cores = rawConfig["cores"] ?: ""
                        memory = rawConfig["memory"]?.let { mStr ->
                            runCatching { mStr.toInt().toString() }.getOrNull() ?: mStr
                        } ?: ""
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Name field
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(nameLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text(nameHelp) }
                        )

                        Divider()

                        // CPU field
                        Text(
                            stringResource(R.string.proxmox_cpu),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = cores,
                            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) cores = it },
                            label = { Text(stringResource(R.string.proxmox_cpu_cores_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.proxmox_cpu_cores_help)) }
                        )

                        Divider()

                        // Memory field
                        Text(
                            stringResource(R.string.proxmox_memory_section),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = memory,
                            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) memory = it },
                            label = { Text(stringResource(R.string.proxmox_memory_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.proxmox_memory_help)) }
                        )

                        Spacer(Modifier.height(32.dp))

                        // Save button at bottom
                        Button(
                            onClick = {
                                isSaving = true
                                saveError = null
                                val configMap = mutableMapOf<String, String>()
                                if (name.isNotBlank()) configMap[nameKey] = name.trim()
                                if (cores.isNotBlank()) configMap["cores"] = cores.trim()
                                if (memory.isNotBlank()) configMap["memory"] = memory.trim()
                                viewModel.updateGuestConfig(
                                    node = node,
                                    vmid = vmid,
                                    isQemu = isQemu,
                                    config = configMap,
                                    onSuccess = { onNavigateBack() }
                                )
                            },
                            enabled = !isSaving && (name.isNotBlank() || cores.isNotBlank() || memory.isNotBlank()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.proxmox_save_changes))
                        }

                        // Error display
                        saveError?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Red.copy(alpha = 0.1f)
                                )
                            ) {
                                Text(
                                    error,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Observe config state changes for error handling after save
    LaunchedEffect(guestConfigState) {
        if (guestConfigState is UiState.Error) {
            isSaving = false
            saveError = (guestConfigState as UiState.Error).message
        }
    }
}
