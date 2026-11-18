package com.example.ui.screens

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.CameraMotion
import com.example.data.model.GeneratedReel
import com.example.data.model.ParticleEffect
import com.example.data.model.SceneSpec
import com.example.data.model.VideoStoryboard
import com.example.media.VideoExportHelper
import com.example.ui.GenerationUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStoryboardScreen(
    reel: GeneratedReel?,
    rerenderState: GenerationUiState,
    onBack: () -> Unit,
    onRerender: (GeneratedReel, VideoStoryboard) -> Unit,
    onRerenderHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusBanner by remember { mutableStateOf<String?>(null) }
    var editableStoryboard by remember(reel?.id, reel?.storyboard) {
        mutableStateOf(reel?.storyboard)
    }
    var editingSceneIndex by remember { mutableStateOf<Int?>(null) }
    var playerReloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(rerenderState) {
        if (rerenderState is GenerationUiState.Completed) {
            playerReloadKey++
            statusBanner = "Updated MP4 rendered successfully."
            onRerenderHandled()
        } else if (rerenderState is GenerationUiState.Error) {
            statusBanner = rerenderState.message
            onRerenderHandled()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = reel?.title ?: "Reel Player",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (reel == null || editableStoryboard == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Reel not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val currentStoryboard = editableStoryboard!!
        val isRerendering = rerenderState is GenerationUiState.Generating

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Video Player Stage
                Mp4TexturePlayer(
                    videoPath = reel.videoFilePath,
                    aspectRatio = reel.aspectRatio.widthPx.toFloat() / reel.aspectRatio.heightPx.toFloat(),
                    reloadKey = playerReloadKey
                )

                // Export / Share Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val res = VideoExportHelper.saveToGallery(
                                    context,
                                    File(reel.videoFilePath),
                                    reel.title
                                )
                                statusBanner = res.getOrElse { it.localizedMessage ?: "Export failed." }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_gallery_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save_to_gallery))
                    }

                    FilledTonalButton(
                        onClick = {
                            VideoExportHelper.shareVideo(
                                context,
                                File(reel.videoFilePath),
                                reel.title
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_video_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.share_video))
                    }
                }

                if (statusBanner != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusBanner!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { statusBanner = null }) {
                                Text("OK")
                            }
                        }
                    }
                }

                if (rerenderState is GenerationUiState.Generating) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(rerenderState.stageTitle, style = MaterialTheme.typography.titleSmall)
                            Text(rerenderState.detail, style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(
                                progress = { rerenderState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Storyboard Header + Re-render CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storyboard_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (currentStoryboard.tagline.isNotBlank()) {
                            Text(
                                text = currentStoryboard.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onRerender(reel, currentStoryboard) },
                        enabled = !isRerendering,
                        modifier = Modifier.testTag("rerender_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.rerender_video))
                    }
                }

                // Scene Cards
                currentStoryboard.scenes.forEachIndexed { index, scene ->
                    val isEditing = editingSceneIndex == index
                    SceneEditorCard(
                        scene = scene,
                        isEditing = isEditing,
                        onToggleEdit = { editingSceneIndex = if (isEditing) null else index },
                        onUpdateScene = { updatedScene ->
                            val updatedScenes = currentStoryboard.scenes.toMutableList()
                            updatedScenes[index] = updatedScene
                            editableStoryboard = currentStoryboard.copy(scenes = updatedScenes)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Mp4TexturePlayer(
    videoPath: String,
    aspectRatio: Float,
    reloadKey: Int
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(true) }
    var durationMs by remember { mutableIntStateOf(1) }
    var positionMs by remember { mutableIntStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(mediaPlayer, isPlaying, isScrubbing) {
        while (true) {
            val mp = mediaPlayer
            if (mp != null && !isScrubbing) {
                runCatching {
                    if (mp.isPlaying) {
                        positionMs = mp.currentPosition
                    }
                }
            }
            delay(120L)
        }
    }

    DisposableEffect(videoPath, reloadKey) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    st: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    val surface = Surface(st)
                                    val mp = MediaPlayer().apply {
                                        setDataSource(videoPath)
                                        setSurface(surface)
                                        this.isLooping = isLooping
                                        setOnPreparedListener { prepared ->
                                            durationMs = prepared.duration.coerceAtLeast(1)
                                            val vol = if (isMuted) 0f else 1f
                                            prepared.setVolume(vol, vol)
                                            prepared.start()
                                            isPlaying = true
                                        }
                                        setOnCompletionListener {
                                            if (!this.isLooping) isPlaying = false
                                        }
                                        prepareAsync()
                                    }
                                    mediaPlayer = mp
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    runCatching {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                    }
                                    mediaPlayer = null
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                            }
                        }
                    },
                    update = { textureView ->
                        // Reload if re-rendered
                        if (reloadKey > 0 && mediaPlayer == null && textureView.isAvailable) {
                            val st = textureView.surfaceTexture
                            if (st != null) {
                                val mp = MediaPlayer().apply {
                                    setDataSource(videoPath)
                                    setSurface(Surface(st))
                                    this.isLooping = isLooping
                                    setOnPreparedListener { prepared ->
                                        durationMs = prepared.duration.coerceAtLeast(1)
                                        prepared.start()
                                        isPlaying = true
                                    }
                                    prepareAsync()
                                }
                                mediaPlayer = mp
                            }
                        }
                    },
                    modifier = Modifier.aspectRatio(aspectRatio)
                )
            }

            // Custom Transport Controls
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sliderValue = if (isScrubbing) {
                    scrubFraction
                } else {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { frac ->
                        isScrubbing = true
                        scrubFraction = frac
                    },
                    onValueChangeFinished = {
                        val targetMs = (scrubFraction * durationMs).toInt()
                        runCatching { mediaPlayer?.seekTo(targetMs) }
                        positionMs = targetMs
                        isScrubbing = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val mp = mediaPlayer ?: return@IconButton
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.play_pause_cd),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                val vol = if (isMuted) 0f else 1f
                                runCatching { mediaPlayer?.setVolume(vol, vol) }
                            },
                            modifier = Modifier.testTag("mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = stringResource(R.string.mute_cd),
                                tint = if (isMuted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                isLooping = !isLooping
                                runCatching { mediaPlayer?.isLooping = isLooping }
                            },
                            modifier = Modifier.testTag("loop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Repeat,
                                contentDescription = stringResource(R.string.loop_cd),
                                tint = if (isLooping) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneEditorCard(
    scene: SceneSpec,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onUpdateScene: (SceneSpec) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${scene.sceneNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = scene.sceneTitle,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${scene.durationSeconds}s • ${scene.motif.name.lowercase().replace('_', ' ')} • ${scene.cameraMotion.name.lowercase().replace('_', ' ')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggleEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit scene ${scene.sceneNumber}",
                        tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isEditing) {
                if (scene.caption.isNotBlank()) {
                    Text(
                        text = "Caption: \"${scene.caption}\"",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (scene.narration.isNotBlank()) {
                    Text(
                        text = "Voiceover: ${scene.narration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                OutlinedTextField(
                    value = scene.caption,
                    onValueChange = { onUpdateScene(scene.copy(caption = it)) },
                    label = { Text("On-Screen Caption") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scene.narration,
                    onValueChange = { onUpdateScene(scene.copy(narration = it)) },
                    label = { Text("Voiceover Narration") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Camera Motion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CameraMotion.entries.take(4).forEach { motion ->
                        FilterChip(
                            selected = scene.cameraMotion == motion,
                            onClick = { onUpdateScene(scene.copy(cameraMotion = motion)) },
                            label = {
                                Text(
                                    motion.name.lowercase().replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                Text(
                    text = "Particles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ParticleEffect.entries.take(4).forEach { fx ->
                        FilterChip(
                            selected = scene.particleEffect == fx,
                            onClick = { onUpdateScene(scene.copy(particleEffect = fx)) },
                            label = {
                                Text(
                                    fx.name.lowercase(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onToggleEdit, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%02d:%02d".format(mins, secs)
}
