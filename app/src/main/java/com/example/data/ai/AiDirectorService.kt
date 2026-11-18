package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AspectRatioOption
import com.example.data.model.CameraMotion
import com.example.data.model.MusicMood
import com.example.data.model.ParticleEffect
import com.example.data.model.SceneMotif
import com.example.data.model.TransitionType
import com.example.data.model.VideoDurationOption
import com.example.data.model.VideoStoryboard
import com.example.data.model.VisualStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "AiDirectorService"

class AiDirectorService {

    private val promptEnhancerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val storyboardSchema by lazy {
        val sceneSchema = Schema.obj(
            properties = mapOf(
                "sceneNumber" to Schema.integer(description = "1-based index of the scene"),
                "sceneTitle" to Schema.string(description = "Short 2-4 word title for the scene"),
                "caption" to Schema.string(description = "Punchy subtitle shown on video (max 10 words)"),
                "narration" to Schema.string(description = "Natural spoken voiceover sentence (10-18 words)"),
                "imagePrompt" to Schema.string(description = "Detailed visual art description for the scene"),
                "skyTopHex" to Schema.string(description = "6-digit hex color like #0D1B2A for top sky gradient"),
                "skyBottomHex" to Schema.string(description = "6-digit hex color like #1B263B for horizon gradient"),
                "accentHex" to Schema.string(description = "6-digit vibrant hex color like #FF6B4A for lights and highlights"),
                "motif" to Schema.enumeration(
                    values = SceneMotif.entries.map { it.name },
                    description = "Primary visual landscape or subject motif"
                ),
                "particleEffect" to Schema.enumeration(
                    values = ParticleEffect.entries.map { it.name },
                    description = "Atmospheric animated particle layer"
                ),
                "cameraMotion" to Schema.enumeration(
                    values = CameraMotion.entries.map { it.name },
                    description = "Smooth camera movement across the scene"
                ),
                "transition" to Schema.enumeration(
                    values = TransitionType.entries.map { it.name },
                    description = "Transition into this scene"
                ),
                "durationSeconds" to Schema.integer(description = "Scene duration in seconds between 3 and 6")
            )
        )

        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Catchy short film title (2-5 words)"),
                "tagline" to Schema.string(description = "One-sentence poetic logline"),
                "musicMood" to Schema.enumeration(
                    values = MusicMood.entries.map { it.name },
                    description = "Background ambient synth mood"
                ),
                "scenes" to Schema.array(
                    items = sceneSchema,
                    description = "Ordered list of visual video scenes"
                )
            )
        )
    }

    private val storyboardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
                temperature = 0.85f
            }
        )
    }

    suspend fun enhancePrompt(rawPrompt: String, style: VisualStyle): String {
        val instruction = """
            You are an award-winning film director. Expand this short video idea into a vivid,
            cinematic 2-sentence visual video prompt matching the "${style.label}" style
            (${style.promptHint}). Return ONLY the enhanced prompt text with no quotes or markdown.
            
            Idea: $rawPrompt
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                promptEnhancerModel.generateContent(instruction)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                throw IllegalStateException("Gemini returned an empty prompt enhancement.")
            }
            text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prompt enhancement failed", e)
            throw e
        }
    }

    suspend fun generateStoryboard(
        prompt: String,
        style: VisualStyle,
        duration: VideoDurationOption,
        aspectRatio: AspectRatioOption
    ): VideoStoryboard {
        val perSceneSeconds = (duration.seconds / duration.sceneCount).coerceIn(3, 6)
        val directorPrompt = """
            You are an expert AI Film Director & Motion Designer.
            Create a cohesive ${duration.sceneCount}-scene storyboard for a ${duration.seconds}-second
            ${aspectRatio.ratioLabel} video in the "${style.label}" style (${style.promptHint}).
            
            User Concept: "$prompt"
            
            Requirements:
            - Produce exactly ${duration.sceneCount} sequential scenes with clear story progression (hook, build, climax, finale).
            - Each scene's durationSeconds should be around $perSceneSeconds seconds (between 3 and 6).
            - Vary cameraMotion, motif, and colors smoothly across scenes so the film feels dynamic and cohesive.
            - skyTopHex, skyBottomHex, and accentHex MUST be valid 6-digit hex codes starting with # (e.g. #0B1026).
            - caption should be concise and cinematic (max 9 words) suitable for on-screen kinetic subtitles.
            - narration should read naturally aloud in ~3-5 seconds and complement the visuals without repeating the caption word-for-word.
            - imagePrompt should be richly descriptive for artwork generation.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                storyboardModel.generateContent(directorPrompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty storyboard.")
            val storyboard = VideoStoryboard.fromJsonString(jsonText)
            if (storyboard.scenes.isEmpty()) {
                throw IllegalStateException("Gemini storyboard contained zero scenes.")
            }
            storyboard
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Storyboard generation failed", e)
            throw e
        }
    }

    suspend fun generateSceneKeyframe(
        imagePrompt: String,
        style: VisualStyle,
        aspectRatio: AspectRatioOption
    ): Bitmap {
        val ratio = when (aspectRatio) {
            AspectRatioOption.PORTRAIT_9_16 -> AspectRatio.PORTRAIT_9x16
            AspectRatioOption.LANDSCAPE_16_9 -> AspectRatio.LANDSCAPE_16x9
            AspectRatioOption.SQUARE_1_1 -> AspectRatio.SQUARE_1x1
        }
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(ratio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        val imageModel = Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )

        val fullPrompt = "$imagePrompt, ${style.promptHint}, no text or watermarks"
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(fullPrompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image
                ?: throw IllegalStateException("Nano Banana returned no image frame for scene.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Keyframe generation failed", e)
            throw e
        }
    }
}
