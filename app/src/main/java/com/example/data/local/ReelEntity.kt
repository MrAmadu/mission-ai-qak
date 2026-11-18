package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AspectRatioOption
import com.example.data.model.GeneratedReel
import com.example.data.model.VideoEngineMode
import com.example.data.model.VideoStoryboard
import com.example.data.model.VisualStyle

@Entity(tableName = "generated_reels")
data class ReelEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val styleName: String,
    val aspectRatioName: String,
    val engineModeName: String,
    val durationMs: Long,
    val videoFilePath: String,
    val thumbnailPath: String,
    val storyboardJson: String,
    val hasVoiceover: Boolean,
    val createdAt: Long
) {
    fun toDomain(): GeneratedReel = GeneratedReel(
        id = id,
        title = title,
        prompt = prompt,
        style = VisualStyle.fromName(styleName),
        aspectRatio = AspectRatioOption.fromName(aspectRatioName),
        engineMode = VideoEngineMode.fromName(engineModeName),
        durationMs = durationMs,
        videoFilePath = videoFilePath,
        thumbnailPath = thumbnailPath,
        storyboard = VideoStoryboard.fromJsonString(storyboardJson),
        hasVoiceover = hasVoiceover,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(reel: GeneratedReel): ReelEntity = ReelEntity(
            id = reel.id,
            title = reel.title,
            prompt = reel.prompt,
            styleName = reel.style.name,
            aspectRatioName = reel.aspectRatio.name,
            engineModeName = reel.engineMode.name,
            durationMs = reel.durationMs,
            videoFilePath = reel.videoFilePath,
            thumbnailPath = reel.thumbnailPath,
            storyboardJson = reel.storyboard.toJsonString(),
            hasVoiceover = reel.hasVoiceover,
            createdAt = reel.createdAt
        )
    }
}
