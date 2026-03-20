package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceRoute(
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = CardDefaults.elevatedShape) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Groups & folders", style = MaterialTheme.typography.headlineMedium)
                    Text("Create, remove, and reuse vault-local tags and folders. Assign them while editing contacts.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkspaceStat("Tags", tags.size.toString())
                        WorkspaceStat("Folders", folders.size.toString())
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Text("Folders", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = { newFolder = "" }) { Icon(Icons.Default.Add, contentDescription = "Add folder") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.ifEmpty { listOf(FolderSummary("Personal"), FolderSummary("Work"), FolderSummary("Medical")) }.forEach { folder ->
                            AssistChip(
                                onClick = {},
                                label = { Text(folder.name) },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.deleteFolder(folder.name) }) { Icon(Icons.Default.Delete, contentDescription = "Delete folder") }
                                },
                            )
                        }
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Label, contentDescription = null)
                            Text("Tags", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = { newTag = "" }) { Icon(Icons.Default.Add, contentDescription = "Add tag") }
                    }
                }
                items(tags, key = { it.name }) { tag ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                                Text("Color: ${tag.colorToken}")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Used ${tag.usageCount} times", style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { viewModel.deleteTag(tag.name) }) { Icon(Icons.Default.Delete, contentDescription = "Delete tag") }
                            }
                        }
                    }
                }
            }
        }
    }

    newTag?.let { value ->
        NameDialog(
            title = "New tag",
            value = value,
            label = "Tag name",
            onValueChange = { newTag = it },
            onDismiss = { newTag = null },
            onConfirm = {
                if (!value.isBlank()) viewModel.saveTag(value)
                newTag = null
            },
        )
    }
    newFolder?.let { value ->
        NameDialog(
            title = "New folder",
            value = value,
            label = "Folder name",
            onValueChange = { newFolder = it },
            onDismiss = { newFolder = null },
            onConfirm = {
                if (!value.isBlank()) viewModel.saveFolder(value)
                newFolder = null
            },
        )
    }
}

@Composable
private fun WorkspaceStat(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = CardDefaults.shape) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NameDialog(title: String, value: String, label: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    val tags = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeTags(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeFolders(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertTag(vaultId, TagSummary(name = name.trim())) }
    }

    fun deleteTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteTag(vaultId, name) }
    }

    fun saveFolder(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertFolder(vaultId, FolderSummary(name = name.trim())) }
    }

    fun deleteFolder(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteFolder(vaultId, name) }
    }
}
