package com.opencontacts.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun ImportExportRoute(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsvFromUri(context, uri)
    }
    val vcfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importVcfFromUri(context, uri)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Import / Export", style = MaterialTheme.typography.headlineMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::exportJson) { Text("Export JSON") }
                Button(onClick = viewModel::exportCsv) { Text("Export CSV") }
                Button(onClick = viewModel::exportVcf) { Text("Export VCF") }
                Button(onClick = viewModel::exportExcel) { Text("Export Excel") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }) { Text("Import CSV file") }
                Button(onClick = { vcfPicker.launch(arrayOf("text/x-vcard", "text/vcard", "*/*")) }) { Text("Import VCF file") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::importFromPhone) { Text("Import all phone contacts") }
                OutlinedButton(onClick = viewModel::exportToPhone) { Text("Export vault to phone") }
            }

            status?.let {
                Card(modifier = Modifier.fillMaxWidth()) { Text(it, modifier = Modifier.padding(16.dp)) }
            }

            if (history.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No import/export records yet", style = MaterialTheme.typography.titleMedium)
                        Text("Use the file picker to import CSV or VCF directly from device storage or Google Drive documents.")
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.operationType, style = MaterialTheme.typography.titleMedium)
                                Text(item.status)
                                Text("Items: ${item.itemCount}")
                                Text(item.filePath)
                                Text(formatTimestamp(item.createdAt))
                            }
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val transferRepository: VaultTransferRepository,
) : ViewModel() {
    val history: StateFlow<List<ImportExportHistorySummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) flowOf(emptyList()) else transferRepository.observeImportExportHistory(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private fun launchMessage(block: suspend () -> ImportExportHistorySummary, success: (ImportExportHistorySummary) -> String) {
        viewModelScope.launch {
            val result = block()
            _status.value = success(result)
        }
    }

    private fun activeVaultId(): String? = vaultSessionManager.activeVaultId.value

    fun exportJson() { activeVaultId()?.let { launchMessage({ transferRepository.exportContactsJson(it) }) { "Exported JSON: ${it.filePath}" } } }
    fun exportCsv() { activeVaultId()?.let { launchMessage({ transferRepository.exportContactsCsv(it) }) { "Exported CSV: ${it.filePath}" } } }
    fun exportVcf() { activeVaultId()?.let { launchMessage({ transferRepository.exportContactsVcf(it) }) { "Exported VCF: ${it.filePath}" } } }
    fun exportExcel() { activeVaultId()?.let { launchMessage({ transferRepository.exportContactsExcel(it) }) { "Exported Excel: ${it.filePath}" } } }
    fun importFromPhone() { activeVaultId()?.let { launchMessage({ transferRepository.importFromPhoneContacts(it) }) { "Imported from phone: ${it.itemCount} item(s)" } } }
    fun exportToPhone() { activeVaultId()?.let { launchMessage({ transferRepository.exportAllContactsToPhone(it) }) { "Exported to phone: ${it.itemCount} item(s)" } } }

    fun importCsvFromUri(context: Context, uri: Uri) {
        activeVaultId()?.let { vaultId ->
            viewModelScope.launch {
                copyUriIntoImports(context, uri, "contacts.csv")
                val result = transferRepository.importLatestContactsCsv(vaultId)
                _status.value = "${result.status}: ${result.itemCount} item(s)"
            }
        }
    }

    fun importVcfFromUri(context: Context, uri: Uri) {
        activeVaultId()?.let { vaultId ->
            viewModelScope.launch {
                copyUriIntoImports(context, uri, "contacts.vcf")
                val result = transferRepository.importLatestContactsVcf(vaultId)
                _status.value = "${result.status}: ${result.itemCount} item(s)"
            }
        }
    }

    private fun copyUriIntoImports(context: Context, uri: Uri, targetName: String) {
        val targetDir = File(context.filesDir, "vault_imports").apply { mkdirs() }
        val outFile = File(targetDir, targetName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

private fun formatTimestamp(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))
