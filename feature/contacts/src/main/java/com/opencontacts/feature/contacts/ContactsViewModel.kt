package com.opencontacts.feature.contacts

import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    val contacts: StateFlow<List<ContactSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val _editingContact = MutableStateFlow<ContactEditorState?>(null)
    val editingContact: StateFlow<ContactEditorState?> = _editingContact

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs

    fun refreshCallLogs() {
        viewModelScope.launch {
            _callLogs.value = queryCallLogs()
        }
    }

    private fun queryCallLogs(): List<CallLogItem> {
        val resolver = context.contentResolver
        val cursor = runCatching {
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 100"
            )
        }.getOrNull() ?: return emptyList()
        cursor.use {
            val items = mutableListOf<CallLogItem>()
            val idCol = it.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numCol = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameCol = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeCol = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durCol = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (it.moveToNext()) {
                items += CallLogItem(
                    id = it.getString(idCol),
                    number = it.getString(numCol).orEmpty(),
                    cachedName = it.getString(nameCol),
                    type = when (it.getInt(typeCol)) {
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                        else -> "Incoming"
                    },
                    timestamp = it.getLong(dateCol),
                    durationSeconds = it.getLong(durCol),
                )
            }
            return items
        }
    }

    fun startCreate() {
        _editingContact.value = ContactEditorState()
    }

    fun startEdit(contact: ContactSummary) {
        _editingContact.value = ContactEditorState(
            id = contact.id,
            displayName = contact.displayName,
            phone = contact.primaryPhone.orEmpty(),
            tags = contact.tags.joinToString(", "),
            isFavorite = contact.isFavorite,
            folderName = contact.folderName.orEmpty(),
        )
    }

    fun updateEditor(state: ContactEditorState) {
        _editingContact.value = state
    }

    fun dismissEditor() {
        _editingContact.value = null
    }

    fun saveEditor() {
        val editor = _editingContact.value ?: return
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId = vaultId,
                draft = ContactDraft(
                    id = editor.id,
                    displayName = editor.displayName.ifBlank { "Unnamed contact" },
                    primaryPhone = editor.phone.ifBlank { null },
                    tags = editor.tags.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) },
                    isFavorite = editor.isFavorite,
                    folderName = editor.folderName.ifBlank { null },
                )
            )
            _editingContact.value = null
        }
    }

    fun delete(contactId: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.deleteContact(vaultId, contactId)
        }
    }

    fun deleteMany(contactIds: Collection<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactIds.forEach { contactRepository.deleteContact(vaultId, it) }
        }
    }
}

data class ContactEditorState(
    val id: String? = null,
    val displayName: String = "",
    val phone: String = "",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val folderName: String = "",
)

data class CallLogItem(
    val id: String,
    val number: String,
    val cachedName: String?,
    val type: String,
    val timestamp: Long,
    val durationSeconds: Long,
)
