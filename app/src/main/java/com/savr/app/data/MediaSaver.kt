package com.savr.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object MediaSaver {
    /** Saves into Movies|Pictures|Music/<folder>. API 29+: MediaStore, no permission. API 26-28: legacy path. */
    fun save(ctx: Context, source: Uri, name: String, mime: String, folder: String): Uri? {
        val (collection, dir) = when {
            mime.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mime.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
        }
        val cr = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$folder"); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = cr.insert(collection, v) ?: return null
            cr.openOutputStream(uri)!!.use { o -> cr.openInputStream(source)!!.use { it.copyTo(o) } }
            v.clear(); v.put(MediaStore.MediaColumns.IS_PENDING, 0); cr.update(uri, v, null, null)
            return uri
        }
        val out = File(File(Environment.getExternalStoragePublicDirectory(dir), folder).apply { mkdirs() }, name)
        out.outputStream().use { o -> cr.openInputStream(source)!!.use { it.copyTo(o) } }
        MediaScannerConnection.scanFile(ctx, arrayOf(out.path), arrayOf(mime), null)
        return Uri.fromFile(out)
    }

    fun delete(ctx: Context, uri: String) { runCatching {
        val u = Uri.parse(uri); if (u.scheme == "file") File(u.path!!).delete() else ctx.contentResolver.delete(u, null, null) } }

    /** Shares the gallery item itself (MediaStore / SAF URIs can be granted directly): no copy, real file name in the share sheet.
     *  Only legacy file:// paths (Android 8-9) go through FileProvider. */
    fun share(ctx: Context, uri: String, mime: String) {
        val u = Uri.parse(uri).let { if (it.scheme == "file") FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(it.path!!)) else it }
        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun exists(ctx: Context, uri: String): Boolean = runCatching {
        val u = Uri.parse(uri); if (u.scheme == "file") File(u.path!!).exists() else ctx.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false }.getOrDefault(false)

    /** Opens the file in another app (gallery, VLC, music player...). */
    fun openWith(ctx: Context, uri: String, mime: String) {
        val u = Uri.parse(uri).let { if (it.scheme == "file") FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(it.path!!)) else it }
        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(u, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun shareText(ctx: Context, text: String) = ctx.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
