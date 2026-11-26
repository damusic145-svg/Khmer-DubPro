package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class ImportedVideoInfo(
    val title: String,
    val localVideoFile: File,
    val thumbnailFile: File?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val hasEmbeddedSubtitles: Boolean,
    val embeddedSubtitleHints: String
)

object VideoInspector {

    suspend fun importAndInspectVideo(
        context: Context,
        sourceUri: Uri,
        projectFolderId: String
    ): ImportedVideoInfo = withContext(Dispatchers.IO) {
        val projectDir = File(context.filesDir, "projects/$projectFolderId").apply { mkdirs() }
        val localVideoFile = File(projectDir, "source_video.mp4")

        val displayTitle = resolveDisplayName(context, sourceUri)
            ?.substringBeforeLast(".")
            ?.ifBlank { "Untitled Video" }
            ?: "Video Project"

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(localVideoFile).use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        } ?: throw IllegalStateException("Unable to open selected video stream.")

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 1920
        var height = 1080
        var fps = 30f
        var thumbFile: File? = null

        try {
            retriever.setDataSource(localVideoFile.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1920
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1080
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull() ?: 30f
            }

            val frameTimeUs = if (durationMs > 2000L) 1_500_000L else 0L
            val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val outThumb = File(projectDir, "thumb.jpg")
                FileOutputStream(outThumb).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                }
                bitmap.recycle()
                thumbFile = outThumb
            }
        } finally {
            runCatching { retriever.release() }
        }

        val (hasSubs, subHints) = extractEmbeddedSubtitles(localVideoFile.absolutePath)

        ImportedVideoInfo(
            title = displayTitle,
            localVideoFile = localVideoFile,
            thumbnailFile = thumbFile,
            durationMs = durationMs,
            width = width,
            height = height,
            fps = fps,
            hasEmbeddedSubtitles = hasSubs,
            embeddedSubtitleHints = subHints
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun extractEmbeddedSubtitles(videoPath: String): Pair<Boolean, String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(videoPath)
            val subtitleTracks = mutableListOf<Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("text/") || mime.contains("subrip") || mime.contains("ttml") || mime.contains("cea")) {
                    subtitleTracks.add(i)
                }
            }
            if (subtitleTracks.isEmpty()) return false to ""

            val trackIndex = subtitleTracks.first()
            extractor.selectTrack(trackIndex)
            val buffer = ByteBuffer.allocate(64 * 1024)
            val sb = StringBuilder()
            var count = 0
            while (count < 40) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val bytes = ByteArray(size)
                buffer.get(bytes)
                val cue = String(bytes, Charsets.UTF_8).trim()
                if (cue.isNotBlank()) {
                    val timeMs = extractor.sampleTime / 1000L
                    sb.append("[${timeMs}ms] $cue\n")
                    count++
                }
                buffer.clear()
                extractor.advance()
            }
            true to sb.toString().take(4000)
        } catch (_: Exception) {
            false to ""
        } finally {
            runCatching { extractor.release() }
        }
    }
}
