package com.savr.app.data

import com.google.gson.annotations.SerializedName
import com.savr.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException

enum class ErrorKind(val title: String, val message: String) {
    Invalid("Invalid link", "That doesn't look like a valid link. Check it and try again."),
    Unsupported("Unsupported link", "Paste an Instagram, Facebook or YouTube link, or a direct link to a video, image or audio file."),
    Private("Private post", "This post is private or has been removed, so it cannot be saved."),
    Network("No connection", "Check your internet connection and try again."),
    Server("Something went wrong", "We could not fetch this video right now. Please try again later."),
    Unavailable("Link not available", "This file has been removed or can't be accessed. Check the link and try again."),
    NotConfigured("Service unavailable", "Saving from this platform isn't available right now. Direct video, image and audio links still work.")
}
class ApiError(val kind: ErrorKind) : Exception(kind.title)

data class ExtractRequest(val url: String)
data class QualityDto(val label: String, val url: String, val bytes: Long?)
data class ExtractResponse(val title: String?, val caption: String?, val hashtags: List<String>?, val thumbnail: String?,
                           @SerializedName("duration") val duration: Int?, val type: String?, val qualities: List<QualityDto>?)

interface ExtractApi { @POST("v1/extract") suspend fun extract(@Body body: ExtractRequest): ExtractResponse }

object ExtractRepository {
    private val backendConfigured get() = !BuildConfig.API_BASE_URL.contains("example.com")
    private val mediaExt = Regex("""\.(mp4|m4v|mov|webm|mkv|3gp|jpg|jpeg|png|webp|gif|mp3|m4a|aac|ogg|wav)$""", RegexOption.IGNORE_CASE)

    suspend fun extract(url: String): MediaInfo {
        if (!url.startsWith("http") || runCatching { java.net.URI(url).host }.getOrNull().isNullOrBlank()) throw ApiError(ErrorKind.Invalid)
        val p = detectPlatform(url)
        if (p == Plat.Unknown || p == Plat.WhatsApp) return direct(url) // any public file link works without the backend
        if (!backendConfigured) throw ApiError(ErrorKind.NotConfigured)
        try {
            val r = ServiceLocator.api.extract(ExtractRequest(url))
            val q = r.qualities.orEmpty().map { Quality(it.label, it.url, it.bytes) }
            if (q.isEmpty()) throw ApiError(ErrorKind.Server)
            return MediaInfo(url, p, r.title.orEmpty(), r.caption.orEmpty(), r.thumbnail.orEmpty(), r.hashtags.orEmpty(), q, r.duration ?: 0, r.type ?: "video")
        } catch (e: HttpException) {
            throw ApiError(when (e.code()) { 400 -> ErrorKind.Invalid; 422 -> ErrorKind.Unsupported; 403, 404 -> ErrorKind.Private; else -> ErrorKind.Server })
        } catch (e: IOException) { throw ApiError(ErrorKind.Network) }
    }

    /** Direct file link (e.g. https://site/clip.mp4): resolve type/size with a HEAD request, no backend needed. */
    private suspend fun direct(url: String): MediaInfo = withContext(Dispatchers.IO) {
        // HEAD first (cheap); some servers reject HEAD, so fall back to a 1-byte ranged GET
        fun probe(head: Boolean) = ServiceLocator.http.newCall(Request.Builder().url(url)
            .apply { if (head) head() else header("Range", "bytes=0-0") }.build()).execute().use { r ->
            val len = if (r.code == 206) r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() else r.header("Content-Length")?.toLongOrNull()
            Triple(r.code, r.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase(), len) }
        val (mime, bytes) = try {
            var (code, type, len) = probe(head = true)
            if (code >= 400) probe(head = false).let { code = it.first; type = it.second; len = it.third }
            if (code >= 400) throw ApiError(ErrorKind.Unavailable)
            type to len
        } catch (e: ApiError) { throw e } catch (e: IOException) { throw ApiError(ErrorKind.Network) }
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        val type = when {
            mime.startsWith("video") -> "video"; mime.startsWith("image") -> "image"; mime.startsWith("audio") -> "audio"
            mediaExt.containsMatchIn(path) -> when (path.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg", "png", "webp", "gif" -> "image"; "mp3", "m4a", "aac", "ogg", "wav" -> "audio"; else -> "video" }
            else -> throw ApiError(ErrorKind.Unsupported) // a web page, not a media file
        }
        val name = path.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace('-', ' ').ifBlank { "Download" }
        val label = if (type == "audio") "Audio" else "Original"
        MediaInfo(url, Plat.Web, java.net.URLDecoder.decode(name, "UTF-8").take(80), "", if (type == "image") url else "", emptyList(),
            listOf(Quality(label, url, bytes)), 0, type)
    }
}
