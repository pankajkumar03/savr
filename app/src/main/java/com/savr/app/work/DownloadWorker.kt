package com.savr.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.savr.app.MainActivity
import com.savr.app.data.*
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Resumable (HTTP Range) foreground download. Pause = cancel work (partial file kept); resume = enqueue again. */
class DownloadWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    private val dao get() = ServiceLocator.db.dao()

    override suspend fun getForegroundInfo() = info("Downloading", 0)

    private fun info(title: String, pct: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dl", "Downloads", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, "dl").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setProgress(100, pct, pct == 0).setOngoing(true).setOnlyAlertOnce(true).build()
        val nid = inputData.getString("id").hashCode()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(nid, n)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val e = dao.get(id) ?: return Result.success()
        runCatching { setForeground(info(e.title, (e.progress * 100).toInt())) }
        dao.setProgress(id, "RUNNING", e.progress)
        val file = File(e.path); val have = if (file.exists()) file.length() else 0L
        return try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(e.remoteUrl).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
                ServiceLocator.downloadHttp.newCall(req).execute().use { r ->
                    if (r.code == 416 && have > 0) { file.delete(); throw IOException("Range not satisfiable") } // stale partial: restart next time
                    if (r.code in 400..499) { file.delete(); throw LinkExpired() }
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val append = have > 0 && r.code == 206
                    val len = r.body!!.contentLength()
                    val total = if (len > 0) len + (if (append) have else 0) else -1L
                    var done = if (append) have else 0L; var last = -1
                    file.parentFile?.mkdirs()
                    FileOutputStream(file, append).use { out -> r.body!!.byteStream().use { inp ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n; ensureActive()
                            if (total > 0) { val pct = (done * 100 / total).toInt()
                                if (pct != last) { last = pct; dao.setProgress(id, "RUNNING", pct / 100f); runCatching { setForeground(info(e.title, pct)) } } }
                        } } }
                }
                val uri = MediaSaver.save(applicationContext, Uri.fromFile(file), e.fileName, e.mime, FOLDER)
                file.delete() // keep only the gallery copy
                if (uri == null) throw IOException("Could not save to gallery")
                dao.finish(id, "DONE", 1f, uri.toString())
            }
            notifyResult(id, e.title, true)
            Result.success()
        } catch (ce: CancellationException) {
            withContext(NonCancellable) { dao.setProgress(id, "PAUSED", dao.get(id)?.progress ?: 0f) }; throw ce
        } catch (t: Throwable) {
            // keep progress so "Retry" resumes from the partial file (unless the link expired and the file was dropped)
            dao.setProgress(id, "FAILED", if (t is LinkExpired || !file.exists()) 0f else (dao.get(id)?.progress ?: 0f))
            notifyResult(id, e.title, false); Result.failure()
        }
    }

    private class LinkExpired : IOException("Link expired")

    /** One-shot "Saved" / "Failed" notification; tapping opens the Downloads tab. Skipped if notifications are denied. */
    @android.annotation.SuppressLint("MissingPermission") // checked above for API 33+
    private fun notifyResult(id: String, title: String, ok: Boolean) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("done", "Completed downloads", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, id.hashCode(), Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "downloads")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, "done").setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "Saved to gallery" else "Download failed").setContentText(title)
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id.hashCode() + 1, n)
    }

    companion object {
        fun enqueue(ctx: Context, id: String, wifiOnly: Boolean) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("id" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("dl_$id", ExistingWorkPolicy.REPLACE, req)
        }
        fun cancel(ctx: Context, id: String) { WorkManager.getInstance(ctx).cancelUniqueWork("dl_$id") }
    }
}
