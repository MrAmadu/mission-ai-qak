package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CameraMotion
import com.example.data.model.MusicMood
import com.example.data.model.ParticleEffect
import com.example.data.model.SceneMotif
import com.example.data.model.TransitionType
import com.example.data.model.VideoStoryboard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mission AI Qadeer", appName)
    }

    @Test
    fun `storyboard json roundtrip preserves scenes`() {
        val rawJson = """
            {
              "title": "Neon Horizon",
              "tagline": "Midnight streets awake.",
              "musicMood": "AMBIENT_SYNTH",
              "scenes": [
                {
                  "sceneNumber": 1,
                  "sceneTitle": "Rainy Alley",
                  "caption": "Lights shimmer on wet asphalt",
                  "narration": "As midnight falls, the neon district comes alive.",
                  "imagePrompt": "Cyberpunk street in rain",
                  "skyTopHex": "#090D1A",
                  "skyBottomHex": "#162447",
                  "accentHex": "#38BDF8",
                  "motif": "CITYSCAPE",
                  "particleEffect": "RAIN",
                  "cameraMotion": "ZOOM_IN",
                  "transition": "CROSSFADE",
                  "durationSeconds": 4
                }
              ]
            }
        """.trimIndent()

        val parsed = VideoStoryboard.fromJsonString(rawJson)
        assertEquals("Neon Horizon", parsed.title)
        assertEquals(MusicMood.AMBIENT_SYNTH, parsed.musicMood)
        assertEquals(1, parsed.scenes.size)
        assertEquals(SceneMotif.CITYSCAPE, parsed.scenes[0].motif)
        assertEquals(ParticleEffect.RAIN, parsed.scenes[0].particleEffect)
        assertEquals(CameraMotion.ZOOM_IN, parsed.scenes[0].cameraMotion)
        assertEquals(TransitionType.CROSSFADE, parsed.scenes[0].transition)

        val reparsed = VideoStoryboard.fromJsonString(parsed.toJsonString())
        assertEquals(parsed, reparsed)
    }
}
