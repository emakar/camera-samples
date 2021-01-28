package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

@SuppressLint("RestrictedApi")
object Grabber {

    suspend fun process(
        context: Context,
        storage: FrameStorage,
    ): File? {
        return try {
            withTimeoutOrNull(10_000) {
                createVideo(context, storage)
            }
        } finally {
            storage.release()
        }
    }

    private suspend fun createVideo(context: Context, storage: FrameStorage): File? {
        return try {
            val imageFiles = storage.frames
            if (imageFiles.isEmpty()) {
                return null
            }
            val destination = File(context.filesDir, "video_${storage.key}.mp4")
            destination.delete()
            val success = ffmpeg(
                arrayOf(
                    "-r",
                    storage.fps.toString(),
                    "-i",
                    "${storage.root}/frame_%d.jpg",
                    "-f",
                    "mp4",
                    "-q:v",
                    "15",
                    "-pix_fmt",
                    "yuv420p",
                    "-r",
                    "10",
                    destination.absolutePath
                )
            )
            Config.printLastCommandOutput(Log.INFO)
            if (success) {
                destination
            } else {
                destination.delete()
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun ffmpeg(args: Array<String>): Boolean {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    FFmpeg.cancel()
                }
                when (FFmpeg.execute(args)) {
                    RETURN_CODE_SUCCESS -> {
                        continuation.resume(true)
                    }
                    RETURN_CODE_CANCEL -> {
                        continuation.cancel()
                    }
                    else -> {
                        continuation.resume(false)
                    }
                }
            }
        }
    }
}
