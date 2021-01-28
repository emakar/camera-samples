package com.android.example.cameraxbasic.fragments.encode

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaMuxer
import android.view.Surface
import androidx.camera.core.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val tag = "Encoder"

@SuppressLint("RestrictedApi")
class FrameEncoder(
    private val config: MediaConfig,
    private val path: String
) {

    private var bufferInfo: MediaCodec.BufferInfo? = null

    private var encoder: MediaCodec? = null

    private var surface: Surface? = null

    private var muxer: MediaMuxer? = null
    private var isStarted = false
    private var videoTrackIndex = 0

    private val isFirstVideoSampleWrite = AtomicBoolean(false)
    private var startTimestamp = 0L
    private var endTimestamp = 0L

    @Throws(IOException::class)
    fun start() {
        bufferInfo = MediaCodec.BufferInfo()

        val mediaFormat = config.toMediaFormat()
        val videoMediaCodec = try {
            val encoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(mediaFormat)
            if (encoder != null) {
                MediaCodec.createByCodecName(encoder)
            } else {
                MediaCodec.createEncoderByType(config.mimeType)
            }
        } catch (e: Exception) {
            MediaCodec.createEncoderByType(config.mimeType)
        }
        videoMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        encoder = videoMediaCodec
        surface = videoMediaCodec.createInputSurface()

        muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        videoMediaCodec.start()
        drainEncoder(false)
    }

    fun createFrame(bitmap: Bitmap) {
        val surface = surface ?: return
        val canvas = surface.lockHardwareCanvas()
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        surface.unlockCanvasAndPost(canvas)
        drainEncoder(false)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        Logger.d(tag, "drainEncoder($endOfStream)")
        val codec = encoder!!
        val muxer = muxer!!
        val bufferInfo = bufferInfo!!

        if (endOfStream) {
            Logger.d(tag, "sending EOS to encoder")
            codec.signalEndOfInputStream()
        }
        while (true) {
            val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, timeoutUsec)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                } else {
                    Logger.d(tag, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = codec.outputFormat
                Logger.d(tag, "encoder output format changed: $newFormat")

                videoTrackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                isStarted = true
            } else if (encoderStatus < 0) {
                Logger.d(tag, "unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
            } else {
                if (writeVideoEncodedBuffer(encoderStatus)) {
                    break
                }
            }
        }
    }

    private fun writeVideoEncodedBuffer(bufferIndex: Int): Boolean {
        val codec = encoder!!
        val muxer = muxer!!
        val bufferInfo = bufferInfo!!

        if (bufferIndex < 0) {
            Logger.e(tag, "Output buffer should not have negative index: $bufferIndex")
            return false
        }
        val outputBuffer = codec.getOutputBuffer(bufferIndex)
        if (outputBuffer == null) {
            Logger.d(tag, "OutputBuffer was null.")
            return false
        }

        if (videoTrackIndex >= 0 && bufferInfo.size > 0) {
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            bufferInfo.presentationTimeUs = System.nanoTime() / 1000
            endTimestamp = TimeUnit.MICROSECONDS.toMillis(bufferInfo.presentationTimeUs)
            if (!isFirstVideoSampleWrite.get()) {
                Logger.i(
                    tag,
                    "First video sample written."
                )
                isFirstVideoSampleWrite.set(true)
                startTimestamp = endTimestamp
            }
            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
        }
        codec.releaseOutputBuffer(bufferIndex, false)

        return bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    fun release() {
        Logger.d(tag, "releasing encoder objects")

        if (encoder != null) {
            // This line could be isn't only call, like flush() or something
            try {
                drainEncoder(true)
            } finally {
                releaseInternals()
            }
        } else {
            releaseInternals()
        }
    }

    private fun releaseInternals() {
        encoder?.stop()
        surface?.release()
        muxer?.release()

        encoder = null
        surface = null
        muxer = null
    }

    companion object {
        private const val timeoutUsec = 10_000L
    }
}
