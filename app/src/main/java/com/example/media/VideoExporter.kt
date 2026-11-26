package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.local.DialogueSegmentEntity
import com.example.data.model.AudioTrackMode
import com.example.data.model.ExportResolution
import com.example.data.model.SubtitleExportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExportOutputResult(
    val outputFile: File,
    val mediaStoreUri: Uri?,
    val subtitleFile: File?,
    val fileSizeBytes: Long
)

object VideoExporter {

    @OptIn(UnstableApi::class)
    suspend fun exportDubbedMp4(
        context: Context,
        sourceVideoFile: File,
        mixedAudioWavFile: File?,
        segments: List<DialogueSegmentEntity>,
        outputTitle: String,
        resolution: ExportResolution,
        audioMode: AudioTrackMode,
        subtitleMode: SubtitleExportMode,
        outputDir: File,
        onProgress: (Int) -> Unit
    ): ExportOutputResult {
        outputDir.mkdirs()
        val safeTitle = outputTitle.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40).ifBlank { "khmer_dub" }
        val outputMp4 = File(outputDir, "${safeTitle}_khmer_dubbed.mp4")
        if (outputMp4.exists()) outputMp4.delete()

        val subtitleFile = if (subtitleMode != SubtitleExportMode.NONE) {
            val srt = File(outputDir, "${safeTitle}_subtitles.srt")
            writeSrtFile(srt, segments, bilingual = subtitleMode == SubtitleExportMode.BILINGUAL_SRT)
            srt
        } else null

        val videoEffects = buildList {
            val targetH = resolution.targetHeight
            if (targetH != null) {
                add(Presentation.createForHeight(targetH))
            }
        }

        val removeSourceAudio = audioMode == AudioTrackMode.KHMER_DUBBED && mixedAudioWavFile?.exists() == true
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceVideoFile)))
            .setRemoveAudio(removeSourceAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val composition = if (audioMode != AudioTrackMode.ORIGINAL_ONLY && mixedAudioWavFile != null && mixedAudioWavFile.exists()) {
            val videoSeq = EditedMediaItemSequence(listOf(videoItem))
            val dubbedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(mixedAudioWavFile))).build()
            val audioSeq = EditedMediaItemSequence(listOf(dubbedItem))
            Composition.Builder(listOf(videoSeq, audioSeq)).build()
        } else {
            val videoSeq = EditedMediaItemSequence(listOf(videoItem))
            Composition.Builder(listOf(videoSeq)).build()
        }

        runTransformerOnMainThread(context, composition, outputMp4.absolutePath, onProgress)

        val galleryUri = withContext(Dispatchers.IO) {
            saveVideoToDeviceMovies(context, outputMp4, "${safeTitle}_khmer.mp4")
        }

        return ExportOutputResult(
            outputFile = outputMp4,
            mediaStoreUri = galleryUri,
            subtitleFile = subtitleFile,
            fileSizeBytes = outputMp4.length()
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMainThread(
        context: Context,
        composition: Composition,
        outputPath: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            lateinit var transformer: Transformer
            val progressHolder = ProgressHolder()
            var finished = false

            val poller = object : Runnable {
                override fun run() {
                    if (finished) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    mainHandler.postDelayed(this, 350L)
                }
            }

            transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, result: ExportResult) {
                        finished = true
                        mainHandler.removeCallbacks(poller)
                        onProgress(100)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        comp: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        finished = true
                        mainHandler.removeCallbacks(poller)
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                .build()

            transformer.start(composition, outputPath)
            mainHandler.post(poller)

            cont.invokeOnCancellation {
                finished = true
                mainHandler.removeCallbacks(poller)
                mainHandler.post { runCatching { transformer.cancel() } }
            }
        }
    }

    private fun saveVideoToDeviceMovies(context: Context, mp4File: File, displayName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AIKhmerDubberPro")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(mp4File).use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } else {
                Uri.fromFile(mp4File)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun writeSrtFile(file: File, segments: List<DialogueSegmentEntity>, bilingual: Boolean) {
        val sb = StringBuilder()
        segments.forEachIndexed { idx, seg ->
            sb.append(idx + 1).append('\n')
            sb.append(formatSrtTimestamp(seg.startMs))
                .append(" --> ")
                .append(formatSrtTimestamp(seg.endMs))
                .append('\n')
            if (seg.khmerText.isNotBlank()) {
                sb.append(seg.khmerText).append('\n')
            }
            if (bilingual && seg.originalText.isNotBlank()) {
                sb.append(seg.originalText).append('\n')
            }
            sb.append('\n')
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val mins = (clamped % 3_600_000L) / 60_000L
        val secs = (clamped % 60_000L) / 1000L
        val millis = clamped % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }
}
