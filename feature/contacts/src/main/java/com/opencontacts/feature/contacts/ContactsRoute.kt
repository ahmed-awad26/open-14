package com.opencontacts.feature.contacts

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.VaultSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SortMode { NAME, FAVORITES, FOLDER }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsRoute(
    activeVaultName: String = "Active vault",
    vaults: List<VaultSummary> = emptyList(),
    onOpenDetails: (String) -> Unit,
    onOpenWorkspace: (() -> Unit)? = null,
    onOpenImportExport: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    onOpenBackup: (() -> Unit)? = null,
    onOpenTrash: (() -> Unit)? = null,
    onOpenVaults: (() -> Unit)? = null,
    onSwitchVault: ((String) -> Unit)? = null,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val editing by viewModel.editingContact.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var permissionPromptDismissed by rememberSaveable { mutableStateOf(false) }
    var dialPadVisible by remember { mutableStateOf(false) }
    var dialNumber by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.READ_CALL_LOG] == true) viewModel.refreshCallLogs()
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty() && !permissionPromptDismissed) permissionLauncher.launch(missing.toTypedArray()) else viewModel.refreshCallLogs()
    }

    val filteredContacts = remember(contacts, favoritesOnly, sortMode) {
        contacts.filter { contact -> !favoritesOnly || contact.isFavorite }
            .sortedWith(
                when (sortMode) {
                    SortMode.NAME -> compareBy { it.displayName.lowercase() }
                    SortMode.FAVORITES -> compareByDescending<ContactSummary> { it.isFavorite }.thenBy { it.displayName.lowercase() }
                    SortMode.FOLDER -> compareBy<ContactSummary> { it.folderName.orEmpty().lowercase() }.thenBy { it.displayName.lowercase() }
                }
            )
    }

    val selectedContacts = remember(selectedIds, contacts) { contacts.filter { it.id in selectedIds } }

    fun openDial(phone: String?) {
        val raw = phone?.trim().orEmpty()
        if (raw.isBlank()) return
        val uri = Uri.parse("tel:$raw")
        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val intent = if (hasCallPermission) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
        context.startActivity(intent)
    }

    fun shareSelectedAsText() {
        if (selectedContacts.isEmpty()) return
        val payload = selectedContacts.joinToString("\n\n") { contact ->
            buildString {
                append(contact.displayName)
                contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("\n$it") }
                if (contact.tags.isNotEmpty()) append("\nTags: ${contact.tags.joinToString()}")
                contact.folderName?.let { append("\nFolder: $it") }
            }
        }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload)
                },
                "Share contact(s)",
            )
        )
    }

    fun shareQr(contact: ContactSummary) {
        qrPayload = buildString {
            append("BEGIN:VCARD\n")
            append("VERSION:3.0\n")
            append("FN:${contact.displayName}\n")
            contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("TEL:$it\n") }
            if (contact.tags.isNotEmpty()) append("NOTE:Tags=${contact.tags.joinToString()}\n")
            contact.folderName?.let { append("ORG:$it\n") }
            append("END:VCARD")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Vaults", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                vaults.forEach { vault ->
                    DrawerItem(
                        title = vault.displayName,
                        subtitle = if (vault.displayName == activeVaultName) "Current vault" else if (vault.isLocked) "Locked" else "Switch vault",
                        icon = Icons.Default.Folder,
                        selected = vault.displayName == activeVaultName,
                    ) {
                        onSwitchVault?.invoke(vault.id)
                        scope.launch { drawerState.close() }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DrawerItem("Add / change vault", "Create or rename vaults", Icons.Default.Add) { onOpenVaults?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Groups", "Tags & folders in current vault", Icons.Default.Groups) { onOpenWorkspace?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Import / Export", "CSV, VCF, Excel", Icons.Default.Sync) { onOpenImportExport?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Backup", "Local and Drive staging", Icons.Default.Storage) { onOpenBackup?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Trash", "Restore or permanently delete", Icons.Default.DeleteSweep) { onOpenTrash?.invoke(); scope.launch { drawerState.close() } }
                DrawerItem("Settings", "Security, theme, retention", Icons.Default.Settings) { onOpenSecurity?.invoke(); scope.launch { drawerState.close() } }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(26.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeAction("Search", Icons.Default.Search) { onOpenSearch?.invoke() }
                        HomeAction("Groups", Icons.Default.Groups) { onOpenWorkspace?.invoke() }
                        HomeAction("Add", Icons.Default.Add) { viewModel.startCreate() }
                        HomeAction("Dial", Icons.Default.Dialpad) { dialPadVisible = true }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                        Box(modifier = Modifier.weight(1f)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = false, onClick = { onOpenSearch?.invoke() }, label = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Favorite") }, leadingIcon = { Icon(Icons.Default.Star, null) })
                                FilterChip(selected = sortMode == SortMode.NAME, onClick = { sortMode = SortMode.NAME }, label = { Text("A–Z") })
                                FilterChip(selected = sortMode == SortMode.FOLDER, onClick = { sortMode = SortMode.FOLDER }, label = { Text("Folders") }, leadingIcon = { Icon(Icons.Default.Folder, null) })
                            }
                        }
                    }
                }

                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Contacts") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; viewModel.refreshCallLogs() }, text = { Text("Call log") })
                }

                if (selectedTab == 0) {
                    if (selectedIds.isNotEmpty()) {
                        SelectionBar(
                            count = selectedIds.size,
                            allSelected = selectedIds.size == filteredContacts.size && filteredContacts.isNotEmpty(),
                            onSelectAll = { selectedIds = if (selectedIds.size == filteredContacts.size) emptySet() else filteredContacts.map { it.id }.toSet() },
                            onShare = ::shareSelectedAsText,
                            onDelete = { viewModel.deleteMany(selectedIds); selectedIds = emptySet() },
                            onEdit = { selectedContacts.singleOrNull()?.let(viewModel::startEdit) },
                            onQr = { selectedContacts.singleOrNull()?.let(::shareQr) },
                            qrEnabled = selectedIds.size == 1,
                            editEnabled = selectedIds.size == 1,
                        )
                    }
                    if (filteredContacts.isEmpty()) {
                        EmptyStateCard("No contacts yet", "Add a private contact, import a CSV or VCF file, or bring in all your phone contacts.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredContacts, key = { it.id }) { contact ->
                                val selected = contact.id in selectedIds
                                ContactModernCard(
                                    contact = contact,
                                    selected = selected,
                                    selectionMode = selectedIds.isNotEmpty(),
                                    onToggleSelection = { selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id },
                                    onLongPress = { selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id },
                                    onOpen = {
                                        if (selectedIds.isNotEmpty()) selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id
                                        else onOpenDetails(contact.id)
                                    },
                                    onCall = { openDial(contact.primaryPhone) },
                                )
                            }
                            item { Spacer(Modifier.height(84.dp)) }
                        }
                    }
                } else {
                    if (callLogs.isEmpty()) {
                        EmptyStateCard("No recent calls", "Grant call-log permission to show recent incoming, outgoing and missed calls.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(callLogs, key = { it.id }) { item -> CallLogCard(item = item, onDial = { openDial(item.number) }) }
                            item { Spacer(Modifier.height(84.dp)) }
                        }
                    }
                }
            }

            editing?.let { editor ->
                ContactEditorDialog(
                    state = editor,
                    onStateChange = viewModel::updateEditor,
                    onDismiss = viewModel::dismissEditor,
                    onConfirm = viewModel::saveEditor,
                )
            }
            qrPayload?.let { payload -> QrCodeDialog(payload = payload, onDismiss = { qrPayload = null }) }
            if (dialPadVisible) {
                DialPadBottomSheet(
                    number = dialNumber,
                    onNumberChange = { dialNumber = it },
                    onDismiss = { dialPadVisible = false },
                    onCall = { openDial(dialNumber) },
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(title: String, subtitle: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun HomeAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.combinedClickable(onClick = onClick)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Icon(icon, contentDescription = label, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    qrEnabled: Boolean,
    editEnabled: Boolean,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSelectAll) { Icon(if (allSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, contentDescription = "Select all") }
                IconButton(onClick = onEdit, enabled = editEnabled) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
                IconButton(onClick = onQr, enabled = qrEnabled) { Icon(Icons.Default.QrCode2, contentDescription = "QR") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@Composable
private fun ContactModernCard(contact: ContactSummary, selected: Boolean, selectionMode: Boolean, onToggleSelection: () -> Unit, onLongPress: () -> Unit, onOpen: () -> Unit, onCall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                    if (selectionMode) { Checkbox(checked = selected, onCheckedChange = { onToggleSelection() }); Spacer(Modifier.width(8.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(contact.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (contact.isFavorite) Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(contact.primaryPhone ?: "No phone number", style = MaterialTheme.typography.bodyLarge)
                        contact.folderName?.let { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.Folder, null) }) }
                    }
                }
                IconButton(onClick = onCall, enabled = !contact.primaryPhone.isNullOrBlank()) { Icon(Icons.Default.Phone, contentDescription = "Call") }
            }
            if (contact.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contact.tags.take(8).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) } }
            }
        }
    }
}

@Composable
private fun CallLogCard(item: CallLogItem, onDial: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.cachedName ?: item.number, style = MaterialTheme.typography.titleMedium)
                Text("${item.type} • ${formatTime(item.timestamp)}", style = MaterialTheme.typography.bodyMedium)
                if (item.durationSeconds > 0) Text("${item.durationSeconds}s", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDial) { Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = "Dial") }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle) } }
}

@Composable
private fun ContactEditorDialog(state: ContactEditorState, onStateChange: (ContactEditorState) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (state.id == null) "Add contact" else "Edit contact") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedTextField(value = state.displayName, onValueChange = { onStateChange(state.copy(displayName = it)) }, label = { Text("Display name") }, singleLine = true)
            androidx.compose.material3.OutlinedTextField(value = state.phone, onValueChange = { onStateChange(state.copy(phone = it)) }, label = { Text("Phone") }, singleLine = true)
            androidx.compose.material3.OutlinedTextField(value = state.folderName, onValueChange = { onStateChange(state.copy(folderName = it)) }, label = { Text("Folder") }, singleLine = true)
            androidx.compose.material3.OutlinedTextField(value = state.tags, onValueChange = { onStateChange(state.copy(tags = it)) }, label = { Text("Tags (comma separated)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = state.isFavorite, onCheckedChange = { onStateChange(state.copy(isFavorite = it)) }); Text("Favorite") }
        }
    }, confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialPadBottomSheet(number: String, onNumberChange: (String) -> Unit, onDismiss: () -> Unit, onCall: () -> Unit) {
    val keys = listOf("1","2","3","4","5","6","7","8","9","*","0","#")
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(number.ifBlank { "Enter number" }, style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                keys.forEach { key ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(84.dp).combinedClickable { onNumberChange(number + key) }) {
                        Box(contentAlignment = Alignment.Center) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (number.isNotEmpty()) onNumberChange(number.dropLast(1)) }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Close") }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    IconButton(onClick = onCall) { Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QrCodeDialog(payload: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(payload) { generateQrBitmap(payload, 720) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Share as QR") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(220.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(12.dp)) }
            Text("Scan to import contact data quickly.")
        }
    }, confirmButton = { TextButton(onClick = { bitmap?.let { shareBitmap(context, it) } }) { Text("Share image") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

private fun generateQrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

private fun shareBitmap(context: android.content.Context, bitmap: Bitmap) {
    val file = File(context.cacheDir, "qr_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    val uri = FileProvider.getUriForFile(context, "com.opencontacts.app.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(intent, "Share QR"))
}

private fun formatTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
