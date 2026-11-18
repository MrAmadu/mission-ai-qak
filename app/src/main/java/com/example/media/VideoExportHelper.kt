package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoExportHelper {

    suspend fun saveToGallery(context: Context, videoFile: File, title: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(videoFile.exists()) { "Video file not found." }
                val safeTitle = title.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "MissionAIQadeer" }
                val displayName = "${safeTitle}_${System.currentTimeMillis()}.mp4"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_MOVIES}/Mission AI Qadeer"
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Could not create MediaStore entry.")
                    resolver.openOutputStream(uri)?.use { out ->
                        videoFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Could not open MediaStore output stream.")

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Saved to Movies/Mission AI Qadeer/$displayName"
                } else {
                    val moviesDir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                        "Mission AI Qadeer"
                    ).apply { mkdirs() }
                    val dest = File(moviesDir, displayName)
                    videoFile.copyTo(dest, overwrite = true)
                    "Saved to ${dest.name}"
                }
            }
        }

    fun shareVideo(context: Context, videoFile: File, title: String) {
        if (!videoFile.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }
}
