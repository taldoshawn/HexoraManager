package com.hexora.manager.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexora.manager.HexoraApplication
import com.hexora.manager.core.filesystem.LocalFileProvider
import com.hexora.manager.core.filesystem.PrivilegedFileProvider
import com.hexora.manager.core.model.HexoraError
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import com.hexora.manager.core.operations.FileOperation
import com.hexora.manager.core.operations.FileOperationState
import com.hexora.manager.core.storage.StorageRepository
import com.hexora.manager.core.storage.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class HomeUiState(
    val loading: Boolean = true,
    val volumes: List<StorageRepository.StorageVolumeInfo> = emptyList(),
    val error: String? = null,
)

data class ClipboardState(
    val items: List<HexoraFileRef>,
    val move: Boolean,
)

data class BrowserUiState(
    val current: HexoraFileRef? = null,
    val entries: List<HexoraFileRef> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val backStack: List<HexoraFileRef> = emptyList(),
    val forwardStack: List<HexoraFileRef> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
)

data class EditorUiState(
    val ref: HexoraFileRef? = null,
    val text: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val readOnly: Boolean = true,
    val truncated: Boolean = false,
    val dirty: Boolean = false,
    val error: String? = null,
)

class HexoraViewModel(application: Application) : AndroidViewModel(application) {
    private val services = (application as HexoraApplication).services

    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _browser = MutableStateFlow(BrowserUiState())
    val browser: StateFlow<BrowserUiState> = _browser.asStateFlow()

    private val _editor = MutableStateFlow(EditorUiState())
    val editor: StateFlow<EditorUiState> = _editor.asStateFlow()

    val operations: StateFlow<List<FileOperation>> = services.operationEngine.operations
    val themeMode = services.settingsRepository.themeMode
    val privilegeState = services.privilegeAccess.state

    private val _hashResult = MutableStateFlow<String?>(null)
    val hashResult: StateFlow<String?> = _hashResult.asStateFlow()

    init {
        refreshHome()
        services.privilegeAccess.refresh()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { services.settingsRepository.setThemeMode(mode) }
    }

    fun refreshHome() {
        viewModelScope.launch {
            _home.value = _home.value.copy(loading = true, error = null)
            runCatching { services.storageRepository.volumes() }
                .onSuccess { _home.value = HomeUiState(loading = false, volumes = it) }
                .onFailure { _home.value = HomeUiState(loading = false, error = "Não foi possível ler os volumes de armazenamento.") }
        }
    }

    fun quickLocations(): List<StorageRepository.QuickLocation> = services.storageRepository.quickLocations()

    fun requestOrConnectShizuku() = services.privilegeAccess.requestOrConnectShizuku()

    fun connectRoot() = services.privilegeAccess.connectRoot()

    fun refreshPrivileges() = services.privilegeAccess.refresh()

    fun openShizukuStorage() = openPrivileged(services.shizukuProvider, SHARED_STORAGE_ROOT)

    fun openRootFilesystem() = openPrivileged(services.rootProvider, "/")

    fun openAndroidData() {
        when {
            services.privilegeAccess.rootService.value != null -> openPrivileged(services.rootProvider, ANDROID_DATA_PATH)
            services.privilegeAccess.shizukuService.value != null -> openPrivileged(services.shizukuProvider, ANDROID_DATA_PATH)
            else -> _browser.value = _browser.value.copy(
                error = "Android/data de outros apps é bloqueado pelo Android moderno. Conecte Shizuku/Depuração sem fio ou Root para tentar acesso privilegiado.",
            )
        }
    }

    private fun openPrivileged(provider: PrivilegedFileProvider, path: String) {
        viewModelScope.launch {
            when (val result = provider.resolve(path)) {
                is HexoraResult.Success -> openDirectory(result.value, pushHistory = false)
                is HexoraResult.Failure -> _browser.value = BrowserUiState(error = result.error.userMessage())
            }
        }
    }

    fun openLocalPath(path: String) {
        when (val result = services.storageRepository.resolveLocal(path)) {
            is HexoraResult.Success -> openDirectory(result.value, pushHistory = false)
            is HexoraResult.Failure -> _browser.value = BrowserUiState(error = result.error.userMessage())
        }
    }

    fun openSafTree(uri: Uri) {
        when (val result = services.safProvider.treeRoot(uri)) {
            is HexoraResult.Success -> openDirectory(result.value, pushHistory = false)
            is HexoraResult.Failure -> _browser.value = BrowserUiState(error = result.error.userMessage())
        }
    }

    fun openDirectory(ref: HexoraFileRef, pushHistory: Boolean = true) {
        if (!ref.isDirectory) return
        val previous = _browser.value.current
        val newBack = if (pushHistory && previous != null && previous.id != ref.id) _browser.value.backStack + previous else _browser.value.backStack
        _browser.value = _browser.value.copy(
            current = ref,
            loading = true,
            error = null,
            backStack = newBack,
            forwardStack = if (pushHistory) emptyList() else _browser.value.forwardStack,
            selectedIds = emptySet(),
        )
        viewModelScope.launch {
            val provider = services.providers.forRef(ref)
            val result = provider?.list(ref) ?: HexoraResult.Failure(HexoraError.UnsupportedFormat("Provider não encontrado"))
            _browser.value = when (result) {
                is HexoraResult.Success -> _browser.value.copy(entries = result.value, loading = false, error = null)
                is HexoraResult.Failure -> _browser.value.copy(entries = emptyList(), loading = false, error = result.error.userMessage())
            }
        }
    }

    fun refreshBrowser() {
        _browser.value.current?.let { openDirectory(it, pushHistory = false) }
    }

    fun goBack(): Boolean {
        val state = _browser.value
        val target = state.backStack.lastOrNull() ?: return false
        val current = state.current
        _browser.value = state.copy(
            backStack = state.backStack.dropLast(1),
            forwardStack = current?.let { listOf(it) + state.forwardStack } ?: state.forwardStack,
        )
        openDirectory(target, pushHistory = false)
        return true
    }

    fun goForward(): Boolean {
        val state = _browser.value
        val target = state.forwardStack.firstOrNull() ?: return false
        val current = state.current
        _browser.value = state.copy(
            backStack = current?.let { state.backStack + it } ?: state.backStack,
            forwardStack = state.forwardStack.drop(1),
        )
        openDirectory(target, pushHistory = false)
        return true
    }

    fun goParent() {
        val current = _browser.value.current ?: return
        viewModelScope.launch {
            val provider = services.providers.forRef(current) ?: return@launch
            when (val result = provider.parentOf(current)) {
                is HexoraResult.Success -> if (result.value != null) openDirectory(result.value) else goBack()
                is HexoraResult.Failure -> _browser.value = _browser.value.copy(error = result.error.userMessage())
            }
        }
    }

    fun toggleSelection(ref: HexoraFileRef) {
        val selected = _browser.value.selectedIds.toMutableSet()
        if (!selected.add(ref.id)) selected.remove(ref.id)
        _browser.value = _browser.value.copy(selectedIds = selected)
    }

    fun clearSelection() {
        _browser.value = _browser.value.copy(selectedIds = emptySet())
    }

    fun selectedItems(): List<HexoraFileRef> {
        val selected = _browser.value.selectedIds
        return _browser.value.entries.filter { it.id in selected }
    }

    fun stageCopy(move: Boolean) {
        val items = selectedItems()
        if (items.isEmpty()) return
        _browser.value = _browser.value.copy(clipboard = ClipboardState(items, move), selectedIds = emptySet())
    }

    fun paste() {
        val state = _browser.value
        val clipboard = state.clipboard ?: return
        val destination = state.current ?: return
        val id = services.operationEngine.copy(
            sources = clipboard.items,
            destinationDirectory = destination,
            deleteSourceAfterCopy = clipboard.move,
        )
        if (clipboard.move) _browser.value = _browser.value.copy(clipboard = null)
        watchOperation(id)
    }

    fun cancelOperation(operationId: String) {
        services.operationEngine.cancel(operationId)
    }

    fun deleteSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        val id = services.operationEngine.delete(items)
        clearSelection()
        watchOperation(id)
    }

    fun rename(ref: HexoraFileRef, newName: String) {
        viewModelScope.launch {
            when (val result = services.operationEngine.rename(ref, newName)) {
                is HexoraResult.Success -> refreshBrowser()
                is HexoraResult.Failure -> _browser.value = _browser.value.copy(error = result.error.userMessage())
            }
        }
    }

    fun createFile(name: String) {
        val current = _browser.value.current ?: return
        viewModelScope.launch {
            when (val result = services.operationEngine.createFile(current, name)) {
                is HexoraResult.Success -> refreshBrowser()
                is HexoraResult.Failure -> _browser.value = _browser.value.copy(error = result.error.userMessage())
            }
        }
    }

    fun createDirectory(name: String) {
        val current = _browser.value.current ?: return
        viewModelScope.launch {
            when (val result = services.operationEngine.createDirectory(current, name)) {
                is HexoraResult.Success -> refreshBrowser()
                is HexoraResult.Failure -> _browser.value = _browser.value.copy(error = result.error.userMessage())
            }
        }
    }

    fun calculateSha256(ref: HexoraFileRef) {
        _hashResult.value = "Calculando…"
        viewModelScope.launch {
            _hashResult.value = when (val result = services.operationEngine.sha256(ref)) {
                is HexoraResult.Success -> result.value
                is HexoraResult.Failure -> result.error.userMessage()
            }
        }
    }

    fun clearHashResult() {
        _hashResult.value = null
    }

    fun openTextEditor(ref: HexoraFileRef) {
        _editor.value = EditorUiState(ref = ref, loading = true)
        viewModelScope.launch {
            val provider = services.providers.forRef(ref)
            if (provider == null) {
                _editor.value = _editor.value.copy(loading = false, error = "Provider não disponível.")
                return@launch
            }
            val inputResult = provider.openInput(ref)
            val input = when (inputResult) {
                is HexoraResult.Success -> inputResult.value
                is HexoraResult.Failure -> {
                    _editor.value = _editor.value.copy(loading = false, error = inputResult.error.userMessage())
                    return@launch
                }
            }
            val maxEditable = MAX_EDITABLE_TEXT_BYTES
            val knownSize = ref.size ?: 0L
            val truncated = knownSize > maxEditable
            val bytes = withContext(Dispatchers.IO) {
                input.use { stream -> readLimited(stream, if (truncated) PREVIEW_TEXT_BYTES else maxEditable.toInt()) }
            }
            val text = bytes.toString(Charsets.UTF_8)
            _editor.value = EditorUiState(
                ref = ref,
                text = text,
                loading = false,
                readOnly = ref.providerKey != LocalFileProvider.KEY || truncated,
                truncated = truncated,
                dirty = false,
            )
        }
    }

    fun updateEditorText(value: String) {
        val state = _editor.value
        if (state.readOnly || state.loading) return
        _editor.value = state.copy(text = value, dirty = true)
    }

    fun saveEditor() {
        val state = _editor.value
        val ref = state.ref ?: return
        if (state.readOnly || ref.providerKey != LocalFileProvider.KEY) return
        _editor.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = services.localProvider.safeReplaceText(ref, _editor.value.text)) {
                is HexoraResult.Success -> _editor.value = _editor.value.copy(saving = false, dirty = false)
                is HexoraResult.Failure -> _editor.value = _editor.value.copy(saving = false, error = result.error.userMessage())
            }
        }
    }

    private fun watchOperation(operationId: String) {
        viewModelScope.launch {
            while (true) {
                val op = operations.value.firstOrNull { it.operationId == operationId }
                if (op == null) return@launch
                if (op.state in setOf(FileOperationState.COMPLETED, FileOperationState.FAILED, FileOperationState.CANCELLED)) {
                    refreshBrowser()
                    return@launch
                }
                delay(250)
            }
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            if (read == 0) continue
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_EDITABLE_TEXT_BYTES = 4L * 1024L * 1024L
        private const val PREVIEW_TEXT_BYTES = 1024 * 1024
        private const val SHARED_STORAGE_ROOT = "/storage/emulated/0"
        private const val ANDROID_DATA_PATH = "/storage/emulated/0/Android/data"
    }
}
