package com.android.example.cameraxbasic.fragments.encode

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.camera.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val mimeAvc = MediaFormat.MIMETYPE_VIDEO_AVC
private const val mimeHevc = MediaFormat.MIMETYPE_VIDEO_HEVC

/**
 * Based on https://github.com/dburckh/bitmap2video
 *
 * also see https://source.android.com/compatibility/6.0/android-6.0-cdd#5_2_video_encoding
 */
@SuppressLint("RestrictedApi")
class MediaCodecEncoder(
    private val width: Int,
    private val height: Int,
    private val bitRate: Int,
    private val frameRate: Int
) {

    private val avcSupported by lazy {
        isSupported(mimeAvc)
    }

    private var alwaysFallbackToHevc = false

    suspend fun encode(imageFiles: List<File>, destination: File, fps: Float): File? {
        return withContext(Dispatchers.Default) {
            doEncode(imageFiles, destination, fps)
        }
    }

    private fun doEncode(imageFiles: List<File>, destination: File, fps: Float): File? {
        val mime = if (alwaysFallbackToHevc || !avcSupported || isWhiteVideoDevice()) {
            mimeHevc
        } else {
            mimeAvc
        }

        val config = MediaConfig(mime, width, height, bitRate, frameRate)

        return try {
            doEncode(config, destination.path, imageFiles)
        } catch (e: EncodeException) {
            if (!alwaysFallbackToHevc) {
                alwaysFallbackToHevc = true
                doEncode(config.copy(mimeType = mimeHevc), destination.path, imageFiles)
            } else {
                throw e
            }
        }
    }

    private fun doEncode(config: MediaConfig, path: String, imageFiles: List<File>): File? {
        val encoder = FrameEncoder(config, path)

        try {
            encoder.start()
            Logger.d("Encoder", "encoding with $config started")

            imageFiles.map { file ->
                Logger.d("Encoder", "encoding ${file.name}")
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                encoder.createFrame(bitmap)
            }

            encoder.release()

            return File(path).takeIf { it.exists() }
        } catch (e: Exception) {
            throw EncodeException("MediaCodecEncoder: failed to encode with $config", e)
        } finally {
            Logger.d("Encoder", "encoding finished")
            encoder.release()
        }
    }

    private fun isWhiteVideoDevice(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (
            Build.MANUFACTURER.contains("huawei", ignoreCase = true) ||
                Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.contains("redmi", ignoreCase = true) ||
                Build.MANUFACTURER.contains("oppo", ignoreCase = true) ||
                Build.MANUFACTURER.contains("sony", ignoreCase = true)
            )
    }

    private fun isSupported(mimeType: String): Boolean {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }
}

class EncodeException(message: String, cause: Throwable) : Exception(message, cause)
