package com.hexora.manager.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.hexora.manager.BuildConfig
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.model.HexoraFileRef
import java.io.File

object FileIntentHelper {
    fun openExternal(context: Context, ref: HexoraFileRef) {
        val uri = shareableUri(context, ref) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ref.mime ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir com"))
    }

    fun share(context: Context, refs: List<HexoraFileRef>) {
        val uris = refs.mapNotNull { shareableUri(context, it) }
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = refs.first().mime ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Compartilhar"))
    }

    private fun shareableUri(context: Context, ref: HexoraFileRef): Uri? = when (ref.source) {
        FileSource.SAF, FileSource.MEDIASTORE -> ref.parsedUri()
        FileSource.LOCAL -> ref.path?.let { path ->
            runCatching {
                FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", File(path))
            }.getOrNull()
        }
        else -> null
    }
}
