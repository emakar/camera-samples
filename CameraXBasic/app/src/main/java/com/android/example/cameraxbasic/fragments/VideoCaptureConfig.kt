package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import androidx.camera.core.impl.Config
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.core.impl.OptionsBundle
import androidx.camera.core.impl.UseCaseConfig
import androidx.camera.core.internal.ThreadConfig

typealias CameraXConfig = androidx.camera.core.impl.VideoCaptureConfig

/**
 * This is an original cameraX VideoCaptureConfig compatible VideoCapture
 */
@SuppressLint("RestrictedApi")
class VideoCaptureConfig(
    private val delegate: CameraXConfig
) : UseCaseConfig<VideoCapture?>,
    ImageOutputConfig,
    ThreadConfig {

    constructor(optionsBundle: OptionsBundle) : this(CameraXConfig(optionsBundle))

    /**
     * Returns the recording frames per second.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getVideoFrameRate(valueIfMissing: Int): Int {
        return delegate.getVideoFrameRate(valueIfMissing)
    }

    /**
     * Returns the recording frames per second.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val videoFrameRate: Int
        get() = delegate.videoFrameRate

    /**
     * Returns the encoding bit rate.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getBitRate(valueIfMissing: Int): Int {
        return delegate.getBitRate(valueIfMissing)
    }

    /**
     * Returns the encoding bit rate.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val bitRate: Int
        get() = delegate.bitRate

    /**
     * Returns the number of seconds between each key frame.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getIFrameInterval(valueIfMissing: Int): Int {
        return delegate.getIFrameInterval(valueIfMissing)
    }

    /**
     * Returns the number of seconds between each key frame.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val iFrameInterval: Int
        get() = delegate.iFrameInterval

    /**
     * Returns the audio encoding bit rate.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getAudioBitRate(valueIfMissing: Int): Int {
        return delegate.getAudioBitRate(valueIfMissing)
    }

    /**
     * Returns the audio encoding bit rate.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val audioBitRate: Int
        get() = delegate.audioBitRate

    /**
     * Returns the audio sample rate.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getAudioSampleRate(valueIfMissing: Int): Int {
        return delegate.getAudioSampleRate(valueIfMissing)
    }

    /**
     * Returns the audio sample rate.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val audioSampleRate: Int
        get() = delegate.audioSampleRate

    /**
     * Returns the audio channel count.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getAudioChannelCount(valueIfMissing: Int): Int {
        return delegate.getAudioChannelCount(valueIfMissing)
    }

    /**
     * Returns the audio channel count.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val audioChannelCount: Int
        get() = delegate.audioChannelCount

    /**
     * Returns the audio recording source.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getAudioRecordSource(valueIfMissing: Int): Int {
        return delegate.getAudioRecordSource(valueIfMissing)
    }

    /**
     * Returns the audio recording source.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val audioRecordSource: Int
        get() = delegate.audioRecordSource

    /**
     * Returns the audio minimum buffer size, in bytes.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or `valueIfMissing` if the value does not exist in this
     * configuration.
     */
    fun getAudioMinBufferSize(valueIfMissing: Int): Int {
        return delegate.getAudioMinBufferSize(valueIfMissing)
    }

    /**
     * Returns the audio minimum buffer size, in bytes.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    val audioMinBufferSize: Int
        get() = delegate.audioMinBufferSize

    /**
     * Retrieves the format of the image that is fed as input.
     *
     *
     * This should always be PRIVATE for VideoCapture.
     */
    override fun getInputFormat(): Int {
        return delegate.inputFormat
    }

    override fun getConfig(): Config {
        return delegate.config
    }

    companion object {
        // Option Declarations:
        // *********************************************************************************************
        val OPTION_VIDEO_FRAME_RATE = CameraXConfig.OPTION_VIDEO_FRAME_RATE
        val OPTION_BIT_RATE = CameraXConfig.OPTION_BIT_RATE
        val OPTION_INTRA_FRAME_INTERVAL = CameraXConfig.OPTION_INTRA_FRAME_INTERVAL
        val OPTION_AUDIO_BIT_RATE = CameraXConfig.OPTION_AUDIO_BIT_RATE
        val OPTION_AUDIO_SAMPLE_RATE = CameraXConfig.OPTION_AUDIO_SAMPLE_RATE
        val OPTION_AUDIO_CHANNEL_COUNT = CameraXConfig.OPTION_AUDIO_CHANNEL_COUNT
        val OPTION_AUDIO_RECORD_SOURCE = CameraXConfig.OPTION_AUDIO_RECORD_SOURCE
        val OPTION_AUDIO_MIN_BUFFER_SIZE = CameraXConfig.OPTION_AUDIO_MIN_BUFFER_SIZE
    }
}
