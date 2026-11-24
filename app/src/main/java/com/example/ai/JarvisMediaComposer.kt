package com.example.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Handles saving generated AI images, wrapping Gemini TTS L16 PCM bytes into valid RIFF/WAV files,
 * and compiling multi-scene AI keyframes + narration into playable 720p H.264/AAC MP4 videos via MediaCodec + MediaMuxer.
 */
object JarvisMediaComposer {

    private fun mediaDir(context: Context): File {
        val dir = File(context.filesDir, "jarvis_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "jarvis_img"
    ): File = withContext(Dispatchers.IO) {
        val file = File(mediaDir(context), "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        file
    }

    suspend fun writeAudioToPlayableFile(
        context: Context,
        rawBytes: ByteArray,
        mimeType: String,
        prefix: String = "jarvis_audio"
    ): Pair<File, Int> = withContext(Dispatchers.IO) {
        val lowerMime = mimeType.lowercase()
        val isWavHeaderAlreadyPresent = rawBytes.size >= 12 &&
            rawBytes[0] == 'R'.code.toByte() &&
            rawBytes[1] == 'I'.code.toByte() &&
            rawBytes[2] == 'F'.code.toByte() &&
            rawBytes[3] == 'F'.code.toByte()

        val sampleRate = Regex("rate=(\\d+)").find(lowerMime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000

        return@withContext when {
            isWavHeaderAlreadyPresent -> {
                val file = File(mediaDir(context), "${prefix}_${System.currentTimeMillis()}.wav")
                file.writeBytes(rawBytes)
                val durationSec = ((rawBytes.size - 44).coerceAtLeast(0) / (sampleRate * 2f)).roundToInt().coerceAtLeast(1)
                file to durationSec
            }
            lowerMime.contains("l16") || lowerMime.contains("pcm") || lowerMime.contains("wav") -> {
                val wavBytes = wrapPcm16MonoToWav(rawBytes, sampleRate)
                val file = File(mediaDir(context), "${prefix}_${System.currentTimeMillis()}.wav")
                file.writeBytes(wavBytes)
                val durationSec = (rawBytes.size / (sampleRate * 2f)).roundToInt().coerceAtLeast(1)
                file to durationSec
            }
            else -> {
                val ext = when {
                    lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> "mp3"
                    lowerMime.contains("ogg") -> "ogg"
                    lowerMime.contains("aac") || lowerMime.contains("mp4") -> "m4a"
                    else -> "wav"
                }
                val file = File(mediaDir(context), "${prefix}_${System.currentTimeMillis()}.$ext")
                val finalBytes = if (ext == "wav") wrapPcm16MonoToWav(rawBytes, sampleRate) else rawBytes
                file.writeBytes(finalBytes)
                val durationSec = (rawBytes.size / (sampleRate * 2f)).roundToInt().coerceAtLeast(1)
                file to durationSec
            }
        }
    }

    fun wrapPcm16MonoToWav(pcmData: ByteArray, sampleRate: Int = 24000): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalDataLen = dataSize + 36

        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
        out.write(header)
        out.write(pcmData)
        return out.toByteArray()
    }

    /**
     * Compiles AI-generated storyboard keyframe [Bitmap] scenes with smooth Ken Burns zoom/pan
     * + cinematic captions + optional WAV narration into a genuine playable H.264/AAC `.mp4` file.
     */
    suspend fun composeAiVideoMp4(
        context: Context,
        scenes: List<Pair<Bitmap, String>>,
        title: String,
        narrationPcmBytes: ByteArray? = null,
        narrationSampleRate: Int = 24000,
        secondsPerScene: Int = 3
    ): Pair<File, Int> = withContext(Dispatchers.IO) {
        require(scenes.isNotEmpty()) { "At least one AI scene keyframe is required to render MP4 video." }

        val width = 1280
        val height = 720
        val fps = 20
        val framesPerScene = (secondsPerScene * fps).coerceAtLeast(fps * 2)
        val totalFrames = scenes.size * framesPerScene
        val totalDurationSec = (totalFrames / fps).coerceAtLeast(3)

        val videoFile = File(mediaDir(context), "jarvis_video_${System.currentTimeMillis()}.mp4")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_800_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(videoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        // Optional AAC audio track preparation
        var audioCodec: MediaCodec? = null
        var audioBufferReady = false
        val cleanPcm = narrationPcmBytes?.let { raw ->
            if (raw.size >= 44 && raw[0] == 'R'.code.toByte() && raw[1] == 'I'.code.toByte()) {
                raw.copyOfRange(44, raw.size)
            } else raw
        }

        if (cleanPcm != null && cleanPcm.size > 2048) {
            runCatching {
                val audioFmt = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    narrationSampleRate,
                    1
                ).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(audioFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
                audioBufferReady = true
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps

            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 34f
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFB74D")
                textSize = 24f
                isFakeBoldText = true
            }
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            for (sceneIndex in scenes.indices) {
                val (sceneBmp, caption) = scenes[sceneIndex]
                val zoomIn = sceneIndex % 2 == 0
                for (f in 0 until framesPerScene) {
                    val globalFrame = sceneIndex * framesPerScene + f
                    val progress = f.toFloat() / (framesPerScene - 1).coerceAtLeast(1)
                    val scale = if (zoomIn) 1.0f + 0.08f * progress else 1.08f - 0.08f * progress

                    val canvas: Canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawColor(Color.parseColor("#0D1117"))
                        drawKenBurnsFrame(canvas, sceneBmp, width, height, scale, progress)
                        drawCinematicOverlay(canvas, width, height, title, caption, barPaint, titlePaint, captionPaint)
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    // Drain available encoded video frames
                    drainEncoder(
                        codec = codec,
                        bufferInfo = bufferInfo,
                        endOfStream = false,
                        onFormatChanged = { outFormat ->
                            if (videoTrackIndex == -1) {
                                videoTrackIndex = muxer.addTrack(outFormat)
                                if (!audioBufferReady || audioTrackIndex != -1) {
                                    muxer.start()
                                    muxerStarted = true
                                }
                            }
                        },
                        onSample = { encodedBuf, info ->
                            if (muxerStarted && videoTrackIndex != -1) {
                                info.presentationTimeUs = globalFrame * frameDurationUs
                                muxer.writeSampleData(videoTrackIndex, encodedBuf, info)
                            }
                        }
                    )
                }
            }

            codec.signalEndOfInputStream()
            drainEncoder(
                codec = codec,
                bufferInfo = bufferInfo,
                endOfStream = true,
                onFormatChanged = { outFormat ->
                    if (videoTrackIndex == -1) {
                        videoTrackIndex = muxer.addTrack(outFormat)
                        if (!audioBufferReady || audioTrackIndex != -1) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                },
                onSample = { encodedBuf, info ->
                    if (muxerStarted && videoTrackIndex != -1) {
                        muxer.writeSampleData(videoTrackIndex, encodedBuf, info)
                    }
                }
            )

            // Encode & write AAC audio narration if present
            if (audioBufferReady && audioCodec != null && cleanPcm != null) {
                encodeAacIntoMuxer(
                    audioCodec = audioCodec!!,
                    pcmBytes = cleanPcm,
                    sampleRate = narrationSampleRate,
                    muxer = muxer,
                    getMuxerStarted = { muxerStarted },
                    onStartMuxer = { aFmt ->
                        audioTrackIndex = muxer.addTrack(aFmt)
                        if (videoTrackIndex != -1 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                        audioTrackIndex
                    }
                )
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { audioCodec?.stop() }
            runCatching { audioCodec?.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }

        videoFile to totalDurationSec
    }

    private fun drawKenBurnsFrame(
        canvas: Canvas,
        bmp: Bitmap,
        targetW: Int,
        targetH: Int,
        zoom: Float,
        progress: Float
    ) {
        val srcAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        val dstAspect = targetW.toFloat() / targetH
        val baseW: Float
        val baseH: Float
        if (srcAspect > dstAspect) {
            baseH = targetH.toFloat()
            baseW = baseH * srcAspect
        } else {
            baseW = targetW.toFloat()
            baseH = baseW / srcAspect
        }
        val drawW = baseW * zoom
        val drawH = baseH * zoom
        val panX = (targetW - drawW) * (0.35f + 0.3f * progress)
        val panY = (targetH - drawH) * 0.5f
        val dstRect = Rect(
            panX.roundToInt(),
            panY.roundToInt(),
            (panX + drawW).roundToInt(),
            (panY + drawH).roundToInt()
        )
        canvas.drawBitmap(bmp, null, dstRect, null)
    }

    private fun drawCinematicOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        title: String,
        caption: String,
        barPaint: Paint,
        titlePaint: Paint,
        captionPaint: Paint
    ) {
        barPaint.shader = LinearGradient(
            0f,
            height * 0.65f,
            0f,
            height.toFloat(),
            Color.TRANSPARENT,
            Color.argb(210, 12, 14, 18),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height * 0.65f, width.toFloat(), height.toFloat(), barPaint)
        barPaint.shader = null
        canvas.drawText("J.A.R.V.I.S STUDIO • ${title.take(42)}", 44f, height - 92f, titlePaint)
        canvas.drawText(caption.take(68), 44f, height - 46f, captionPaint)
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatChanged: (MediaFormat) -> Unit,
        onSample: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatChanged(codec.outputFormat)
                }
                outIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outIndex)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        onSample(encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun encodeAacIntoMuxer(
        audioCodec: MediaCodec,
        pcmBytes: ByteArray,
        sampleRate: Int,
        muxer: MediaMuxer,
        getMuxerStarted: () -> Boolean,
        onStartMuxer: (MediaFormat) -> Int
    ) {
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var trackIndex = -1
        var totalSamplesFed = 0L
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = audioCodec.dequeueInputBuffer(5_000L)
                if (inIdx >= 0) {
                    val buf = audioCodec.getInputBuffer(inIdx)
                    if (buf != null) {
                        buf.clear()
                        if (offset >= pcmBytes.size) {
                            audioCodec.queueInputBuffer(inIdx, 0, 0, (totalSamplesFed * 1_000_000L) / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunkSize = minOf(buf.capacity(), pcmBytes.size - offset)
                            buf.put(pcmBytes, offset, chunkSize)
                            val ptsUs = (totalSamplesFed * 1_000_000L) / sampleRate
                            audioCodec.queueInputBuffer(inIdx, 0, chunkSize, ptsUs, 0)
                            offset += chunkSize
                            totalSamplesFed += (chunkSize / 2)
                        }
                    }
                }
            }

            val outIdx = audioCodec.dequeueOutputBuffer(info, 5_000L)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = onStartMuxer(audioCodec.outputFormat)
                }
                outIdx >= 0 -> {
                    val outBuf = audioCodec.getOutputBuffer(outIdx)
                    if (outBuf != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && getMuxerStarted() && trackIndex != -1) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, outBuf, info)
                    }
                    audioCodec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }
    }

    fun createShareFileIntent(context: Context, filePath: String, mimeType: String, title: String): Intent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
