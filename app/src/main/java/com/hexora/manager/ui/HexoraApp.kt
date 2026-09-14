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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.hexora.manager.core.model.FileSource
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
            if (route != Routes.EDITOR && route != Routes.SETTINGS) BottomBar(nav, route)
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onPath = { viewModel.openLocalPath(it); nav.navigate(Routes.BROWSER) },
                    onAccess = { nav.navigate(Routes.ACCESS) },
                    onAndroidData = { viewModel.openAndroidData(); nav.navigate(Routes.BROWSER) },
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
                        } else if (ref.source == FileSource.LOCAL || ref.source == FileSource.SAF) {
                            FileIntentHelper.openExternal(context, ref)
                        }
                    },
                    onShare = { refs ->
                        val safe = refs.filter { it.source == FileSource.LOCAL || it.source == FileSource.SAF }
                        if (safe.isNotEmpty()) FileIntentHelper.share(context, safe)
                    },
                    onAccess = { nav.navigate(Routes.ACCESS) },
                )
            }
            composable(Routes.TASKS) { TasksScreen(viewModel) }
            composable(Routes.ACCESS) {
                AccessScreen(viewModel) { nav.navigate(Routes.BROWSER) }
            }
            composable(Routes.EDITOR) { TextEditorScreen(viewModel) { nav.popBackStack() } }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel) { nav.popBackStack() } }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar {
        listOf(
            Triple(Routes.HOME, "Home", Icons.Outlined.Home),
            Triple(Routes.BROWSER, "Arquivos", Icons.Outlined.Folder),
            Triple(Routes.TASKS, "Tarefas", Icons.Outlined.TaskAlt),
            Triple(Routes.ACCESS, "Acesso", Icons.Outlined.Security),
        ).forEach { (target, label, icon) ->
            NavigationBarItem(
                selected = route == target,
                onClick = { nav.navigate(target) { launchSingleTop = true } },
                icon = { Icon(icon, null) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: HexoraViewModel,
    onPath: (String) -> Unit,
    onAccess: () -> Unit,
    onAndroidData: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val privilege by viewModel.privilegeState.collectAsStateWithLifecycle()
    val allFiles = rememberAllFilesState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Column { Text("HEXORA", fontWeight = FontWeight.Bold); Text("File manager avançado", style = MaterialTheme.typography.labelSmall) } },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Configurações") } },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionTitle("ARMAZENAMENTO") }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            state.error?.let { item { ErrorText(it) } }
            items(state.volumes, key = { it.id }) { volume ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storage, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(volume.name, fontWeight = FontWeight.Medium)
                        Text(
                            if (volume.totalBytes != null && volume.usedBytes != null) "${formatBytes(volume.usedBytes)} de ${formatBytes(volume.totalBytes)}" else volume.state,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionTitle("PASTAS") }
            items(viewModel.quickLocations(), key = { it.path }) { location ->
                DenseActionRow(
                    icon = if (allFiles) Icons.Outlined.Folder else Icons.Outlined.Lock,
                    title = location.label,
                    detail = if (allFiles) location.path else "Conceda All Files ou use SAF",
                    onClick = { if (allFiles) onPath(location.path) else onAccess() },
                )
            }
            item {
                DenseActionRow(
                    icon = Icons.Outlined.Android,
                    title = "Android/data",
                    detail = when {
                        privilege.rootConnected -> "Acesso via root"
                        privilege.shizukuConnected -> "Acesso via Shizuku / shell"
                        else -> "Android moderno bloqueia outros apps sem acesso privilegiado"
                    },
                    onClick = { if (privilege.rootConnected || privilege.shizukuConnected) onAndroidData() else onAccess() },
                )
            }
            item { SectionTitle("PRIVILÉGIOS") }
            item {
                DenseActionRow(
                    icon = Icons.Outlined.Security,
                    title = when {
                        privilege.rootConnected -> "Root conectado"
                        privilege.shizukuConnected -> "Shizuku conectado"
                        allFiles -> "All Files ativo"
                        else -> "Modo padrão"
                    },
                    detail = "Toque para gerenciar Shizuku, depuração sem fio, root e SAF",
                    onClick = onAccess,
                )
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

    createKind?.let { kind ->
        NameDialog(if (kind == "folder") "Nova pasta" else "Novo arquivo", "", { createKind = null }) { name ->
            if (kind == "folder") viewModel.createDirectory(name) else viewModel.createFile(name)
            createKind = null
        }
    }
    renameRef?.let { ref ->
        NameDialog("Renomear", ref.name, { renameRef = null }) { name -> viewModel.rename(ref, name); renameRef = null }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Excluir permanentemente?") },
            text = { Text("A lixeira ainda não está pronta. A exclusão não pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteSelected(); deleteConfirm = false }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancelar") } },
        )
    }
    hash?.let { value ->
        AlertDialog(onDismissRequest = viewModel::clearHashResult, title = { Text("SHA-256") }, text = { Text(value, fontFamily = FontFamily.Monospace) }, confirmButton = { TextButton(onClick = viewModel::clearHashResult) { Text("Fechar") } })
    }

    BackHandler(enabled = state.current != null) { if (!viewModel.goBack()) viewModel.goParent() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(state.current?.displayName ?: "Arquivos", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.current?.path ?: state.current?.uri ?: "Selecione um local", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = { viewModel.goBack() }, enabled = state.backStack.isNotEmpty()) { Icon(Icons.Outlined.ArrowBack, "Voltar") } },
            actions = {
                IconButton(onClick = { viewModel.goForward() }, enabled = state.forwardStack.isNotEmpty()) { Icon(Icons.Outlined.ArrowForward, "Avançar") }
                IconButton(onClick = viewModel::goParent, enabled = state.current != null) { Icon(Icons.Outlined.ArrowUpward, "Diretório pai") }
                IconButton(onClick = viewModel::refreshBrowser, enabled = state.current != null) { Icon(Icons.Outlined.Refresh, "Atualizar") }
            },
        )

        if (state.current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Folder, null, Modifier.size(54.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Abra uma pasta pela Home ou Acesso")
                    TextButton(onClick = onAccess) { Text("Central de acesso") }
                }
            }
            return@Column
        }

        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), placeholder = { Text("Filtrar nesta pasta") }, singleLine = true)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { ErrorText(it) }

        if (state.selectedIds.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                SmallAction(Icons.Outlined.ContentCopy, "Copiar") { viewModel.stageCopy(false) }
                SmallAction(Icons.Outlined.DriveFileMove, "Mover") { viewModel.stageCopy(true) }
                SmallAction(Icons.Outlined.Share, "Share") { onShare(viewModel.selectedItems()) }
                SmallAction(Icons.Outlined.Fingerprint, "Hash") { viewModel.selectedItems().singleOrNull()?.let(viewModel::calculateSha256) }
                SmallAction(Icons.Outlined.Edit, "Renomear") { viewModel.selectedItems().singleOrNull()?.let { renameRef = it } }
                SmallAction(Icons.Outlined.DeleteOutline, "Excluir") { deleteConfirm = true }
            }
        }

        state.clipboard?.let { clipboard ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${clipboard.items.size} item(ns) · ${if (clipboard.move) "mover" else "copiar"}", Modifier.weight(1f))
                    Button(onClick = viewModel::paste) { Text("Colar") }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { createKind = "folder" }) { Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.width(5.dp)); Text("Pasta") }
            OutlinedButton(onClick = { createKind = "file" }) { Icon(Icons.Outlined.NoteAdd, null); Spacer(Modifier.width(5.dp)); Text("Arquivo") }
        }

        val filtered = remember(state.entries, query) { if (query.isBlank()) state.entries else state.entries.filter { it.displayName.contains(query, true) } }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { ref ->
                val selected = ref.id in state.selectedIds
                Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background) {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = {
                                if (state.selectedIds.isNotEmpty()) viewModel.toggleSelection(ref)
                                else if (ref.isDirectory) viewModel.openDirectory(ref)
                                else onOpenFile(ref)
                            },
                            onLongClick = { viewModel.toggleSelection(ref) },
                        ).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (ref.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile, null, Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ref.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                buildString {
                                    append(if (ref.isDirectory) "Pasta" else ref.size?.let(::formatBytes) ?: "Arquivo")
                                    ref.lastModified?.let { append(" · "); append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (ref.source == FileSource.ROOT) Icon(Icons.Outlined.AdminPanelSettings, "Root", tint = MaterialTheme.colorScheme.primary)
                        else if (ref.source == FileSource.SHIZUKU) Icon(Icons.Outlined.Bolt, "Shizuku", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label) }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessScreen(viewModel: HexoraViewModel, onBrowserOpened: () -> Unit) {
    val context = LocalContext.current
    val privilege by viewModel.privilegeState.collectAsStateWithLifecycle()
    val persisted = context.contentResolver.persistedUriPermissions
    val allFiles = rememberAllFilesState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPrivileges() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            viewModel.openSafTree(uri)
            onBrowserOpened()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Acesso e privilégios") }, actions = { IconButton(onClick = viewModel::refreshPrivileges) { Icon(Icons.Outlined.Refresh, "Atualizar") } })
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("SEM ROOT / SEM SHIZUKU") }
            item {
                AccessRow(
                    "Todos os arquivos",
                    if (allFiles) "Ativo para armazenamento compartilhado. Não remove as restrições especiais de Android/data de outros apps." else "Acesso amplo ao armazenamento compartilhado. Opcional.",
                    allFiles,
                    if (allFiles) null else "Conceder",
                ) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                }
            }
            item { AccessRow("Storage Access Framework", "Selecione pastas permitidas pelo seletor do Android.", persisted.isNotEmpty(), "Selecionar pasta") { treeLauncher.launch(null) } }
            if (persisted.isNotEmpty()) {
                items(persisted, key = { it.uri.toString() }) { permission ->
                    AccessRow("Pasta SAF", permission.uri.toString(), true, "Abrir") { viewModel.openSafTree(permission.uri); onBrowserOpened() }
                }
            }

            item { SectionTitle("SHIZUKU / DEPURAÇÃO SEM FIO") }
            item {
                val detail = when {
                    privilege.shizukuConnected && privilege.shizukuUid == 0 -> "Conectado como UID 0 (Sui/root)."
                    privilege.shizukuConnected -> "Conectado como shell. Se o Shizuku foi iniciado por Depuração sem fio/ADB, este é o backend ADB do Hexora."
                    privilege.shizukuAvailable && privilege.shizukuPermission -> "Shizuku autorizado; toque para conectar o serviço de arquivos."
                    privilege.shizukuAvailable -> "Shizuku ativo; o Hexora precisa da autorização do usuário."
                    else -> "Shizuku não está ativo. No Android 11+, ele pode ser iniciado usando Depuração sem fio."
                }
                AccessRow("Shizuku", detail, privilege.shizukuConnected, if (privilege.shizukuConnected) "Abrir armazenamento" else "Conectar") {
                    if (privilege.shizukuConnected) { viewModel.openShizukuStorage(); onBrowserOpened() } else viewModel.requestOrConnectShizuku()
                }
            }
            item {
                AccessRow("Depuração sem fio", "Abra Opções do desenvolvedor para parear/iniciar o Shizuku. O Hexora não finge um cliente ADB direto quando não há uma sessão autorizada.", privilege.shizukuConnected && privilege.shizukuUid != 0, "Abrir opções") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                }
            }

            item { SectionTitle("ROOT") }
            item {
                val detail = when {
                    privilege.rootConnected -> "Serviço root conectado como UID ${privilege.rootUid ?: 0}."
                    privilege.rootGranted == true -> "Root concedido; toque para iniciar o serviço de arquivos."
                    privilege.rootGranted == false -> "Root não está disponível ou foi negado."
                    else -> "Status ainda não determinado; conectar solicitará root ao gerenciador instalado."
                }
                AccessRow("Root", detail, privilege.rootConnected, if (privilege.rootConnected) "Abrir /" else "Conectar root") {
                    if (privilege.rootConnected) { viewModel.openRootFilesystem(); onBrowserOpened() } else viewModel.connectRoot()
                }
            }

            item { SectionTitle("ANDROID/DATA") }
            item {
                val connected = privilege.rootConnected || privilege.shizukuConnected
                AccessRow(
                    "Android/data",
                    if (connected) "Abrir usando ${if (privilege.rootConnected) "root" else "Shizuku/shell"}." else "Android moderno não permite acesso universal aos dados privados de outros apps apenas com All Files/SAF. Conecte Shizuku ou root.",
                    connected,
                    if (connected) "Abrir Android/data" else null,
                ) { viewModel.openAndroidData(); onBrowserOpened() }
            }
            privilege.message?.let { message -> item { Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
private fun AccessRow(title: String, detail: String, active: Boolean, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(if (active) Icons.Outlined.CheckCircle else Icons.Outlined.Security, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun DenseActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = {}).padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksScreen(viewModel: HexoraViewModel) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Tarefas") })
        if (operations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma operação ainda") }
        else LazyColumn {
            items(operations, key = { it.operationId }) { op ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TaskAlt, null); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${op.type} · ${op.state}", fontWeight = FontWeight.Medium)
                            Text("${op.filesProcessed}/${op.filesTotal ?: "?"} itens${op.speedBytesPerSecond?.let { " · ${formatBytes(it)}/s" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                            op.error?.let { Text(it.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (op.state == FileOperationState.RUNNING || op.state == FileOperationState.QUEUED) TextButton(onClick = { viewModel.cancelOperation(op.operationId) }) { Text("Cancelar") }
                    }
                    if (op.state == FileOperationState.RUNNING) LinearProgressIndicator(progress = { op.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                HorizontalDivider()
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
            title = { Column { Text(state.ref?.displayName ?: "Editor", maxLines = 1, overflow = TextOverflow.Ellipsis); if (state.readOnly) Text("Somente leitura", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Voltar") } },
            actions = { IconButton(enabled = state.dirty && !state.readOnly && !state.saving, onClick = viewModel::saveEditor) { Icon(Icons.Outlined.Save, "Salvar") } },
        )
        if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.truncated) Text("Arquivo grande: prévia limitada a 1 MiB.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        state.error?.let { ErrorText(it) }
        BasicTextField(
            value = state.text,
            onValueChange = viewModel::updateEditorText,
            readOnly = state.readOnly,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: HexoraViewModel, onBack: () -> Unit) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Configurações") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Voltar") } })
        LazyColumn {
            item { SectionTitle("APARÊNCIA") }
            item {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { item ->
                        val label = when (item) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Escuro" }
                        if (item == mode) Button(onClick = { viewModel.setThemeMode(item) }) { Text(label) }
                        else OutlinedButton(onClick = { viewModel.setThemeMode(item) }) { Text(label) }
                    }
                }
            }
            item { SectionTitle("SEGURANÇA") }
            item { Text("Menor privilégio por padrão. Shizuku/root só são usados depois de autorização explícita.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ErrorText(message: String) { Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

@Composable
private fun SectionTitle(text: String) { Text(text, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun rememberAllFilesState(): Boolean {
    var value by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) value = Environment.isExternalStorageManager() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return value
}

private fun isTextLike(ref: HexoraFileRef): Boolean {
    if (ref.mime?.startsWith("text/") == true) return true
    return ref.extension in setOf("txt", "log", "kt", "kts", "java", "smali", "xml", "json", "json5", "yaml", "yml", "toml", "html", "css", "js", "ts", "py", "sh", "c", "h", "cpp", "hpp", "rs", "go", "sql", "md", "gradle", "properties", "ini", "conf", "csv")
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 5)
    val label = listOf("KiB", "MiB", "GiB", "TiB", "PiB")[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(exp.toDouble()), label)
}
