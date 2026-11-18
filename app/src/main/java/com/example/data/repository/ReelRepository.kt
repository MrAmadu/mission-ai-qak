package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.ai.AiDirectorService
import com.example.data.local.ReelDao
import com.example.data.local.ReelEntity
import com.example.data.model.AspectRatioOption
import com.example.data.model.GeneratedReel
import com.example.data.model.VideoDurationOption
import com.example.data.model.VideoEngineMode
import com.example.data.model.VideoStoryboard
import com.example.data.model.VisualStyle
import com.example.media.MotionSceneRenderer
import com.example.media.Mp4VideoEncoder
import com.example.media.VoiceoverAudioSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class RenderProgress(
    val stageTitle: String,
    val detail: String,
    val fraction: Float,
    val previewBitmap: Bitmap? = null
)

class ReelRepository(
    private val context: Context,
    private val reelDao: ReelDao,
    private val aiDirector: AiDirectorService = AiDirectorService(),
    private val audioSynth: VoiceoverAudioSynthesizer = VoiceoverAudioSynthesizer(context),
    private val mp4Encoder: Mp4VideoEncoder = Mp4VideoEncoder()
) {
    private val videosRootDir: File
        get() = File(context.filesDir, "videos").apply { mkdirs() }

    fun observeAllReels(): Flow<List<GeneratedReel>> =
        reelDao.observeAllReels().map { list -> list.map { it.toDomain() } }

    fun observeReelById(id: String): Flow<GeneratedReel?> =
        reelDao.observeReelById(id).map { it?.toDomain() }

    suspend fun enhancePrompt(rawPrompt: String, style: VisualStyle): String =
        aiDirector.enhancePrompt(rawPrompt, style)

    suspend fun generateVideo(
        prompt: String,
        style: VisualStyle,
        aspectRatio: AspectRatioOption,
        duration: VideoDurationOption,
        engineMode: VideoEngineMode,
        includeVoiceover: Boolean,
        onProgress: (RenderProgress) -> Unit
    ): GeneratedReel {
        val reelId = UUID.randomUUID().toString()

        onProgress(
            RenderProgress(
                stageTitle = "Directing Storyboard",
                detail = "Gemini 3.8 Flash is scripting scenes, pacing & camera motion...",
                fraction = 0.08f
            )
        )
        val storyboard = aiDirector.generateStoryboard(
            prompt = prompt,
            style = style,
            duration = duration,
            aspectRatio = aspectRatio
        )

        val keyframes: List<Bitmap?>? = if (engineMode == VideoEngineMode.NANO_BANANA_ART) {
            val keyframeDir = File(videosRootDir, "keyframes_$reelId").apply { mkdirs() }
            storyboard.scenes.mapIndexed { index, scene ->
                onProgress(
                    RenderProgress(
                        stageTitle = "Generating AI Keyframes",
                        detail = "Nano Banana 2.1 painting scene ${index + 1} of ${storyboard.scenes.size}...",
                        fraction = 0.18f + (0.28f * (index.toFloat() / storyboard.scenes.size))
                    )
                )
                val bmp = aiDirector.generateSceneKeyframe(
                    imagePrompt = scene.imagePrompt,
                    style = style,
                    aspectRatio = aspectRatio
                )
                withContext(Dispatchers.IO) {
                    FileOutputStream(File(keyframeDir, "scene_$index.jpg")).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                }
                bmp
            }
        } else {
            null
        }

        return renderAndPersistReel(
            reelId = reelId,
            prompt = prompt,
            style = style,
            aspectRatio = aspectRatio,
            engineMode = engineMode,
            includeVoiceover = includeVoiceover,
            storyboard = storyboard,
            keyframes = keyframes,
            createdAt = System.currentTimeMillis(),
            onProgress = onProgress
        )
    }

    suspend fun rerenderReel(
        existing: GeneratedReel,
        updatedStoryboard: VideoStoryboard,
        onProgress: (RenderProgress) -> Unit
    ): GeneratedReel {
        val keyframes = if (existing.engineMode == VideoEngineMode.NANO_BANANA_ART) {
            val keyframeDir = File(videosRootDir, "keyframes_${existing.id}")
            if (keyframeDir.exists()) {
                updatedStoryboard.scenes.indices.map { idx ->
                    val f = File(keyframeDir, "scene_$idx.jpg")
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }
            } else null
        } else null

        return renderAndPersistReel(
            reelId = existing.id,
            prompt = existing.prompt,
            style = existing.style,
            aspectRatio = existing.aspectRatio,
            engineMode = existing.engineMode,
            includeVoiceover = existing.hasVoiceover,
            storyboard = updatedStoryboard,
            keyframes = keyframes,
            createdAt = existing.createdAt,
            onProgress = onProgress
        )
    }

    private suspend fun renderAndPersistReel(
        reelId: String,
        prompt: String,
        style: VisualStyle,
        aspectRatio: AspectRatioOption,
        engineMode: VideoEngineMode,
        includeVoiceover: Boolean,
        storyboard: VideoStoryboard,
        keyframes: List<Bitmap?>?,
        createdAt: Long,
        onProgress: (RenderProgress) -> Unit
    ): GeneratedReel {
        val tempDir = File(context.cacheDir, "render_$reelId").apply { mkdirs() }
        try {
            onProgress(
                RenderProgress(
                    stageTitle = if (includeVoiceover) "Synthesizing Voiceover & Score" else "Composing Soundtrack",
                    detail = if (includeVoiceover) {
                        "Recording AI narration and mixing ambient ${storyboard.musicMood.name.lowercase().replace('_', ' ')}..."
                    } else {
                        "Mixing ambient soundtrack..."
                    },
                    fraction = 0.48f
                )
            )
            val audioPcm = audioSynth.synthesizeTrackPcm(
                storyboard = storyboard,
                includeVoiceover = includeVoiceover,
                tempDir = tempDir
            )

            val width = aspectRatio.widthPx
            val height = aspectRatio.heightPx
            val videoFile = File(videosRootDir, "reel_$reelId.mp4")
            val thumbFile = File(videosRootDir, "thumb_$reelId.jpg")

            // Save crisp thumbnail from scene 0 midpoint
            withContext(Dispatchers.Default) {
                val thumbBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                MotionSceneRenderer(width, height).renderFrameInto(
                    targetBitmap = thumbBmp,
                    storyboard = storyboard,
                    sceneIndex = 0,
                    sceneProgress = 0.35f,
                    keyframes = keyframes
                )
                FileOutputStream(thumbFile).use { out ->
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                thumbBmp.recycle()
            }

            onProgress(
                RenderProgress(
                    stageTitle = "Encoding MP4 Video",
                    detail = "Rendering ${width}x${height} at 24fps (H.264 + AAC)...",
                    fraction = 0.56f
                )
            )

            val durationMs = mp4Encoder.encodeVideo(
                outputFile = videoFile,
                width = width,
                height = height,
                storyboard = storyboard,
                keyframes = keyframes,
                audioPcm = audioPcm
            ) { encodeFraction, preview ->
                onProgress(
                    RenderProgress(
                        stageTitle = "Encoding MP4 Video",
                        detail = "Rendering frame batch ${(encodeFraction * 100).toInt()}%...",
                        fraction = 0.56f + encodeFraction * 0.42f,
                        previewBitmap = preview
                    )
                )
            }

            val reel = GeneratedReel(
                id = reelId,
                title = storyboard.title,
                prompt = prompt,
                style = style,
                aspectRatio = aspectRatio,
                engineMode = engineMode,
                durationMs = durationMs,
                videoFilePath = videoFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                storyboard = storyboard,
                hasVoiceover = includeVoiceover,
                createdAt = createdAt
            )
            reelDao.upsertReel(ReelEntity.fromDomain(reel))

            onProgress(
                RenderProgress(
                    stageTitle = "Reel Ready",
                    detail = "Saved playable MP4 to your library.",
                    fraction = 1.0f
                )
            )
            return reel
        } finally {
            keyframes?.forEach { it?.recycle() }
            tempDir.deleteRecursively()
        }
    }

    suspend fun deleteReel(reel: GeneratedReel) {
        withContext(Dispatchers.IO) {
            File(reel.videoFilePath).delete()
            File(reel.thumbnailPath).delete()
            File(videosRootDir, "keyframes_${reel.id}").deleteRecursively()
        }
        reelDao.deleteReelById(reel.id)
    }
}
