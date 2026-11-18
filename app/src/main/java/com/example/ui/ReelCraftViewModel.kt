package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ReelDatabase
import com.example.data.model.AspectRatioOption
import com.example.data.model.GeneratedReel
import com.example.data.model.VideoDurationOption
import com.example.data.model.VideoEngineMode
import com.example.data.model.VideoStoryboard
import com.example.data.model.VisualStyle
import com.example.data.repository.ReelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ReelCraftViewModel"

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Generating(
        val stageTitle: String,
        val detail: String,
        val progress: Float,
        val previewBitmap: Bitmap? = null
    ) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
    data class Completed(val reelId: String) : GenerationUiState
}

data class CreateStudioState(
    val prompt: String = "",
    val selectedStyle: VisualStyle = VisualStyle.CINEMATIC,
    val selectedAspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
    val selectedDuration: VideoDurationOption = VideoDurationOption.SHORT_12S,
    val selectedEngineMode: VideoEngineMode = VideoEngineMode.MOTION_STUDIO_FREE,
    val includeVoiceover: Boolean = true,
    val isEnhancingPrompt: Boolean = false,
    val promptError: String? = null
)

class ReelCraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReelRepository(
        context = application.applicationContext,
        reelDao = ReelDatabase.getInstance(application.applicationContext).reelDao()
    )

    val reels: StateFlow<List<GeneratedReel>> = repository.observeAllReels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _studioState = MutableStateFlow(CreateStudioState())
    val studioState: StateFlow<CreateStudioState> = _studioState.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _rerenderState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val rerenderState: StateFlow<GenerationUiState> = _rerenderState.asStateFlow()

    private var activeGenerationJob: Job? = null

    fun observeReel(id: String): Flow<GeneratedReel?> = repository.observeReelById(id)

    fun updatePrompt(value: String) {
        _studioState.update { it.copy(prompt = value, promptError = null) }
    }

    fun applyInspirationPrompt(prompt: String, style: VisualStyle) {
        _studioState.update {
            it.copy(prompt = prompt, selectedStyle = style, promptError = null)
        }
    }

    fun selectStyle(style: VisualStyle) {
        _studioState.update { it.copy(selectedStyle = style) }
    }

    fun selectAspectRatio(ratio: AspectRatioOption) {
        _studioState.update { it.copy(selectedAspectRatio = ratio) }
    }

    fun selectDuration(duration: VideoDurationOption) {
        _studioState.update { it.copy(selectedDuration = duration) }
    }

    fun selectEngineMode(mode: VideoEngineMode) {
        _studioState.update { it.copy(selectedEngineMode = mode) }
        if (_generationState.value is GenerationUiState.Error) {
            _generationState.value = GenerationUiState.Idle
        }
    }

    fun setIncludeVoiceover(enabled: Boolean) {
        _studioState.update { it.copy(includeVoiceover = enabled) }
    }

    fun enhancePromptWithAi() {
        val current = _studioState.value
        if (current.prompt.isBlank()) {
            _studioState.update { it.copy(promptError = "Enter a short idea first so AI can expand it.") }
            return
        }
        if (current.isEnhancingPrompt) return

        viewModelScope.launch {
            _studioState.update { it.copy(isEnhancingPrompt = true, promptError = null) }
            try {
                val enhanced = repository.enhancePrompt(current.prompt, current.selectedStyle)
                _studioState.update { it.copy(prompt = enhanced, isEnhancingPrompt = false) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Prompt enhance timed out", e)
                _studioState.update {
                    it.copy(
                        isEnhancingPrompt = false,
                        promptError = "Prompt enhancement timed out. Please try again."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prompt enhance failed", e)
                _studioState.update {
                    it.copy(
                        isEnhancingPrompt = false,
                        promptError = e.localizedMessage ?: "Failed to enhance prompt."
                    )
                }
            }
        }
    }

    fun generateVideo() {
        val current = _studioState.value
        val trimmedPrompt = current.prompt.trim()
        if (trimmedPrompt.isEmpty()) {
            _studioState.update { it.copy(promptError = "Please describe the video you want to create.") }
            return
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _generationState.value = GenerationUiState.Generating(
                stageTitle = "Starting Studio",
                detail = "Preparing AI Director...",
                progress = 0.03f
            )
            try {
                val reel = repository.generateVideo(
                    prompt = trimmedPrompt,
                    style = current.selectedStyle,
                    aspectRatio = current.selectedAspectRatio,
                    duration = current.selectedDuration,
                    engineMode = current.selectedEngineMode,
                    includeVoiceover = current.includeVoiceover
                ) { progress ->
                    _generationState.value = GenerationUiState.Generating(
                        stageTitle = progress.stageTitle,
                        detail = progress.detail,
                        progress = progress.fraction,
                        previewBitmap = progress.previewBitmap
                    )
                }
                _generationState.value = GenerationUiState.Completed(reel.id)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Video generation timed out", e)
                _generationState.value = GenerationUiState.Error(
                    "AI generation timed out after 60 seconds. Please check your connection and try again."
                )
            } catch (e: CancellationException) {
                _generationState.value = GenerationUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video generation failed", e)
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "Video generation failed."
                )
            }
        }
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _generationState.value = GenerationUiState.Idle
    }

    fun dismissGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun rerenderStoryboard(reel: GeneratedReel, updatedStoryboard: VideoStoryboard) {
        viewModelScope.launch {
            _rerenderState.value = GenerationUiState.Generating(
                stageTitle = "Re-rendering Reel",
                detail = "Applying storyboard edits...",
                progress = 0.1f
            )
            try {
                val updated = repository.rerenderReel(reel, updatedStoryboard) { progress ->
                    _rerenderState.value = GenerationUiState.Generating(
                        stageTitle = progress.stageTitle,
                        detail = progress.detail,
                        progress = progress.fraction,
                        previewBitmap = progress.previewBitmap
                    )
                }
                _rerenderState.value = GenerationUiState.Completed(updated.id)
            } catch (e: CancellationException) {
                _rerenderState.value = GenerationUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Storyboard re-render failed", e)
                _rerenderState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "Failed to re-render video."
                )
            }
        }
    }

    fun resetRerenderState() {
        _rerenderState.value = GenerationUiState.Idle
    }

    fun deleteReel(reel: GeneratedReel) {
        viewModelScope.launch {
            runCatching { repository.deleteReel(reel) }
                .onFailure { Log.e(TAG, "Failed to delete reel", it) }
        }
    }
}
