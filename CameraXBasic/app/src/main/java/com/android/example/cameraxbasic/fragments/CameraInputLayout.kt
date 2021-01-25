package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.Logger
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.impl.utils.futures.FutureCallback
import androidx.camera.core.impl.utils.futures.Futures
import androidx.camera.view.PreviewView
import java.util.concurrent.Executor

/**
 * This is a cameraX CameraView focus logic
 */
class CameraInputLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var downEventTimestamp = 0L
    private var upEvent: MotionEvent? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()

    private val directExecutor = Executor { it.run() }

    private val previewView: PreviewView
        get() = getChildAt(0) as PreviewView

    var camera: Camera? = null

    private fun delta(): Long {
        return System.currentTimeMillis() - downEventTimestamp
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> downEventTimestamp = System.currentTimeMillis()
            MotionEvent.ACTION_UP -> if (delta() < longPressTimeout && camera != null) {
                upEvent = event
                performClick()
            }
            else -> return false
        }
        return true
    }

    /**
     * Focus the position of the touch event, or focus the center of the preview for
     * accessibility events
     */
    @SuppressLint("RestrictedApi")
    override fun performClick(): Boolean {
        super.performClick()
        val upEvent = this.upEvent.also { this.upEvent = null }

        val x = upEvent?.x ?: x + width / 2f
        val y = upEvent?.y ?: y + height / 2f
        val camera = camera
        if (camera != null) {
            val pointFactory: MeteringPointFactory = previewView.meteringPointFactory
            val afPointWidth = 1.0f / 6.0f // 1/6 total area
            val aePointWidth = afPointWidth * 1.5f
            val afPoint = pointFactory.createPoint(x, y, afPointWidth)
            val aePoint = pointFactory.createPoint(x, y, aePointWidth)
            val future = camera.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    afPoint,
                    FocusMeteringAction.FLAG_AF
                ).addPoint(
                    aePoint,
                    FocusMeteringAction.FLAG_AE
                ).build()
            )
            Futures.addCallback(
                future,
                object : FutureCallback<FocusMeteringResult?> {
                    override fun onSuccess(result: FocusMeteringResult?) {}
                    override fun onFailure(t: Throwable) {
                    }
                },
                directExecutor
            )
        } else {
            Logger.d("CameraInputLayout", "cannot access camera")
        }
        return true
    }
}
