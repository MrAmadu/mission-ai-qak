package com.example.media

import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.data.model.VideoStoryboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

private const val TAG = "Mp4VideoEncoder"
private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
private const val FPS = 24
private const val TIMEOUT_US = 10_000L

class Mp4VideoEncoder {

    private data class EncodedSample(
        val data: ByteArray,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )

    suspend fun encodeVideo(
        outputFile: File,
        width: Int,
        height: Int,
        storyboard: VideoStoryboard,
        keyframes: List<Bitmap?>?,
        audioPcm: ShortArray?,
        onProgress: (fraction: Float, previewBitmap: Bitmap?) -> Unit
    ): Long = withContext(Dispatchers.Default) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val encodedAudio = if (audioPcm != null && audioPcm.isNotEmpty()) {
            runCatching { encodeAudioToAac(audioPcm) }.onFailure {
                Log.w(TAG, "Audio AAC encode skipped, writing video-only MP4", it)
            }.getOrNull()
        } else null

        val renderer = MotionSceneRenderer(width, height)
        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val argbPixels = IntArray(width * height)

        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 3_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        try {
            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoCodec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val totalFrames = storyboard.scenes.sumOf { it.durationSeconds * FPS }.coerceAtLeast(1)
            var globalFrame = 0
            var inputDone = false
            var outputDone = false

            // Build flat frame schedule
            val frameSchedule = buildList {
                storyboard.scenes.forEachIndexed { sceneIdx, scene ->
                    val count = (scene.durationSeconds * FPS).coerceAtLeast(1)
                    for (f in 0 until count) {
                        add(sceneIdx to (f.toFloat() / count.toFloat()))
                    }
                }
            }

            while (!outputDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inIndex = videoCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        if (globalFrame >= frameSchedule.size) {
                            val ptsUs = (globalFrame.toLong() * 1_000_000L) / FPS
                            videoCodec.queueInputBuffer(
                                inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val (sceneIdx, progress) = frameSchedule[globalFrame]
                            renderer.renderFrameInto(frameBitmap, storyboard, sceneIdx, progress, keyframes)
                            frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

                            val image: Image? = runCatching { videoCodec.getInputImage(inIndex) }.getOrNull()
                            val byteSize: Int
                            if (image != null) {
                                byteSize = writeArgbToImage(argbPixels, width, height, image)
                                image.close()
                            } else {
                                val buf = videoCodec.getInputBuffer(inIndex)!!
                                byteSize = writeArgbToI420Buffer(argbPixels, width, height, buf)
                            }

                            val ptsUs = (globalFrame.toLong() * 1_000_000L) / FPS
                            videoCodec.queueInputBuffer(inIndex, 0, byteSize, ptsUs, 0)

                            if (globalFrame % 6 == 0 || globalFrame == frameSchedule.lastIndex) {
                                val previewCopy = frameBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                onProgress((globalFrame + 1).toFloat() / totalFrames, previewCopy)
                            }
                            globalFrame++
                        }
                    }
                }

                val outIndex = videoCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrackIndex = muxer.addTrack(videoCodec.outputFormat)
                        if (encodedAudio != null) {
                            audioTrackIndex = muxer.addTrack(encodedAudio.first)
                        }
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = videoCodec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (muxerStarted) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, outBuf, bufferInfo)
                            }
                        }
                        videoCodec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (muxerStarted && encodedAudio != null && audioTrackIndex >= 0) {
                val sampleInfo = MediaCodec.BufferInfo()
                for (sample in encodedAudio.second) {
                    sampleInfo.set(0, sample.size, sample.presentationTimeUs, sample.flags)
                    muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(sample.data, 0, sample.size), sampleInfo)
                }
            }

            (storyboard.totalDurationSeconds * 1000L)
        } finally {
            frameBitmap.recycle()
            runCatching { videoCodec.stop() }
            runCatching { videoCodec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun encodeAudioToAac(pcm: ShortArray): Pair<MediaFormat, List<EncodedSample>> {
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, AUDIO_SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
        val samples = mutableListOf<EncodedSample>()
        var outputFormat: MediaFormat? = null

        try {
            codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val chunkSamples = 1024
            var cursor = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        buf.clear()
                        if (cursor >= pcm.size) {
                            val ptsUs = (cursor.toLong() * 1_000_000L) / AUDIO_SAMPLE_RATE
                            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val count = minOf(chunkSamples, pcm.size - cursor, buf.remaining() / 2)
                            for (i in 0 until count) {
                                val s = pcm[cursor + i].toInt()
                                buf.put((s and 0xFF).toByte())
                                buf.put(((s ushr 8) and 0xFF).toByte())
                            }
                            val ptsUs = (cursor.toLong() * 1_000_000L) / AUDIO_SAMPLE_RATE
                            codec.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                            cursor += count
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            outBuf.get(bytes)
                            samples.add(EncodedSample(bytes, info.size, info.presentationTimeUs, info.flags))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        val finalFmt = outputFormat ?: MediaFormat.createAudioFormat(AUDIO_MIME, AUDIO_SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
        }
        return finalFmt to samples
    }

    private fun writeArgbToImage(argb: IntArray, width: Int, height: Int, image: Image): Int {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF

                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yBuf.put(j * yRowStride + i * yPixelStride, yVal.toByte())

                if ((j and 1) == 0 && (i and 1) == 0) {
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val uvRow = j ushr 1
                    val uvCol = i ushr 1
                    uBuf.put(uvRow * uRowStride + uvCol * uPixelStride, uVal.toByte())
                    vBuf.put(uvRow * vRowStride + uvCol * vPixelStride, vVal.toByte())
                }
            }
        }
        return (width * height * 3) / 2
    }

    private fun writeArgbToI420Buffer(argb: IntArray, width: Int, height: Int, buf: ByteBuffer): Int {
        buf.clear()
        val frameSize = width * height
        var yIdx = 0
        var uIdx = frameSize
        var vIdx = frameSize + frameSize / 4

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                buf.put(yIdx++, y.toByte())
                if ((j and 1) == 0 && (i and 1) == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    buf.put(uIdx++, u.toByte())
                    buf.put(vIdx++, v.toByte())
                }
            }
        }
        return (frameSize * 3) / 2
    }
}
