package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.camera.core.Logger
import com.android.example.cameraxbasic.fragments.encode.MediaCodecEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@SuppressLint("RestrictedApi")
object Grabber {

    suspend fun process(
        context: Context,
        storage: FrameStorage,
    ): File? {
        val encoder = MediaCodecEncoder(storage.width, storage.height, 8 * 1024 * 1024, storage.fps)
        return try {
            withTimeoutOrNull(10_000) {
                createVideo(context, encoder, storage)
            }
        } finally {
            storage.release()
        }
    }

    private suspend fun createVideo(context: Context, encoder: MediaCodecEncoder, storage: FrameStorage): File? {
        return try {
            val imageFiles = storage.frames
            if (imageFiles.isNotEmpty()) {
                val destination = File(context.filesDir, "video_${storage.key}.mp4")
                val start = SystemClock.uptimeMillis()
                encoder.encode(imageFiles, destination, storage.fps.toFloat()).also {
                    Logger.d("Encode", "encoded ${destination.name} in ${SystemClock.uptimeMillis() - start}")
                }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
}
