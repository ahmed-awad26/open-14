package com.opencontacts.feature.contacts

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ContactDetails
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactDetailsRoute(
    onBack: () -> Unit,
    viewModel: ContactDetailsViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val noteEditor by viewModel.noteEditor.collectAsStateWithLifecycle()
    val reminderEditor by viewModel.reminderEditor.collectAsStateWithLifecycle()
    val contactEditor by viewModel.contactEditor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share as text") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                details?.let { shareAsText(context, it.contact) }
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share as file") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                details?.let { shareAsVcfFile(context, it.contact) }
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share as QR") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                details?.let { shareAsText(context, it.contact) }
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                details?.let { viewModel.startEdit(it.contact) }
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                viewModel.deleteContact()
                                expanded = false
                                onBack()
                            },
                        )
                    }
                }
            }

            if (details == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Contact not found or vault is locked",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            } else {
                ContactDetailsContent(
                    details = details!!,
                    onCall = {
                        details!!.contact.primaryPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                            val hasCallPermission =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CALL_PHONE,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            val intent =
                                if (hasCallPermission) {
                                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                                } else {
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                }

                            context.startActivity(intent)
                        }
                    },
                    onOpenWhatsApp = {
                        details!!.contact.primaryPhone?.let { openWhatsApp(context, it) }
                    },
                    onOpenTelegram = {
                        details!!.contact.primaryPhone?.let { openTelegram(context, it) }
                    },
                    onAddNote = viewModel::startAddNote,
                    onAddReminder = viewModel::startAddReminder,
                    onToggleReminder = viewModel::toggleReminder,
                )
            }
        }

        noteEditor?.let { state ->
            SimpleTextDialog(
                title = "Add note",
                value = state,
                label = "Encrypted note",
                onValueChange = viewModel::updateNoteEditor,
                onDismiss = viewModel::dismissNoteEditor,
                onConfirm = viewModel::saveNote,
            )
        }

        reminderEditor?.let { state ->
            ReminderEditorDialog(
                state = state,
                onStateChange = viewModel::updateReminderEditor,
                onDismiss = viewModel::dismissReminderEditor,
                onConfirm = viewModel::saveReminder,
            )
        }

        contactEditor?.let { state ->
            ContactEditorDialog(
                state = state,
                onStateChange = viewModel::updateContactEditor,
                onDismiss = viewModel::dismissContactEditor,
                onConfirm = viewModel::saveContactEditor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDetailsContent(
    details: ContactDetails,
    onCall: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenTelegram: () -> Unit,
    onAddNote: () -> Unit,
    onAddReminder: () -> Unit,
    onToggleReminder: (String, Boolean) -> Unit,
) {
    val contact = details.contact

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CardDefaults.elevatedShape,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    contact.displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                if (contact.isFavorite) {
                                    Icon(Icons.Default.Star, contentDescription = null)
                                }
                            }

                            Text(
                                contact.primaryPhone ?: "No primary phone",
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            contact.folderName?.let {
                                AssistChip(onClick = {}, label = { Text(it) })
                            }
                        }

                        FilledTonalButton(
                            onClick = onCall,
                            enabled = !contact.primaryPhone.isNullOrBlank(),
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null)
                            Text("Call")
                        }
                    }

                    if (contact.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            contact.tags.forEach { tag ->
                                AssistChip(onClick = {}, label = { Text(tag) })
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = onOpenWhatsApp, label = { Text("WhatsApp") })
                        AssistChip(onClick = onOpenTelegram, label = { Text("Telegram") })
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Notes",
                actionLabel = "Add note",
                onAction = onAddNote,
                icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
            )
        }

        if (details.notes.isEmpty()) {
            item {
                EmptyCard(
                    "No notes yet",
                    "Capture protected follow-up notes and context for this contact.",
                )
            }
        } else {
            items(details.notes, key = { it.id }) { note ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(note.body, style = MaterialTheme.typography.bodyLarge)
                        Text(formatTime(note.createdAt), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Reminders",
                actionLabel = "Add reminder",
                onAction = onAddReminder,
                icon = { Icon(Icons.Default.Alarm, null) },
            )
        }

        if (details.reminders.isEmpty()) {
            item {
                EmptyCard(
                    "No reminders",
                    "Create follow-up reminders directly inside the active vault.",
                )
            }
        } else {
            items(details.reminders, key = { it.id }) { reminder ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Schedule, null)
                                Text(formatTime(reminder.dueAt))
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = reminder.isDone,
                                onCheckedChange = { onToggleReminder(reminder.id, it) },
                            )
                            Text(if (reminder.isDone) "Done" else "Open")
                        }
                    }
                }
            }
        }

        item {
            Text("Timeline", style = MaterialTheme.typography.titleLarge)
        }

        if (details.timeline.isEmpty()) {
            item {
                EmptyCard(
                    "No activity yet",
                    "The timeline will show notes, reminders, edits, and future bridge events.",
                )
            }
        } else {
            items(details.timeline, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        item.subtitle?.let { Text(it) }
                        Text(
                            "${item.type} • ${formatTime(item.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle)
        }
    }
}

@Composable
private fun SimpleTextDialog(
    title: String,
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReminderEditorDialog(
    state: ReminderEditorState,
    onStateChange: (ReminderEditorState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onStateChange(state.copy(title = it)) },
                    label = { Text("Reminder title") },
                )
                OutlinedTextField(
                    value = state.daysAhead,
                    onValueChange = {
                        onStateChange(state.copy(daysAhead = it.filter(Char::isDigit)))
                    },
                    label = { Text("Due in days") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContactEditorDialog(
    state: ContactEditorState,
    onStateChange: (ContactEditorState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id == null) "Add contact" else "Edit contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { onStateChange(state.copy(displayName = it)) },
                    label = { Text("Display name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = { onStateChange(state.copy(phone = it)) },
                    label = { Text("Phone") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.folderName,
                    onValueChange = { onStateChange(state.copy(folderName = it)) },
                    label = { Text("Folder") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.tags,
                    onValueChange = { onStateChange(state.copy(tags = it)) },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.isFavorite,
                        onCheckedChange = { onStateChange(state.copy(isFavorite = it)) },
                    )
                    Text("Favorite")
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTime(value: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(value))

private fun shareAsText(
    context: android.content.Context,
    contact: ContactSummary,
) {
    val payload = buildString {
        append(contact.displayName)
        contact.primaryPhone
            ?.takeIf(String::isNotBlank)
            ?.let { append("\n$it") }
    }

    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload)
            },
            "Share contact",
        )
    )
}

private fun shareAsVcfFile(
    context: android.content.Context,
    contact: ContactSummary,
) {
    val payload = buildString {
        append("BEGIN:VCARD\n")
        append("VERSION:3.0\n")
        append("FN:${contact.displayName}\n")
        contact.primaryPhone
            ?.takeIf(String::isNotBlank)
            ?.let { append("TEL:$it\n") }
        append("END:VCARD")
    }

    val file = File(context.cacheDir, "contact_${System.currentTimeMillis()}.vcf")
    file.writeText(payload)

    val uri = FileProvider.getUriForFile(
        context,
        "com.opencontacts.app.fileprovider",
        file,
    )

    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share contact file",
        )
    )
}

private fun openWhatsApp(context: android.content.Context, phone: String) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/${digits.filter { it.isDigit() }}"),
        )
    )
}

private fun openTelegram(context: android.content.Context, phone: String) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    val filteredDigits = digits.filter { it.isDigit() }

    val tgIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("tg://resolve?phone=$filteredDigits"),
    )
    val fallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://t.me/+${filteredDigits}"),
    )

    runCatching { context.startActivity(tgIntent) }
        .getOrElse { context.startActivity(fallback) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle.get<String>("contactId"))

    val details: StateFlow<ContactDetails?> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked ->
            vaultId to isLocked
        }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) {
                flowOf(null)
            } else {
                contactRepository.observeContactDetails(vaultId, contactId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _noteEditor = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val noteEditor: StateFlow<String?> = _noteEditor

    private val _reminderEditor =
        kotlinx.coroutines.flow.MutableStateFlow<ReminderEditorState?>(null)
    val reminderEditor: StateFlow<ReminderEditorState?> = _reminderEditor

    private val _contactEditor =
        kotlinx.coroutines.flow.MutableStateFlow<ContactEditorState?>(null)
    val contactEditor: StateFlow<ContactEditorState?> = _contactEditor

    fun startAddNote() {
        _noteEditor.value = ""
    }

    fun updateNoteEditor(value: String) {
        _noteEditor.value = value
    }

    fun dismissNoteEditor() {
        _noteEditor.value = null
    }

    fun saveNote() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val note = _noteEditor.value ?: return

        viewModelScope.launch {
            contactRepository.addNote(vaultId, contactId, note)
            _noteEditor.value = null
        }
    }

    fun startAddReminder() {
        _reminderEditor.value = ReminderEditorState()
    }

    fun updateReminderEditor(state: ReminderEditorState) {
        _reminderEditor.value = state
    }

    fun dismissReminderEditor() {
        _reminderEditor.value = null
    }

    fun saveReminder() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val state = _reminderEditor.value ?: return
        val days = state.daysAhead.toLongOrNull() ?: 1L
        val dueAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L

        viewModelScope.launch {
            contactRepository.addReminder(vaultId, contactId, state.title, dueAt)
            _reminderEditor.value = null
        }
    }

    fun toggleReminder(reminderId: String, done: Boolean) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.setReminderDone(vaultId, reminderId, done)
        }
    }

    fun startEdit(contact: ContactSummary) {
        _contactEditor.value = ContactEditorState(
            id = contact.id,
            displayName = contact.displayName,
            phone = contact.primaryPhone.orEmpty(),
            tags = contact.tags.joinToString(", "),
            isFavorite = contact.isFavorite,
            folderName = contact.folderName.orEmpty(),
        )
    }

    fun updateContactEditor(state: ContactEditorState) {
        _contactEditor.value = state
    }

    fun dismissContactEditor() {
        _contactEditor.value = null
    }

    fun saveContactEditor() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val editor = _contactEditor.value ?: return

        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = editor.id,
                    displayName = editor.displayName.ifBlank { "Unnamed contact" },
                    primaryPhone = editor.phone.ifBlank { null },
                    tags = editor.tags
                        .split(',')
                        .mapNotNull { it.trim().takeIf(String::isNotBlank) },
                    isFavorite = editor.isFavorite,
                    folderName = editor.folderName.ifBlank { null },
                )
            )
            _contactEditor.value = null
        }
    }

    fun deleteContact() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.deleteContact(vaultId, contactId)
        }
    }
}

data class ReminderEditorState(
    val title: String = "",
    val daysAhead: String = "1",
)
