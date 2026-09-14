package com.hexora.manager.privileged

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.hexora.manager.BuildConfig
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku


data class PrivilegeUiState(
    val shizukuAvailable: Boolean = false,
    val shizukuPermission: Boolean = false,
    val shizukuConnected: Boolean = false,
    val shizukuUid: Int? = null,
    val rootGranted: Boolean? = null,
    val rootConnected: Boolean = false,
    val rootUid: Int? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class PrivilegeAccessManager(private val application: Application) {
    private val _state = MutableStateFlow(PrivilegeUiState(rootGranted = Shell.isAppGrantedRoot()))
    val state: StateFlow<PrivilegeUiState> = _state.asStateFlow()

    private val _shizukuService = MutableStateFlow<IPrivilegedFileService?>(null)
    val shizukuService: StateFlow<IPrivilegedFileService?> = _shizukuService.asStateFlow()

    private val _rootService = MutableStateFlow<IPrivilegedFileService?>(null)
    val rootService: StateFlow<IPrivilegedFileService?> = _rootService.asStateFlow()

    private val shizukuArgs = Shizuku.UserServiceArgs(
        ComponentName(application, ShizukuFileService::class.java),
    )
        .processNameSuffix("hexora_files")
        .tag("hexora-files-v2")
        .version(BuildConfig.VERSION_CODE)
        .debuggable(BuildConfig.DEBUG)
        .daemon(false)

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = IPrivilegedFileService.Stub.asInterface(service)
            _shizukuService.value = remote
            val uid = runCatching { remote.uid() }.getOrNull()
            _state.value = _state.value.copy(
                shizukuConnected = remote != null,
                shizukuUid = uid,
                busy = false,
                message = if (uid == 0) "Shizuku conectado como root." else "Shizuku conectado como shell (ADB).",
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _shizukuService.value = null
            _state.value = _state.value.copy(shizukuConnected = false, shizukuUid = null)
        }
    }

    private val rootConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = IPrivilegedFileService.Stub.asInterface(service)
            _rootService.value = remote
            val uid = runCatching { remote.uid() }.getOrNull()
            _state.value = _state.value.copy(
                rootGranted = uid == 0,
                rootConnected = remote != null && uid == 0,
                rootUid = uid,
                busy = false,
                message = if (uid == 0) "Root conectado." else "O serviço root não iniciou como UID 0.",
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _rootService.value = null
            _state.value = _state.value.copy(rootConnected = false, rootUid = null, busy = false)
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        _shizukuService.value = null
        _state.value = _state.value.copy(
            shizukuAvailable = false,
            shizukuPermission = false,
            shizukuConnected = false,
            shizukuUid = null,
            busy = false,
            message = "Shizuku foi desconectado.",
        )
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (requestCode != SHIZUKU_PERMISSION_REQUEST) return@OnRequestPermissionResultListener
        val granted = result == PackageManager.PERMISSION_GRANTED
        _state.value = _state.value.copy(shizukuPermission = granted, busy = false)
        if (granted) connectShizuku() else _state.value = _state.value.copy(message = "Permissão do Shizuku negada.")
    }

    init {
        Shell.setDefaultBuilder(Shell.Builder.create().setContext(application))
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refreshShizukuState()
    }

    fun requestOrConnectShizuku() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            _state.value = _state.value.copy(
                shizukuAvailable = false,
                busy = false,
                message = "Shizuku não está ativo. Inicie-o por Depuração sem fio ou root.",
            )
            return
        }
        val granted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (granted) {
            connectShizuku()
        } else {
            _state.value = _state.value.copy(shizukuAvailable = true, busy = true, message = "Aguardando permissão do Shizuku…")
            runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) }
                .onFailure { _state.value = _state.value.copy(busy = false, message = it.message ?: "Falha ao pedir permissão Shizuku.") }
        }
    }

    fun connectShizuku() {
        val granted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!granted) {
            requestOrConnectShizuku()
            return
        }
        _state.value = _state.value.copy(busy = true, shizukuPermission = true, message = "Conectando ao serviço privilegiado…")
        runCatching { Shizuku.bindUserService(shizukuArgs, shizukuConnection) }
            .onFailure { _state.value = _state.value.copy(busy = false, message = it.message ?: "Falha ao conectar Shizuku.") }
    }

    fun connectRoot() {
        _state.value = _state.value.copy(busy = true, message = "Solicitando acesso root…")
        val intent = Intent(application, HexoraRootFileService::class.java)
        runCatching { RootService.bind(intent, rootConnection) }
            .onFailure {
                _state.value = _state.value.copy(
                    rootGranted = Shell.isAppGrantedRoot(),
                    busy = false,
                    message = it.message ?: "Falha ao iniciar serviço root.",
                )
            }
    }

    fun refresh() {
        refreshShizukuState()
        _state.value = _state.value.copy(rootGranted = Shell.isAppGrantedRoot())
    }

    private fun refreshShizukuState() {
        val available = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = available && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        _state.value = _state.value.copy(
            shizukuAvailable = available,
            shizukuPermission = granted,
            shizukuUid = if (available) runCatching { Shizuku.getUid() }.getOrNull() else null,
        )
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 0x4845
    }
}
