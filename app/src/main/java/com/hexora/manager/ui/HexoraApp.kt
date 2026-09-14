package com.hexora.manager.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.operations.FileOperationState
import com.hexora.manager.core.storage.ThemeMode
import com.hexora.manager.core.util.FileIntentHelper
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private object Routes {
    const val HOME = "home"
    const val BROWSER = "browser"
    const val TASKS = "tasks"
    const val ACCESS = "access"
    const val EDITOR = "editor"
    const val SETTINGS = "settings"
}

@Composable
fun HexoraApp(viewModel: HexoraViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    Scaffold(
        bottomBar = {
            val route = nav.currentBackStackEntryAsState().value?.destination?.route
            if (route != Routes.EDITOR && route != Routes.SETTINGS) {
                BottomBar(nav = nav, route = route)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onPath = {
                        viewModel.openLocalPath(it)
                        nav.navigate(Routes.BROWSER)
                    },
                    onAccess = { nav.navigate(Routes.ACCESS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.BROWSER) {
                BrowserScreen(
                    viewModel = viewModel,
                    onOpenFile = { ref ->
                        if (isTextLike(ref)) {
                            viewModel.openTextEditor(ref)
                            nav.navigate(Routes.EDITOR)
                        } else {
                            FileIntentHelper.openExternal(context, ref)
                        }
                    },
                    onShare = { FileIntentHelper.share(context, it) },
                    onAccess = { nav.navigate(Routes.ACCESS) },
                )
            }
            composable(Routes.TASKS) { TasksScreen(viewModel) }
            composable(Routes.ACCESS) {
                AccessScreen(viewModel) { nav.navigate(Routes.BROWSER) }
            }
            composable(Routes.EDITOR) {
                TextEditorScreen(viewModel) { nav.popBackStack() }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel) { nav.popBackStack() }
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar {
        val destinations = listOf(
            Triple(Routes.HOME, "Home", Icons.Outlined.Home),
            Triple(Routes.BROWSER, "Arquivos", Icons.Outlined.Folder),
            Triple(Routes.TASKS, "Tarefas", Icons.Outlined.TaskAlt),
            Triple(Routes.ACCESS, "Acesso", Icons.Outlined.Security),
        )
        destinations.forEach { (target, label, icon) ->
            NavigationBarItem(
                selected = route == target,
                onClick = { nav.navigate(target) { launchSingleTop = true } },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: HexoraViewModel,
    onPath: (String) -> Unit,
    onAccess: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val allFiles = rememberAllFilesState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("HEXORA", fontWeight = FontWeight.Bold)
                    Text("Controle preciso dos seus arquivos", style = MaterialTheme.typography.labelSmall)
                }
            },
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Configurações")
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionTitle("ARMAZENAMENTO") }
            if (state.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            }
            state.error?.let { error ->
                item { ErrorText(error) }
            }
            items(state.volumes, key = { it.id }) { volume ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Storage, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(volume.name, fontWeight = FontWeight.Medium)
                        val total = volume.totalBytes
                        val used = volume.usedBytes
                        Text(
                            text = if (total != null && used != null) {
                                "${formatBytes(used)} de ${formatBytes(total)} · ${volume.state}"
                            } else {
                                volume.state
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("PASTAS") }
            items(viewModel.quickLocations(), key = { it.path }) { location ->
                val locked = !allFiles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { if (locked) onAccess() else onPath(location.path) },
                            onLongClick = {},
                        )
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (locked) Icons.Outlined.Lock else Icons.Outlined.Folder, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(location.label, Modifier.weight(1f))
                    if (locked) {
                        Text(
                            "Conceder acesso",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item { SectionTitle("ACESSO") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onAccess, onLongClick = {})
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (allFiles) "Armazenamento compartilhado disponível" else "Acesso amplo desativado",
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "SAF continua disponível como alternativa de menor privilégio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(
    viewModel: HexoraViewModel,
    onOpenFile: (HexoraFileRef) -> Unit,
    onShare: (List<HexoraFileRef>) -> Unit,
    onAccess: () -> Unit,
) {
    val state by viewModel.browser.collectAsStateWithLifecycle()
    val hash by viewModel.hashResult.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var createKind by remember { mutableStateOf<String?>(null) }
    var renameRef by remember { mutableStateOf<HexoraFileRef?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var properties by remember { mutableStateOf<HexoraFileRef?>(null) }

    if (createKind != null) {
        NameDialog(
            title = if (createKind == "folder") "Nova pasta" else "Novo arquivo",
            initial = "",
            onDismiss = { createKind = null },
            onConfirm = { name ->
                if (createKind == "folder") viewModel.createDirectory(name) else viewModel.createFile(name)
                createKind = null
            },
        )
    }

    renameRef?.let { ref ->
        NameDialog(
            title = "Renomear",
            initial = ref.name,
            onDismiss = { renameRef = null },
            onConfirm = {
                viewModel.rename(ref, it)
                renameRef = null
            },
        )
    }

    properties?.let { ref ->
        PropertiesDialog(ref = ref, onDismiss = { properties = null })
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Excluir permanentemente?") },
            text = { Text("A lixeira ainda não está implementada nesta versão. Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        deleteConfirm = false
                    },
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    hash?.let { value ->
        AlertDialog(
            onDismissRequest = viewModel::clearHashResult,
            title = { Text("SHA-256") },
            text = { Text(value, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = viewModel::clearHashResult) { Text("Fechar") }
            },
        )
    }

    BackHandler(enabled = state.current != null) {
        if (!viewModel.goBack()) viewModel.goParent()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        state.current?.displayName ?: "Arquivos",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.current?.path ?: state.current?.uri ?: "Selecione um local",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.goBack() },
                    enabled = state.backStack.isNotEmpty(),
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar") }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.goForward() },
                    enabled = state.forwardStack.isNotEmpty(),
                ) { Icon(Icons.Outlined.ArrowForward, contentDescription = "Avançar") }
                IconButton(onClick = viewModel::goParent, enabled = state.current != null) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Diretório pai")
                }
                IconButton(onClick = viewModel::refreshBrowser, enabled = state.current != null) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar")
                }
            },
        )

        val current = state.current
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Abra uma pasta pela Home ou pelo SAF")
                    TextButton(onClick = onAccess) { Text("Abrir central de acesso") }
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                label = { Text("Filtrar pasta") },
                singleLine = true,
            )

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { ErrorText(it) }

            if (state.selectedIds.isNotEmpty()) {
                SelectionActions(
                    count = state.selectedIds.size,
                    onCopy = { viewModel.stageCopy(false) },
                    onMove = { viewModel.stageCopy(true) },
                    onShare = { onShare(viewModel.selectedItems()) },
                    onDelete = { deleteConfirm = true },
                    onRename = { viewModel.selectedItems().singleOrNull()?.let { renameRef = it } },
                    onHash = { viewModel.selectedItems().singleOrNull()?.let(viewModel::calculateSha256) },
                )
            }

            val clipboard = state.clipboard
            if (clipboard != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${clipboard.items.size} item(ns) prontos para ${if (clipboard.move) "mover" else "copiar"}",
                            Modifier.weight(1f),
                        )
                        Button(onClick = viewModel::paste) { Text("Colar") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { createKind = "folder" }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pasta")
                }
                OutlinedButton(onClick = { createKind = "file" }) {
                    Icon(Icons.Outlined.NoteAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Arquivo")
                }
            }

            val filtered = remember(state.entries, query) {
                if (query.isBlank()) state.entries
                else state.entries.filter { it.displayName.contains(query, ignoreCase = true) }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { ref ->
                    val selected = ref.id in state.selectedIds
                    FileRow(
                        ref = ref,
                        selected = selected,
                        onClick = {
                            if (state.selectedIds.isNotEmpty()) {
                                viewModel.toggleSelection(ref)
                            } else if (ref.isDirectory) {
                                viewModel.openDirectory(ref)
                            } else {
                                onOpenFile(ref)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(ref) },
                        onInfo = { properties = ref },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun SelectionActions(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onHash: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "$count selecionado(s)",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar") }
            IconButton(onClick = onMove) { Icon(Icons.Outlined.DriveFileMove, contentDescription = "Mover") }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Compartilhar") }
            IconButton(onClick = onHash) { Icon(Icons.Outlined.CheckCircle, contentDescription = "SHA-256") }
            IconButton(onClick = onRename) { Icon(Icons.Outlined.InsertDriveFile, contentDescription = "Renomear") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Excluir") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    ref: HexoraFileRef,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfo: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (ref.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ref.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        if (ref.isDirectory) append("Pasta") else append(ref.size?.let(::formatBytes) ?: "Arquivo")
                        ref.lastModified?.let {
                            append(" · ")
                            append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, contentDescription = "Propriedades") }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) {
                Text("Confirmar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PropertiesDialog(ref: HexoraFileRef, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propriedades") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Property("Nome", ref.displayName)
                Property("Origem", ref.source.name)
                ref.path?.let { Property("Path", it) }
                ref.uri?.let { Property("URI", it) }
                Property("Tipo", ref.mime ?: ref.extension ?: if (ref.isDirectory) "Diretório" else "Desconhecido")
                ref.size?.let { Property("Tamanho", formatBytes(it)) }
                Property("Provider", ref.providerKey)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun Property(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessScreen(viewModel: HexoraViewModel, onSafOpened: () -> Unit) {
    val context = LocalContext.current
    val persisted = context.contentResolver.persistedUriPermissions
    val allFiles = rememberAllFilesState()
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.openSafTree(uri)
            onSafOpened()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Acesso e privilégios") })
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("ARMAZENAMENTO") }
            item {
                AccessRow(
                    title = "Todos os arquivos",
                    detail = if (allFiles) {
                        "Concedido. Áreas protegidas pelo Android continuam sujeitas às regras do sistema."
                    } else {
                        "Opcional; use somente quando a navegação ampla no armazenamento compartilhado for necessária."
                    },
                    active = allFiles,
                    action = if (allFiles) null else "Conceder",
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
            item {
                AccessRow(
                    title = "Storage Access Framework",
                    detail = "Escolha somente as pastas que deseja conceder ao Hexora.",
                    active = persisted.isNotEmpty(),
                    action = "Selecionar pasta",
                ) { treeLauncher.launch(null) }
            }
            if (persisted.isNotEmpty()) {
                items(persisted, key = { it.uri.toString() }) { permission ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            permission.uri.toString(),
                            Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(
                            onClick = {
                                viewModel.openSafTree(permission.uri)
                                onSafOpened()
                            },
                        ) { Text("Abrir") }
                    }
                }
            }
            item { SectionTitle("PRIVILÉGIOS AVANÇADOS") }
            item { AccessRow("Shizuku", "Ainda não integrado; nenhum acesso é simulado.", false) }
            item { AccessRow("Wireless ADB", "Ainda não integrado; a autorização ADB nunca será contornada.", false) }
            item { AccessRow("Root", "Ainda não integrado; futuras operações root serão explícitas e isoladas.", false) }
        }
    }
}

@Composable
private fun AccessRow(
    title: String,
    detail: String,
    active: Boolean,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (active) Icons.Outlined.CheckCircle else Icons.Outlined.Security,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null && onAction != null) {
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksScreen(viewModel: HexoraViewModel) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Tarefas") })
        if (operations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma operação ainda")
            }
        } else {
            LazyColumn {
                items(operations, key = { it.operationId }) { op ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${op.type} · ${op.state}", fontWeight = FontWeight.Medium)
                                Text(
                                    "${op.filesProcessed}/${op.filesTotal ?: "?"} itens${op.speedBytesPerSecond?.let { " · ${formatBytes(it)}/s" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                op.error?.let {
                                    Text(
                                        it.userMessage(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (op.state == FileOperationState.RUNNING || op.state == FileOperationState.QUEUED) {
                                TextButton(onClick = { viewModel.cancelOperation(op.operationId) }) { Text("Cancelar") }
                            }
                        }
                        if (op.state == FileOperationState.RUNNING) {
                            LinearProgressIndicator(
                                progress = { op.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditorScreen(viewModel: HexoraViewModel, onBack: () -> Unit) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        state.ref?.displayName ?: "Editor",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.readOnly) {
                        Text("Somente leitura", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar") }
            },
            actions = {
                IconButton(
                    enabled = state.dirty && !state.readOnly && !state.saving,
                    onClick = viewModel::saveEditor,
                ) { Icon(Icons.Outlined.Save, contentDescription = "Salvar") }
            },
        )
        if (state.loading || state.saving) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.truncated) {
            Text(
                "Arquivo grande: prévia limitada a 1 MiB.",
                Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.error?.let { ErrorText(it) }
        BasicTextField(
            value = state.text,
            onValueChange = viewModel::updateEditorText,
            readOnly = state.readOnly,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: HexoraViewModel, onBack: () -> Unit) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Configurações") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar") }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("APARÊNCIA") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { item ->
                        val label = when (item) {
                            ThemeMode.SYSTEM -> "Sistema"
                            ThemeMode.LIGHT -> "Claro"
                            ThemeMode.DARK -> "Escuro"
                        }
                        if (item == mode) {
                            Button(onClick = { viewModel.setThemeMode(item) }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { viewModel.setThemeMode(item) }) { Text(label) }
                        }
                    }
                }
            }
            item { SectionTitle("SEGURANÇA") }
            item {
                Text(
                    "Menor privilégio por padrão. Sem conta, anúncios ou telemetria. Exclusões são permanentes nesta versão e exigem confirmação.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun rememberAllFilesState(): Boolean {
    var value by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                value = Environment.isExternalStorageManager()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return value
}

private fun isTextLike(ref: HexoraFileRef): Boolean {
    if (ref.mime?.startsWith("text/") == true) return true
    return ref.extension in setOf(
        "txt", "log", "kt", "kts", "java", "smali", "xml", "json", "json5", "yaml", "yml", "toml",
        "html", "css", "js", "ts", "py", "sh", "c", "h", "cpp", "hpp", "rs", "go", "sql", "md",
        "gradle", "properties", "ini", "conf", "csv",
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 5)
    val label = listOf("KiB", "MiB", "GiB", "TiB", "PiB")[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(exp.toDouble()), label)
}
