package com.android.example.cameraxbasic.fragments

import android.content.Context
import android.graphics.Bitmap

class FrameProcessor(
    private val context: Context,
    private val durationMs: Long
) {
    private var storage: FrameStorage? = null

    fun start(key: String) {
        require(storage == null || storage?.key == key) {
            "previous storage was ignored"
        }
        storage?.release()
        storage = FrameStorage(context, key, durationMs)
    }

    fun processFrame(frame: Bitmap) {
        val storage = this.storage ?: return
        storage.append(frame)
    }

    fun stop(): FrameStorage? {
        return storage.also { storage = null }
    }
}
