package com.savr.app.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savr.app.data.*
import com.savr.app.work.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed interface ExtractState {
    data object Loading : ExtractState
    data class Ready(val info: MediaInfo) : ExtractState
    data class Failed(val kind: ErrorKind) : ExtractState
}

class ExtractVm(app: Application) : AndroidViewModel(app) {
    val state = MutableStateFlow<ExtractState>(ExtractState.Loading)
    fun load(url: String) = viewModelScope.launch {
        state.value = ExtractState.Loading
        state.value = try { ExtractState.Ready(ExtractRepository.extract(url)) }
        catch (e: ApiError) { ExtractState.Failed(e.kind) } catch (e: Exception) { ExtractState.Failed(ErrorKind.Server) }
    }
}

class SettingsVm(app: Application) : AndroidViewModel(app) {
    private val store = ServiceLocator.settings
    val settings: StateFlow<Settings?> = store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun update(f: (Settings) -> Settings) { viewModelScope.launch { store.update(f) } }
}

class DownloadsVm(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val dao = ServiceLocator.db.dao()
    val items: StateFlow<List<DownloadEntity>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun start(info: MediaInfo, q: Quality, wifiOnly: Boolean) = viewModelScope.launch {
        val id = System.nanoTime().toString(); val audio = q.label == "Audio"
        val tmp = (ctx.getExternalFilesDir("tmp") ?: ctx.cacheDir)
        val e = DownloadEntity(id, info.title.ifEmpty { "Video" }, info.platform.label, if (audio) "audio" else info.type, info.caption,
            info.hashtags.asHashtagString(), info.thumbnail, info.sourceUrl, q.url, "", "QUEUED", 0f, info.durationSec, System.currentTimeMillis())
        dao.upsert(e.copy(path = File(tmp, "$id.part").path)); DownloadWorker.enqueue(ctx, id, wifiOnly)
    }
    fun pause(e: DownloadEntity) { DownloadWorker.cancel(ctx, e.id) }
    fun resume(e: DownloadEntity, wifiOnly: Boolean) = DownloadWorker.enqueue(ctx, e.id, wifiOnly)
    fun remove(e: DownloadEntity) = viewModelScope.launch {
        DownloadWorker.cancel(ctx, e.id); File(e.path).delete()
        if (e.mediaUri.isNotEmpty()) MediaSaver.delete(ctx, e.mediaUri); dao.delete(e.id) }
    fun setCaption(e: DownloadEntity, c: String) = viewModelScope.launch { dao.setCaption(e.id, c) }
}

class StatusVm(app: Application) : AndroidViewModel(app) {
    val files = MutableStateFlow<List<StatusFile>>(emptyList()); val loading = MutableStateFlow(false)
    val saved = MutableStateFlow<Set<String>>(emptySet()) // statuses saved this session (shows a check mark)
    /** quiet = background refresh on resume: no skeleton flash. */
    fun refresh(trees: Set<String>, quiet: Boolean = false) = viewModelScope.launch {
        if (!quiet || files.value.isEmpty()) loading.value = true
        files.value = StatusRepo.list(getApplication(), trees); loading.value = false }
    // Copying a video can take seconds: run off the main thread
    suspend fun save(f: StatusFile): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { MediaSaver.save(getApplication(), android.net.Uri.parse(f.uri), f.name, f.mime, "$FOLDER/Status") != null }.getOrDefault(false)
            .also { if (it) saved.value = saved.value + f.uri } }
}
