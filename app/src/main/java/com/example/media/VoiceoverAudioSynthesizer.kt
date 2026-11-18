package com.example.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.MusicMood
import com.example.data.model.VideoStoryboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "VoiceoverSynth"
const val AUDIO_SAMPLE_RATE = 44100

class VoiceoverAudioSynthesizer(private val context: Context) {

    suspend fun synthesizeTrackPcm(
        storyboard: VideoStoryboard,
        includeVoiceover: Boolean,
        tempDir: File
    ): ShortArray = withContext(Dispatchers.IO) {
        val totalSeconds = storyboard.totalDurationSeconds.coerceAtLeast(1)
        val totalSamples = totalSeconds * AUDIO_SAMPLE_RATE
        val mixed = ShortArray(totalSamples)

        // 1. Base ambient musical pad tailored to musicMood
        val ambientPad = generateAmbientPad(storyboard.musicMood, totalSamples)

        // 2. Synthesize spoken voiceover per scene via Android TextToSpeech if enabled
        val voiceBuffer = FloatArray(totalSamples)
        if (includeVoiceover) {
            val ttsFiles = synthesizeAllScenesWithTts(storyboard, tempDir)
            var cursorSample = 0
            storyboard.scenes.forEachIndexed { index, scene ->
                val sceneSamples = scene.durationSeconds * AUDIO_SAMPLE_RATE
                val wavFile = ttsFiles.getOrNull(index)
                if (wavFile != null && wavFile.exists()) {
                    val speech = readWavAs44100MonoFloat(wavFile)
                    val offset = (cursorSample + (AUDIO_SAMPLE_RATE * 0.25f).toInt())
                        .coerceAtMost(totalSamples)
                    val copyLen = minOf(speech.size, totalSamples - offset, sceneSamples)
                    for (i in 0 until copyLen) {
                        voiceBuffer[offset + i] = speech[i]
                    }
                    wavFile.delete()
                }
                cursorSample += sceneSamples
            }
        }

        // 3. Mix ambient music + speech with smooth ducking when speech is active
        for (i in 0 until totalSamples) {
            val speechSample = voiceBuffer[i]
            val duckFactor = if (kotlin.math.abs(speechSample) > 0.015f) 0.28f else 0.72f
            val combined = (ambientPad[i] * duckFactor + speechSample * 0.92f).coerceIn(-0.98f, 0.98f)
            mixed[i] = (combined * Short.MAX_VALUE).toInt().toShort()
        }

        mixed
    }

    private suspend fun synthesizeAllScenesWithTts(
        storyboard: VideoStoryboard,
        tempDir: File
    ): List<File?> {
        if (storyboard.scenes.all { it.narration.isBlank() }) return emptyList()

        return withTimeoutOrNull(25_000L) {
            val tts = initTts() ?: return@withTimeoutOrNull emptyList()
            try {
                tts.language = Locale.US
                tts.setSpeechRate(1.02f)
                storyboard.scenes.mapIndexed { index, scene ->
                    if (scene.narration.isBlank()) {
                        null
                    } else {
                        val outFile = File(tempDir, "tts_scene_$index.wav")
                        val ok = synthesizeLine(tts, scene.narration, outFile, "utt_$index")
                        if (ok && outFile.exists() && outFile.length() > 44) outFile else null
                    }
                }
            } finally {
                tts.stop()
                tts.shutdown()
            }
        } ?: emptyList()
    }

    private suspend fun initTts(): TextToSpeech? = suspendCancellableCoroutine { cont ->
        var ttsRef: TextToSpeech? = null
        ttsRef = TextToSpeech(context.applicationContext) { status ->
            if (cont.isActive) {
                if (status == TextToSpeech.SUCCESS) {
                    cont.resume(ttsRef)
                } else {
                    Log.w(TAG, "TTS initialization returned status=$status")
                    ttsRef?.shutdown()
                    cont.resume(null)
                }
            }
        }
        cont.invokeOnCancellation {
            ttsRef?.shutdown()
        }
    }

    private suspend fun synthesizeLine(
        tts: TextToSpeech,
        text: String,
        outFile: File,
        utteranceId: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }
        })
        val res = tts.synthesizeToFile(text, null, outFile, utteranceId)
        if (res != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resume(false)
        }
    }

    private fun generateAmbientPad(mood: MusicMood, totalSamples: Int): FloatArray {
        val out = FloatArray(totalSamples)
        val chords: Array<FloatArray> = when (mood) {
            MusicMood.AMBIENT_SYNTH -> arrayOf(
                floatArrayOf(130.81f, 196.00f, 246.94f), // Cmaj7
                floatArrayOf(110.00f, 164.81f, 220.00f), // Am
                floatArrayOf(87.31f, 130.81f, 174.61f),  // Fmaj
                floatArrayOf(98.00f, 146.83f, 196.00f)   // G
            )
            MusicMood.WARM_PIANO -> arrayOf(
                floatArrayOf(261.63f, 329.63f, 392.00f),
                floatArrayOf(220.00f, 261.63f, 329.63f),
                floatArrayOf(174.61f, 220.00f, 261.63f),
                floatArrayOf(196.00f, 246.94f, 293.66f)
            )
            MusicMood.EPIC_PULSE -> arrayOf(
                floatArrayOf(73.42f, 146.83f, 220.00f),  // Dm
                floatArrayOf(58.27f, 116.54f, 174.61f),  // Bb
                floatArrayOf(65.41f, 130.81f, 196.00f),  // C
                floatArrayOf(55.00f, 110.00f, 164.81f)   // A
            )
            MusicMood.LO_FI_CHILL -> arrayOf(
                floatArrayOf(146.83f, 220.00f, 261.63f),
                floatArrayOf(196.00f, 246.94f, 293.66f),
                floatArrayOf(130.81f, 196.00f, 246.94f),
                floatArrayOf(164.81f, 246.94f, 293.66f)
            )
        }
        val chordDurationSamples = AUDIO_SAMPLE_RATE * 4
        val fadeSamples = (AUDIO_SAMPLE_RATE * 0.6f).toInt()

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / AUDIO_SAMPLE_RATE
            val chordIdx = (i / chordDurationSamples) % chords.size
            val freqs = chords[chordIdx]
            val localPos = i % chordDurationSamples
            val chordEnv = when {
                localPos < fadeSamples -> localPos.toFloat() / fadeSamples
                localPos > chordDurationSamples - fadeSamples -> (chordDurationSamples - localPos).toFloat() / fadeSamples
                else -> 1f
            }.coerceIn(0f, 1f)

            var wave = 0.0
            for (f in freqs) {
                wave += sin(2.0 * PI * f * t) * 0.33
            }
            val masterEnv = when {
                i < AUDIO_SAMPLE_RATE -> i.toFloat() / AUDIO_SAMPLE_RATE
                i > totalSamples - AUDIO_SAMPLE_RATE -> (totalSamples - i).toFloat() / AUDIO_SAMPLE_RATE
                else -> 1f
            }.coerceIn(0f, 1f)
            out[i] = (wave * 0.22 * chordEnv * masterEnv).toFloat()
        }
        return out
    }

    private fun readWavAs44100MonoFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        if (bytes.size < 44) return FloatArray(0)
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        val channels = header.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = header.getInt(24).coerceAtLeast(8000)
        val bitsPerSample = header.getShort(34).toInt().coerceAtLeast(16)

        // Locate 'data' chunk robustly
        var pos = 12
        var dataStart = 44
        var dataLen = bytes.size - 44
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                dataStart = pos + 8
                dataLen = chunkSize.coerceAtMost(bytes.size - dataStart)
                break
            }
            pos += 8 + chunkSize
        }

        if (bitsPerSample != 16 || dataLen <= 0) return FloatArray(0)
        val frameCount = dataLen / (2 * channels)
        val monoSrc = FloatArray(frameCount)
        val bb = ByteBuffer.wrap(bytes, dataStart, dataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += bb.short / 32768f
            }
            monoSrc[i] = sum / channels
        }

        if (sampleRate == AUDIO_SAMPLE_RATE) return monoSrc
        val ratio = sampleRate.toDouble() / AUDIO_SAMPLE_RATE.toDouble()
        val outLen = (frameCount / ratio).toInt().coerceAtLeast(1)
        return FloatArray(outLen) { idx ->
            val srcPos = idx * ratio
            val i0 = srcPos.toInt().coerceIn(0, frameCount - 1)
            val i1 = (i0 + 1).coerceIn(0, frameCount - 1)
            val frac = (srcPos - i0).toFloat()
            monoSrc[i0] * (1f - frac) + monoSrc[i1] * frac
        }
    }
}
