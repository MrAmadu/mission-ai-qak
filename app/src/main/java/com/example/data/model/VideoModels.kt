package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class AspectRatioOption(
    val label: String,
    val ratioLabel: String,
    val widthPx: Int,
    val heightPx: Int
) {
    PORTRAIT_9_16("Reels", "9:16", 544, 960),
    LANDSCAPE_16_9("Cinema", "16:9", 960, 544),
    SQUARE_1_1("Square", "1:1", 720, 720);

    companion object {
        fun fromName(name: String): AspectRatioOption =
            entries.firstOrNull { it.name == name } ?: PORTRAIT_9_16
    }
}

enum class VisualStyle(val label: String, val promptHint: String) {
    CINEMATIC("Cinematic", "dramatic golden-hour lighting, filmic depth, rich atmosphere"),
    CYBERPUNK("Neon Cyber", "electric neon reflections, rain-slicked synthwave energy, glowing cyan and magenta"),
    WATERCOLOR("Dreamy Art", "soft luminous brushwork, ethereal pastel skies, storybook atmosphere"),
    SCI_FI("Sci-Fi", "deep cosmic vistas, futuristic silhouettes, glowing starfields and nebulae"),
    MINIMAL("Minimal", "bold clean geometric horizons, serene negative space, editorial elegance"),
    DOCUMENTARY("Nature Doc", "crisp natural light, sweeping vistas, authentic wildlife & earth textures");

    companion object {
        fun fromName(name: String): VisualStyle =
            entries.firstOrNull { it.name == name } ?: CINEMATIC
    }
}

enum class VideoDurationOption(val seconds: Int, val sceneCount: Int, val label: String) {
    SHORT_12S(12, 3, "12s"),
    MEDIUM_20S(20, 4, "20s"),
    LONG_30S(30, 5, "30s")
}

enum class VideoEngineMode(
    val title: String,
    val badge: String,
    val description: String
) {
    MOTION_STUDIO_FREE(
        title = "AI Motion Studio",
        badge = "FREE",
        description = "Gemini 3.8 Flash directs multi-scene parallax art, particles, voiceover & 24fps MP4 — 100% free tier."
    ),
    NANO_BANANA_ART(
        title = "AI Keyframe Art",
        badge = "BLAZE",
        description = "Gemini directs + Nano Banana 2.1 generates unique AI artwork per scene (requires Firebase Blaze)."
    );

    companion object {
        fun fromName(name: String): VideoEngineMode =
            entries.firstOrNull { it.name == name } ?: MOTION_STUDIO_FREE
    }
}

enum class SceneMotif {
    MOUNTAINS,
    CITYSCAPE,
    OCEAN,
    SPACE,
    FOREST,
    DESERT,
    CYBER_GRID,
    AURORA_WAVES,
    SUNSET_HORIZON,
    ABSTRACT_ORBS;

    companion object {
        fun fromRaw(raw: String): SceneMotif =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: SUNSET_HORIZON
    }
}

enum class ParticleEffect {
    STARS,
    RAIN,
    EMBERS,
    SNOW,
    BOKEH,
    NONE;

    companion object {
        fun fromRaw(raw: String): ParticleEffect =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: BOKEH
    }
}

enum class CameraMotion {
    ZOOM_IN,
    ZOOM_OUT,
    PAN_LEFT,
    PAN_RIGHT,
    DRIFT_UP;

    companion object {
        fun fromRaw(raw: String): CameraMotion =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: ZOOM_IN
    }
}

enum class TransitionType {
    CROSSFADE,
    WIPE_LEFT,
    ZOOM_DISSOLVE;

    companion object {
        fun fromRaw(raw: String): TransitionType =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: CROSSFADE
    }
}

enum class MusicMood {
    AMBIENT_SYNTH,
    WARM_PIANO,
    EPIC_PULSE,
    LO_FI_CHILL;

    companion object {
        fun fromRaw(raw: String): MusicMood =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: AMBIENT_SYNTH
    }
}

data class SceneSpec(
    val sceneNumber: Int,
    val sceneTitle: String,
    val caption: String,
    val narration: String,
    val imagePrompt: String,
    val skyTopHex: String,
    val skyBottomHex: String,
    val accentHex: String,
    val motif: SceneMotif,
    val particleEffect: ParticleEffect,
    val cameraMotion: CameraMotion,
    val transition: TransitionType,
    val durationSeconds: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sceneNumber", sceneNumber)
        put("sceneTitle", sceneTitle)
        put("caption", caption)
        put("narration", narration)
        put("imagePrompt", imagePrompt)
        put("skyTopHex", skyTopHex)
        put("skyBottomHex", skyBottomHex)
        put("accentHex", accentHex)
        put("motif", motif.name)
        put("particleEffect", particleEffect.name)
        put("cameraMotion", cameraMotion.name)
        put("transition", transition.name)
        put("durationSeconds", durationSeconds)
    }

    companion object {
        fun fromJson(obj: JSONObject, index: Int): SceneSpec = SceneSpec(
            sceneNumber = obj.optInt("sceneNumber", index + 1),
            sceneTitle = obj.optString("sceneTitle", "Scene ${index + 1}"),
            caption = obj.optString("caption", ""),
            narration = obj.optString("narration", ""),
            imagePrompt = obj.optString("imagePrompt", ""),
            skyTopHex = sanitizeHex(obj.optString("skyTopHex"), "#0F172A"),
            skyBottomHex = sanitizeHex(obj.optString("skyBottomHex"), "#1E293B"),
            accentHex = sanitizeHex(obj.optString("accentHex"), "#FF6B4A"),
            motif = SceneMotif.fromRaw(obj.optString("motif")),
            particleEffect = ParticleEffect.fromRaw(obj.optString("particleEffect")),
            cameraMotion = CameraMotion.fromRaw(obj.optString("cameraMotion")),
            transition = TransitionType.fromRaw(obj.optString("transition")),
            durationSeconds = obj.optInt("durationSeconds", 4).coerceIn(3, 8)
        )

        fun sanitizeHex(raw: String?, fallback: String): String {
            val trimmed = raw?.trim().orEmpty()
            val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            return if (Regex("^#[0-9A-Fa-f]{6}$").matches(withHash)) withHash else fallback
        }
    }
}

data class VideoStoryboard(
    val title: String,
    val tagline: String,
    val musicMood: MusicMood,
    val scenes: List<SceneSpec>
) {
    val totalDurationSeconds: Int
        get() = scenes.sumOf { it.durationSeconds }

    fun toJsonString(): String = JSONObject().apply {
        put("title", title)
        put("tagline", tagline)
        put("musicMood", musicMood.name)
        put("scenes", JSONArray().apply { scenes.forEach { put(it.toJson()) } })
    }.toString()

    companion object {
        fun fromJsonString(jsonString: String): VideoStoryboard {
            val root = JSONObject(jsonString)
            val scenesArray = root.optJSONArray("scenes") ?: JSONArray()
            val scenes = (0 until scenesArray.length()).map { idx ->
                SceneSpec.fromJson(scenesArray.getJSONObject(idx), idx)
            }
            return VideoStoryboard(
                title = root.optString("title", "Untitled Reel"),
                tagline = root.optString("tagline", ""),
                musicMood = MusicMood.fromRaw(root.optString("musicMood")),
                scenes = scenes
            )
        }
    }
}

data class GeneratedReel(
    val id: String,
    val title: String,
    val prompt: String,
    val style: VisualStyle,
    val aspectRatio: AspectRatioOption,
    val engineMode: VideoEngineMode,
    val durationMs: Long,
    val videoFilePath: String,
    val thumbnailPath: String,
    val storyboard: VideoStoryboard,
    val hasVoiceover: Boolean,
    val createdAt: Long
)
