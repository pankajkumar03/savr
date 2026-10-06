package com.savr.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Entity
import androidx.room.PrimaryKey

const val FOLDER = "Savr" // <- your [APP NAME]; gallery folder

enum class Plat(val label: String, val icon: ImageVector) {
    Instagram("Instagram", Icons.Outlined.CameraAlt), Facebook("Facebook", Icons.Outlined.Groups),
    YouTube("YouTube", Icons.Outlined.SmartDisplay), WhatsApp("WhatsApp", Icons.Outlined.ChatBubbleOutline),
    Web("Web", Icons.Outlined.Public),
    Unknown("Unknown", Icons.Outlined.Link) // generic icons, no official logos
}

private val urlRe = Regex("https?://\\S+")
fun extractUrl(text: String): String? = urlRe.find(text)?.value

fun detectPlatform(url: String): Plat {
    val h = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        "instagram.com" in h -> Plat.Instagram
        "facebook.com" in h || "fb.watch" in h || "fb.com" in h -> Plat.Facebook
        "youtube.com" in h || "youtu.be" in h -> Plat.YouTube
        "whatsapp.com" in h || "wa.me" in h -> Plat.WhatsApp
        else -> Plat.Unknown
    }
}

data class Quality(val label: String, val url: String, val bytes: Long? = null) // HD | SD | Audio
data class MediaInfo(val sourceUrl: String, val platform: Plat, val title: String, val caption: String, val thumbnail: String,
                     val hashtags: List<String>, val qualities: List<Quality>, val durationSec: Int, val type: String)

fun List<String>.asHashtagString() = joinToString(" ") { if (it.startsWith("#")) it else "#$it" }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String, val title: String, val platform: String, val type: String, // video | image | audio
    val caption: String, val hashtags: String, val thumb: String, val sourceUrl: String, val remoteUrl: String,
    val path: String, val status: String, val progress: Float, val durationSec: Int, val createdAt: Long,
    val mediaUri: String = "") {
    private val remoteExt get() = runCatching { java.net.URI(remoteUrl).path.substringAfterLast('.', "").lowercase() }.getOrDefault("")
    val ext get() = when (type) {
        "audio" -> remoteExt.takeIf { it in setOf("mp3", "m4a", "aac", "ogg", "wav") } ?: "m4a"
        "image" -> remoteExt.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
        else -> remoteExt.takeIf { it in setOf("mp4", "webm", "mkv", "mov", "3gp") } ?: "mp4" }
    val mime get() = when (ext) {
        "mp3" -> "audio/mpeg"; "m4a", "aac" -> "audio/mp4"; "ogg" -> "audio/ogg"; "wav" -> "audio/wav"
        "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "jpg", "jpeg" -> "image/jpeg"
        "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; else -> "video/mp4" }
    val fileName get() = title.replace(Regex("[^\\w\\- ]"), "").trim().ifEmpty { "media" }.take(60) + "_$id.$ext"
    val isDone get() = status == "DONE"
    val thumbModel: Any? get() = if (type == "audio") thumb.ifEmpty { null } else mediaUri.ifEmpty { thumb.ifEmpty { null } }
}

data class StatusFile(val uri: String, val name: String, val mime: String, val modified: Long) { val isVideo get() = mime.startsWith("video") }
