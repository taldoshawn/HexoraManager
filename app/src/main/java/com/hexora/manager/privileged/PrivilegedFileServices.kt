package com.hexora.manager.privileged

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.Keep
import com.topjohnwu.superuser.ipc.RootService
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import kotlin.system.exitProcess

internal data class PrivilegedRecord(
    val path: String,
    val name: String,
    val directory: Boolean,
    val symlink: Boolean,
    val size: Long,
    val modified: Long,
    val readable: Boolean,
    val writable: Boolean,
)

internal object PrivilegedRecordCodec {
    private const val VERSION = "1"
    private const val SEP = "|"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(file: File): String {
        val path = file.toPath().toAbsolutePath().normalize()
        val attrs = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull()
        val symlink = Files.isSymbolicLink(path)
        val directory = attrs?.isDirectory == true
        val size = attrs?.size() ?: 0L
        val modified = attrs?.lastModifiedTime()?.toMillis() ?: 0L
        return listOf(
            VERSION,
            b64(path.toString()),
            b64(file.name.ifEmpty { path.toString() }),
            if (directory) "1" else "0",
            if (symlink) "1" else "0",
            size.toString(),
            modified.toString(),
            if (Files.isReadable(path)) "1" else "0",
            if (Files.isWritable(path)) "1" else "0",
        ).joinToString(SEP)
    }

    fun decode(value: String): PrivilegedRecord {
        val p = value.split(SEP)
        require(p.size == 9 && p[0] == VERSION) { "Invalid privileged record" }
        return PrivilegedRecord(
            path = text(p[1]),
            name = text(p[2]),
            directory = p[3] == "1",
            symlink = p[4] == "1",
            size = p[5].toLongOrNull() ?: 0L,
            modified = p[6].toLongOrNull() ?: 0L,
            readable = p[7] == "1",
            writable = p[8] == "1",
        )
    }

    private fun b64(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun text(value: String): String = decoder.decode(value).toString(Charsets.UTF_8)
}

open class PrivilegedFileBinder(
    private val onDestroyCallback: () -> Unit,
) : IPrivilegedFileService.Stub() {

    override fun uid(): Int = Process.myUid()

    override fun stat(path: String): String {
        val file = checkedFile(path)
        if (!existsNoFollow(file.toPath())) throw FileNotFoundException(path)
        return PrivilegedRecordCodec.encode(file)
    }

    override fun list(path: String): MutableList<String> {
        val directory = checkedFile(path)
        if (!directory.isDirectory) throw FileNotFoundException("Not a directory: $path")
        val files = directory.listFiles() ?: throw SecurityException("Cannot list $path")
        return files
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map(PrivilegedRecordCodec::encode)
            .toMutableList()
    }

    override fun openRead(path: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(checkedFile(path), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun openWrite(path: String, truncate: Boolean): ParcelFileDescriptor {
        var flags = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
        if (truncate) flags = flags or ParcelFileDescriptor.MODE_TRUNCATE
        return ParcelFileDescriptor.open(checkedFile(path), flags)
    }

    override fun createFile(path: String): Boolean {
        val file = checkedFile(path)
        val parent = file.parentFile ?: return false
        if (!parent.isDirectory) return false
        return file.createNewFile()
    }

    override fun createDirectory(path: String): Boolean {
        val file = checkedFile(path)
        val parent = file.parentFile ?: return false
        if (!parent.isDirectory) return false
        return file.mkdir()
    }

    override fun rename(source: String, target: String): Boolean {
        val src = checkedFile(source).toPath()
        val dst = checkedFile(target).toPath()
        if (!existsNoFollow(src) || existsNoFollow(dst)) return false
        Files.move(src, dst)
        return true
    }

    override fun delete(path: String, recursive: Boolean): Boolean {
        val target = checkedFile(path).toPath()
        if (!existsNoFollow(target)) return false
        if (!recursive || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(target)
            return true
        }
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
        return true
    }

    override fun destroy() {
        onDestroyCallback()
    }

    private fun checkedFile(raw: String): File {
        require(raw.isNotBlank()) { "Empty path" }
        require(raw.indexOf('\u0000') < 0) { "NUL in path" }
        val path = Path.of(raw)
        require(path.isAbsolute) { "Privileged paths must be absolute" }
        return path.normalize().toFile()
    }

    private fun existsNoFollow(path: Path): Boolean = Files.exists(path, LinkOption.NOFOLLOW_LINKS)
}

@Keep
class ShizukuFileService : PrivilegedFileBinder(onDestroyCallback = { exitProcess(0) })

class HexoraRootFileService : RootService() {
    private val binder by lazy { PrivilegedFileBinder(onDestroyCallback = { stopSelf() }) }

    override fun onBind(intent: Intent): IBinder = binder
}
