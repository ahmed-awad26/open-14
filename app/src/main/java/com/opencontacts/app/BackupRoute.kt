package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Backup & restore", style = MaterialTheme.typography.headlineMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::createBackup) { Text("Create local backup") }
                OutlinedButton(onClick = viewModel::restoreLatest) { Text("Restore latest") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::stageGoogleDrive) { Text("Stage to Google Drive") }
                OutlinedButton(onClick = viewModel::stageOneDrive) { Text("Stage to OneDrive") }
            }

            status?.let {
                Card(modifier = Modifier.fillMaxWidth()) { Text(it, modifier = Modifier.padding(16.dp)) }
            }

            if (records.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No backup records yet", style = MaterialTheme.typography.titleMedium)
                        Text("Local encrypted backups are real. Drive/OneDrive adapters currently stage encrypted packages into provider-specific app folders for later auth/sync wiring.")
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(records, key = { it.id }) { record -> BackupRecordCard(record) }
                }
            }
        }
    }
}

@Composable
private fun BackupRecordCard(record: BackupRecordSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.status, style = MaterialTheme.typography.titleMedium)
            Text("Provider: ${record.provider}")
            Text("File: ${record.filePath}")
            Text("Size: ${record.fileSizeBytes} bytes")
            Text("Created: ${formatTimestamp(record.createdAt)}")
        }
    }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val transferRepository: VaultTransferRepository,
) : ViewModel() {
    val records: StateFlow<List<BackupRecordSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) flowOf(emptyList()) else transferRepository.observeBackupRecords(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun createBackup() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val result = transferRepository.createLocalBackup(vaultId)
            _status.value = "Backup created: ${result.filePath}"
        }
    }

    fun restoreLatest() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val restored = transferRepository.restoreLatestLocalBackup(vaultId)
            _status.value = if (restored) "Latest backup restored into active vault" else "No backup file found for active vault"
        }
    }

    fun stageGoogleDrive() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val result = transferRepository.stageLatestBackupToGoogleDrive(vaultId)
            _status.value = "Staged for Google Drive: ${result.filePath}"
        }
    }

    fun stageOneDrive() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            val result = transferRepository.stageLatestBackupToOneDrive(vaultId)
            _status.value = "Staged for OneDrive: ${result.filePath}"
        }
    }
}

private fun formatTimestamp(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))
