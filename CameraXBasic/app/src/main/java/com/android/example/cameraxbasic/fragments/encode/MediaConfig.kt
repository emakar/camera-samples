package com.android.example.cameraxbasic.fragments.encode

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaFormat

data class MediaConfig(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bitRate: Int,
    val frameRate: Int
)

fun MediaConfig.toMediaFormat(): MediaFormat {
    return MediaFormat.createVideoFormat(mimeType, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
}
