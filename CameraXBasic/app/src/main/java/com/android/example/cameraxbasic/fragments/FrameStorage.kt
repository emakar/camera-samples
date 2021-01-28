package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.Logger
import java.io.File
import java.io.IOException
import java.util.ArrayList
import kotlin.math.ceil

@SuppressLint("RestrictedApi")
class FrameStorage(
    context: Context,
    val key: String,
    private val maxDuration: Long
) {

    private val times = arrayListOf<Long>()
    private val root = File(context.filesDir, "frames_$key")

    private val _frames = ArrayList<File>(64)
    val frames: List<File> get() = _frames.toList()

    private var allFrames = 0

    private val duration: Long
        get() {
            return if (times.size > 1) times.last() - times.first() else 0L
        }

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    val fps: Int
        get() = ceil(frames.size.toDouble() / duration.toDouble() * 1000.0).toInt()

    init {
        root.mkdir()
    }

    fun append(frame: Bitmap) {
        width = frame.width
        height = frame.height

        val time = System.currentTimeMillis()
        while (times.isNotEmpty() && _frames.isNotEmpty() && time - times.first() > maxDuration) {
            times.removeAt(0)
            val firstFrame = _frames.first()
            if (firstFrame.exists()) {
                firstFrame.delete()
            }
            _frames.removeAt(0)
        }

        try {
            if (root.exists()) {
                val file = File(root, "frame_$allFrames.jpg")
                file.outputStream().use {
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, it)
                }
                _frames.add(file)
                times.add(time)
                allFrames++
            }
        } catch (e: IOException) {
            Logger.e("Encode", "failed to write frame")
        }
    }

    fun release() {
        root.deleteRecursively()
        _frames.clear()
    }
}
