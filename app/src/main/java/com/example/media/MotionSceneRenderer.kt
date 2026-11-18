package com.example.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.CameraMotion
import com.example.data.model.ParticleEffect
import com.example.data.model.SceneMotif
import com.example.data.model.SceneSpec
import com.example.data.model.TransitionType
import com.example.data.model.VideoStoryboard
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MotionSceneRenderer(
    private val width: Int,
    private val height: Int
) {
    private val sceneBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val sceneCanvas = Canvas(sceneBuffer)

    private val nextBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val nextCanvas = Canvas(nextBuffer)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(12f, 0f, 3f, Color.argb(190, 0, 0, 0))
    }

    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
    }

    private val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    fun renderFrameInto(
        targetBitmap: Bitmap,
        storyboard: VideoStoryboard,
        sceneIndex: Int,
        sceneProgress: Float,
        keyframes: List<Bitmap?>?
    ) {
        val canvas = Canvas(targetBitmap)
        val scene = storyboard.scenes[sceneIndex]
        val nextScene = storyboard.scenes.getOrNull(sceneIndex + 1)

        // Transition window during the final 16% of the scene
        val transitionStart = 0.84f
        val isTransitioning = nextScene != null && sceneProgress > transitionStart

        drawSingleScene(
            canvas = sceneCanvas,
            scene = scene,
            progress = sceneProgress,
            keyframe = keyframes?.getOrNull(sceneIndex)
        )

        if (!isTransitioning) {
            canvas.drawBitmap(sceneBuffer, 0f, 0f, null)
        } else {
            val t = ((sceneProgress - transitionStart) / (1f - transitionStart)).coerceIn(0f, 1f)
            val easedT = smoothStep(t)
            drawSingleScene(
                canvas = nextCanvas,
                scene = nextScene!!,
                progress = easedT * 0.16f,
                keyframe = keyframes?.getOrNull(sceneIndex + 1)
            )
            drawTransition(canvas, nextScene.transition, easedT)
        }

        drawVignette(canvas)

        // Film title intro badge on scene 0 early frames
        if (sceneIndex == 0 && sceneProgress < 0.75f && storyboard.title.isNotBlank()) {
            val titleAlpha = when {
                sceneProgress < 0.15f -> sceneProgress / 0.15f
                sceneProgress > 0.55f -> 1f - ((sceneProgress - 0.55f) / 0.20f)
                else -> 1f
            }.coerceIn(0f, 1f)
            drawIntroTitleOverlay(canvas, storyboard.title, storyboard.tagline, titleAlpha)
        }

        // Animated kinetic caption pill
        val captionAlpha = when {
            sceneProgress < 0.12f -> sceneProgress / 0.12f
            sceneProgress > 0.88f -> 1f - ((sceneProgress - 0.88f) / 0.12f)
            else -> 1f
        }.coerceIn(0f, 1f)

        if (scene.caption.isNotBlank() && captionAlpha > 0.02f) {
            drawCaptionPill(canvas, scene.caption, scene.accentHex, captionAlpha)
        }
    }

    private fun drawSingleScene(
        canvas: Canvas,
        scene: SceneSpec,
        progress: Float,
        keyframe: Bitmap?
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.save()
        applyCameraTransform(canvas, scene.cameraMotion, progress, w, h)

        if (keyframe != null) {
            drawScaledCenterCrop(canvas, keyframe, w, h)
        } else {
            drawProceduralScene(canvas, scene, progress, w, h)
        }
        canvas.restore()

        // Particles render in screen-space for crisp atmospheric depth
        if (scene.particleEffect != ParticleEffect.NONE) {
            drawParticles(canvas, scene.particleEffect, scene.accentHex, progress, scene.sceneNumber, w, h)
        }
    }

    private fun applyCameraTransform(
        canvas: Canvas,
        motion: CameraMotion,
        progress: Float,
        w: Float,
        h: Float
    ) {
        val eased = smoothStep(progress)
        when (motion) {
            CameraMotion.ZOOM_IN -> {
                val scale = 1.0f + 0.14f * eased
                canvas.scale(scale, scale, w * 0.5f, h * 0.5f)
            }
            CameraMotion.ZOOM_OUT -> {
                val scale = 1.14f - 0.14f * eased
                canvas.scale(scale, scale, w * 0.5f, h * 0.5f)
            }
            CameraMotion.PAN_LEFT -> {
                val scale = 1.12f
                val tx = w * 0.05f - w * 0.10f * eased
                canvas.scale(scale, scale, w * 0.5f, h * 0.5f)
                canvas.translate(tx, 0f)
            }
            CameraMotion.PAN_RIGHT -> {
                val scale = 1.12f
                val tx = -w * 0.05f + w * 0.10f * eased
                canvas.scale(scale, scale, w * 0.5f, h * 0.5f)
                canvas.translate(tx, 0f)
            }
            CameraMotion.DRIFT_UP -> {
                val scale = 1.12f
                val ty = h * 0.04f - h * 0.08f * eased
                canvas.scale(scale, scale, w * 0.5f, h * 0.5f)
                canvas.translate(0f, ty)
            }
        }
    }

    private fun drawScaledCenterCrop(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float) {
        val scale = maxOf(w / bitmap.width.toFloat(), h / bitmap.height.toFloat())
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (w - dw) * 0.5f
        val top = (h - dh) * 0.5f
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), paint)
    }

    private fun drawProceduralScene(
        canvas: Canvas,
        scene: SceneSpec,
        progress: Float,
        w: Float,
        h: Float
    ) {
        val topColor = safeColor(scene.skyTopHex, 0xFF0D1B2A.toInt())
        val bottomColor = safeColor(scene.skyBottomHex, 0xFF1B263B.toInt())
        val accentColor = safeColor(scene.accentHex, 0xFFFF6B4A.toInt())

        // 1. Sky gradient
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(0f, 0f, 0f, h, topColor, bottomColor, Shader.TileMode.CLAMP)
        canvas.drawRect(-w * 0.15f, -h * 0.15f, w * 1.15f, h * 1.15f, paint)
        paint.shader = null

        // 2. Atmospheric radial glow
        val glowX = w * (0.45f + 0.1f * sin(progress * PI.toFloat()))
        val glowY = h * 0.38f
        val glowRadius = min(w, h) * 0.65f
        paint.shader = RadialGradient(
            glowX,
            glowY,
            glowRadius,
            intArrayOf(
                Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.argb(0, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(glowX, glowY, glowRadius, paint)
        paint.shader = null

        // 3. Motif layers
        when (scene.motif) {
            SceneMotif.MOUNTAINS -> drawMountains(canvas, w, h, topColor, bottomColor, accentColor, progress)
            SceneMotif.CITYSCAPE -> drawCityscape(canvas, w, h, topColor, accentColor, progress)
            SceneMotif.OCEAN -> drawOcean(canvas, w, h, topColor, bottomColor, accentColor, progress)
            SceneMotif.SPACE -> drawSpace(canvas, w, h, accentColor, progress)
            SceneMotif.FOREST -> drawForest(canvas, w, h, topColor, bottomColor, accentColor, progress)
            SceneMotif.DESERT -> drawDesert(canvas, w, h, topColor, bottomColor, accentColor, progress)
            SceneMotif.CYBER_GRID -> drawCyberGrid(canvas, w, h, accentColor, progress)
            SceneMotif.AURORA_WAVES -> drawAurora(canvas, w, h, accentColor, bottomColor, progress)
            SceneMotif.SUNSET_HORIZON -> drawSunsetHorizon(canvas, w, h, topColor, accentColor, progress)
            SceneMotif.ABSTRACT_ORBS -> drawAbstractOrbs(canvas, w, h, accentColor, bottomColor, progress)
        }
    }

    private fun drawMountains(
        canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int, accent: Int, p: Float
    ) {
        // Sun / Moon orb behind peaks
        paint.color = Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(w * 0.65f, h * (0.36f - 0.04f * p), min(w, h) * 0.11f, paint)

        val layers = listOf(0.54f to 0.35f, 0.65f to 0.62f, 0.76f to 0.90f)
        layers.forEachIndexed { idx, (baseYRatio, darkness) ->
            path.reset()
            val baseY = h * baseYRatio
            val shift = (idx + 1) * w * 0.03f * p
            path.moveTo(-w * 0.2f, h * 1.15f)
            path.lineTo(-w * 0.2f, baseY)
            val peaks = 5 + idx
            for (i in 0..peaks) {
                val px = -w * 0.15f + (w * 1.3f / peaks) * i - shift
                val peakOffset = if (i % 2 == 0) -h * (0.14f - idx * 0.025f) else h * 0.04f
                path.lineTo(px, baseY + peakOffset)
            }
            path.lineTo(w * 1.2f, h * 1.15f)
            path.close()
            paint.color = blendColors(bottom, Color.BLACK, darkness)
            canvas.drawPath(path, paint)
        }
        // Subtle mist band
        paint.shader = LinearGradient(
            0f, h * 0.6f, 0f, h * 0.9f,
            Color.argb(0, 255, 255, 255),
            Color.argb(45, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.6f, w, h * 0.9f, paint)
        paint.shader = null
    }

    private fun drawCityscape(canvas: Canvas, w: Float, h: Float, top: Int, accent: Int, p: Float) {
        // Glowing moon / neon halo
        paint.color = Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(w * 0.3f, h * 0.28f, min(w, h) * 0.12f, paint)

        val buildingCount = 14
        val bw = (w * 1.2f) / buildingCount
        for (i in 0 until buildingCount) {
            val seed = ((i * 73 + 19) % 100) / 100f
            val bh = h * (0.22f + seed * 0.35f)
            val left = -w * 0.1f + i * bw - p * w * 0.03f
            val topY = h * 0.78f - bh
            paint.color = blendColors(top, Color.BLACK, 0.72f)
            canvas.drawRect(left, topY, left + bw * 0.92f, h * 1.1f, paint)

            // Lit neon windows
            paint.color = Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent))
            val cols = 3
            val rows = (bh / (h * 0.03f)).toInt().coerceAtLeast(2)
            val winW = bw * 0.14f
            val winH = h * 0.012f
            for (r in 1 until rows step 2) {
                for (c in 0 until cols) {
                    if ((i + r + c) % 3 != 0) {
                        val wx = left + bw * 0.18f + c * (bw * 0.24f)
                        val wy = topY + r * (h * 0.025f)
                        if (wy < h * 0.76f) {
                            canvas.drawRect(wx, wy, wx + winW, wy + winH, paint)
                        }
                    }
                }
            }
        }
        // Wet street reflection floor
        paint.shader = LinearGradient(
            0f, h * 0.78f, 0f, h,
            Color.argb(160, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.argb(230, 5, 8, 18),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.78f, w, h * 1.1f, paint)
        paint.shader = null
    }

    private fun drawOcean(
        canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int, accent: Int, p: Float
    ) {
        val horizon = h * 0.56f
        paint.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(w * 0.5f, horizon - min(w, h) * 0.05f, min(w, h) * 0.13f, paint)

        for (layer in 0..4) {
            val yBase = horizon + layer * (h * 0.085f)
            path.reset()
            path.moveTo(-w * 0.1f, h * 1.1f)
            path.lineTo(-w * 0.1f, yBase)
            val steps = 28
            for (s in 0..steps) {
                val x = -w * 0.1f + (w * 1.2f / steps) * s
                val phase = (s * 0.45f) + (p * 4.5f) + layer * 1.3f
                val y = yBase + sin(phase.toDouble()).toFloat() * (h * 0.015f)
                path.lineTo(x, y)
            }
            path.lineTo(w * 1.15f, h * 1.1f)
            path.close()
            paint.color = blendColors(bottom, Color.BLACK, 0.25f + layer * 0.14f)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawSpace(canvas: Canvas, w: Float, h: Float, accent: Int, p: Float) {
        // Distant glowing ringed planet
        val cx = w * (0.58f - 0.05f * p)
        val cy = h * 0.36f
        val radius = min(w, h) * 0.16f
        paint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.4f,
            intArrayOf(accent, Color.rgb(20, 24, 52)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        // Planetary ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.09f
        paint.color = Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.save()
        canvas.rotate(-18f, cx, cy)
        canvas.drawOval(
            RectF(cx - radius * 1.85f, cy - radius * 0.38f, cx + radius * 1.85f, cy + radius * 0.38f),
            paint
        )
        canvas.restore()
        paint.style = Paint.Style.FILL
    }

    private fun drawForest(
        canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int, accent: Int, p: Float
    ) {
        // Sun rays through mist
        paint.color = Color.argb(50, Color.red(accent), Color.green(accent), Color.blue(accent))
        for (i in 0..3) {
            path.reset()
            val rx = w * (0.25f + i * 0.16f)
            path.moveTo(rx, 0f)
            path.lineTo(rx + w * 0.08f, 0f)
            path.lineTo(rx + w * 0.22f, h * 0.85f)
            path.lineTo(rx - w * 0.04f, h * 0.85f)
            path.close()
            canvas.drawPath(path, paint)
        }

        // Layered pine tree silhouettes
        for (layer in 0..2) {
            val baseY = h * (0.68f + layer * 0.1f)
            val treeCount = 9 + layer * 3
            val treeW = (w * 1.2f) / treeCount
            paint.color = blendColors(bottom, Color.BLACK, 0.45f + layer * 0.2f)
            for (i in 0 until treeCount) {
                val tx = -w * 0.1f + i * treeW - p * w * 0.02f * (layer + 1)
                val treeH = h * (0.22f + ((i * 37) % 10) * 0.015f)
                path.reset()
                path.moveTo(tx + treeW * 0.5f, baseY - treeH)
                path.lineTo(tx + treeW * 1.05f, baseY)
                path.lineTo(tx - treeW * 0.05f, baseY)
                path.close()
                canvas.drawPath(path, paint)
            }
            canvas.drawRect(-w * 0.1f, baseY, w * 1.1f, h * 1.1f, paint)
        }
    }

    private fun drawDesert(
        canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int, accent: Int, p: Float
    ) {
        paint.color = Color.argb(230, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(w * 0.5f, h * (0.42f - 0.05f * p), min(w, h) * 0.15f, paint)

        for (layer in 0..3) {
            val y0 = h * (0.56f + layer * 0.11f)
            path.reset()
            path.moveTo(-w * 0.1f, h * 1.1f)
            path.lineTo(-w * 0.1f, y0)
            path.cubicTo(
                w * 0.28f, y0 - h * 0.08f,
                w * 0.68f, y0 + h * 0.07f,
                w * 1.15f, y0 - h * 0.03f
            )
            path.lineTo(w * 1.15f, h * 1.1f)
            path.close()
            paint.color = blendColors(accent, Color.BLACK, 0.35f + layer * 0.16f)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawCyberGrid(canvas: Canvas, w: Float, h: Float, accent: Int, p: Float) {
        val horizonY = h * 0.54f
        // Retro synthwave sun
        paint.shader = LinearGradient(
            0f, horizonY - h * 0.24f, 0f, horizonY,
            Color.rgb(255, 210, 90), accent, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.5f, horizonY - h * 0.04f, min(w, h) * 0.19f, paint)
        paint.shader = null

        // Perspective grid floor
        paint.color = Color.rgb(10, 12, 26)
        canvas.drawRect(-w * 0.1f, horizonY, w * 1.1f, h * 1.1f, paint)

        paint.color = accent
        paint.strokeWidth = 2.5f
        val vpX = w * 0.5f
        for (i in -8..8) {
            canvas.drawLine(vpX + i * w * 0.04f, horizonY, vpX + i * w * 0.22f, h * 1.05f, paint)
        }
        val rowCount = 10
        for (r in 0 until rowCount) {
            val t = ((r.toFloat() / rowCount) + p * 0.8f) % 1f
            val y = horizonY + (t * t) * (h * 0.52f)
            paint.alpha = (80 + (t * 175)).toInt().coerceIn(0, 255)
            canvas.drawLine(-w * 0.1f, y, w * 1.1f, y, paint)
        }
        paint.alpha = 255
    }

    private fun drawAurora(canvas: Canvas, w: Float, h: Float, accent: Int, bottom: Int, p: Float) {
        for (ribbon in 0..2) {
            path.reset()
            val yCenter = h * (0.28f + ribbon * 0.09f)
            path.moveTo(-w * 0.1f, yCenter)
            for (s in 0..16) {
                val x = -w * 0.1f + (w * 1.2f / 16) * s
                val dy = sin((s * 0.55f + p * 3.2f + ribbon).toDouble()).toFloat() * h * 0.07f
                path.lineTo(x, yCenter + dy)
            }
            path.lineTo(w * 1.15f, yCenter + h * 0.18f)
            path.lineTo(-w * 0.1f, yCenter + h * 0.18f)
            path.close()
            paint.color = Color.argb(65 - ribbon * 12, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawPath(path, paint)
        }
        drawMountains(canvas, w, h, bottom, bottom, accent, p)
    }

    private fun drawSunsetHorizon(
        canvas: Canvas, w: Float, h: Float, top: Int, accent: Int, p: Float
    ) {
        val sunY = h * (0.50f + 0.03f * sin(p * PI.toFloat()))
        paint.color = accent
        canvas.drawCircle(w * 0.5f, sunY, min(w, h) * 0.18f, paint)
        paint.color = blendColors(top, Color.BLACK, 0.72f)
        canvas.drawRect(-w * 0.1f, h * 0.62f, w * 1.1f, h * 1.1f, paint)
    }

    private fun drawAbstractOrbs(canvas: Canvas, w: Float, h: Float, accent: Int, bottom: Int, p: Float) {
        for (i in 0..5) {
            val angle = p * 2f * PI.toFloat() * 0.35f + i * 1.1f
            val cx = w * (0.25f + 0.5f * ((i * 37 % 10) / 10f)) + cos(angle.toDouble()).toFloat() * w * 0.08f
            val cy = h * (0.25f + 0.5f * ((i * 53 % 10) / 10f)) + sin(angle.toDouble()).toFloat() * h * 0.06f
            val rad = min(w, h) * (0.10f + (i % 3) * 0.05f)
            val baseCol = if (i % 2 == 0) accent else bottom
            paint.color = Color.argb(110, Color.red(baseCol), Color.green(baseCol), Color.blue(baseCol))
            canvas.drawCircle(cx, cy, rad, paint)
        }
    }

    private fun drawParticles(
        canvas: Canvas,
        effect: ParticleEffect,
        accentHex: String,
        progress: Float,
        seed: Int,
        w: Float,
        h: Float
    ) {
        val accent = safeColor(accentHex, Color.WHITE)
        paint.reset()
        paint.isAntiAlias = true
        val count = when (effect) {
            ParticleEffect.STARS -> 48
            ParticleEffect.RAIN -> 42
            ParticleEffect.EMBERS -> 32
            ParticleEffect.SNOW -> 36
            ParticleEffect.BOKEH -> 18
            ParticleEffect.NONE -> 0
        }

        for (i in 0 until count) {
            val rx = (((i * 197 + seed * 41) % 1000) / 1000f)
            val ry = (((i * 313 + seed * 79) % 1000) / 1000f)
            when (effect) {
                ParticleEffect.STARS -> {
                    val twinkle = (0.35f + 0.65f * absSin(progress * 6f + i)).coerceIn(0f, 1f)
                    paint.color = Color.argb((twinkle * 220).toInt(), 255, 255, 255)
                    canvas.drawCircle(rx * w, ry * h * 0.7f, 1.5f + (i % 3), paint)
                }
                ParticleEffect.RAIN -> {
                    val y = ((ry + progress * 2.8f) % 1f) * h
                    val x = ((rx - progress * 0.35f + 1f) % 1f) * w
                    paint.color = Color.argb(125, 190, 230, 255)
                    paint.strokeWidth = 2f
                    canvas.drawLine(x, y, x - 8f, y + 26f, paint)
                }
                ParticleEffect.EMBERS -> {
                    val y = ((ry - progress * 0.9f + 1f) % 1f) * h
                    val x = rx * w + sin((progress * 8f + i).toDouble()).toFloat() * 14f
                    paint.color = Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent))
                    canvas.drawCircle(x, y, 2.5f + (i % 3) * 1.2f, paint)
                }
                ParticleEffect.SNOW -> {
                    val y = ((ry + progress * 0.7f) % 1f) * h
                    val x = rx * w + sin((progress * 5f + i).toDouble()).toFloat() * 16f
                    paint.color = Color.argb(185, 255, 255, 255)
                    canvas.drawCircle(x, y, 2.2f + (i % 4), paint)
                }
                ParticleEffect.BOKEH -> {
                    val y = ((ry - progress * 0.25f + 1f) % 1f) * h
                    val x = rx * w
                    paint.color = Color.argb(48, Color.red(accent), Color.green(accent), Color.blue(accent))
                    canvas.drawCircle(x, y, min(w, h) * (0.025f + (i % 4) * 0.015f), paint)
                }
                ParticleEffect.NONE -> Unit
            }
        }
    }

    private fun drawTransition(canvas: Canvas, type: TransitionType, t: Float) {
        when (type) {
            TransitionType.CROSSFADE -> {
                canvas.drawBitmap(sceneBuffer, 0f, 0f, null)
                paint.reset()
                paint.alpha = (t * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(nextBuffer, 0f, 0f, paint)
                paint.alpha = 255
            }
            TransitionType.WIPE_LEFT -> {
                canvas.drawBitmap(sceneBuffer, 0f, 0f, null)
                val splitX = width * (1f - t)
                canvas.save()
                canvas.clipRect(splitX, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(nextBuffer, 0f, 0f, null)
                canvas.restore()
            }
            TransitionType.ZOOM_DISSOLVE -> {
                canvas.save()
                val scaleOut = 1f + 0.12f * t
                canvas.scale(scaleOut, scaleOut, width * 0.5f, height * 0.5f)
                paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(sceneBuffer, 0f, 0f, paint)
                canvas.restore()

                paint.alpha = (t * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(nextBuffer, 0f, 0f, paint)
                paint.alpha = 255
            }
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.reset()
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.5f, maxOf(w, h) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.argb(125, 0, 0, 0)),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawIntroTitleOverlay(canvas: Canvas, title: String, tagline: String, alpha: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        titlePaint.textSize = min(w, h) * 0.068f
        titlePaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawText(title, w * 0.5f, h * 0.22f, titlePaint)

        if (tagline.isNotBlank()) {
            badgePaint.textSize = min(w, h) * 0.026f
            badgePaint.textAlign = Paint.Align.CENTER
            badgePaint.alpha = (alpha * 210).toInt().coerceIn(0, 255)
            val truncated = if (tagline.length > 52) tagline.take(49) + "..." else tagline
            canvas.drawText(truncated, w * 0.5f, h * 0.265f, badgePaint)
        }
    }

    private fun drawCaptionPill(canvas: Canvas, caption: String, accentHex: String, alpha: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val maxTextWidth = (w * 0.80f).toInt()
        captionPaint.textSize = min(w, h) * 0.038f
        captionPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)

        val layout = StaticLayout.Builder
            .obtain(caption, 0, caption.length, captionPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()

        val pillPadH = min(w, h) * 0.045f
        val pillPadV = min(w, h) * 0.024f
        val contentWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        val pillWidth = contentWidth + pillPadH * 2f
        val pillHeight = layout.height + pillPadV * 2f
        val pillLeft = (w - pillWidth) * 0.5f
        val pillBottom = h * 0.89f
        val pillTop = pillBottom - pillHeight

        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.argb((alpha * 175).toInt().coerceIn(0, 255), 11, 14, 23)
        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillLeft + pillWidth, pillBottom),
            pillHeight * 0.35f,
            pillHeight * 0.35f,
            paint
        )

        val accent = safeColor(accentHex, 0xFFFF6B4A.toInt())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb((alpha * 165).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillLeft + pillWidth, pillBottom),
            pillHeight * 0.35f,
            pillHeight * 0.35f,
            paint
        )
        paint.style = Paint.Style.FILL

        canvas.save()
        canvas.translate((w - maxTextWidth) * 0.5f, pillTop + pillPadV)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun smoothStep(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun absSin(v: Float): Float = kotlin.math.abs(sin(v.toDouble())).toFloat()

    private fun safeColor(hex: String, fallback: Int): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        fallback
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val inv = 1f - r
        val red = (Color.red(c1) * inv + Color.red(c2) * r).toInt()
        val green = (Color.green(c1) * inv + Color.green(c2) * r).toInt()
        val blue = (Color.blue(c1) * inv + Color.blue(c2) * r).toInt()
        return Color.rgb(red, green, blue)
    }
}
