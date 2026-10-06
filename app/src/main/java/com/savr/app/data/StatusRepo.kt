package com.savr.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** WhatsApp statuses via Storage Access Framework (works on Android 11+ where Android/media is scoped). */
object StatusRepo {
    fun initialUri(business: Boolean): Uri {
        val pkg = if (business) "com.whatsapp.w4b" else "com.whatsapp"; val sub = if (business) "WhatsApp Business" else "WhatsApp"
        return Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2F$pkg%2F$sub%2FMedia%2F.Statuses")
    }
    fun persist(ctx: Context, tree: Uri) = ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    fun release(ctx: Context, tree: String) { runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    fun looksLikeStatusFolder(tree: Uri) = Uri.decode(tree.toString()).contains(".Statuses")

    suspend fun list(ctx: Context, trees: Set<String>): List<StatusFile> = withContext(Dispatchers.IO) {
        trees.flatMap { t ->
            runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(t))?.listFiles().orEmpty() }.getOrDefault(emptyArray()).filter {
                it.isFile && (it.type?.startsWith("image") == true || it.type?.startsWith("video") == true) }
                .map { StatusFile(it.uri.toString(), it.name ?: "status", it.type.orEmpty(), it.lastModified()) }
        }.sortedByDescending { it.modified }
    }
}
