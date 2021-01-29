package com.android.example.cameraxbasic.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import java.nio.ByteBuffer
import kotlin.math.abs

object JpegToRgbConverter {

    fun jpegToRgb(image: Image): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).apply {
            buffer.get(this)
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

// https://github.com/android/camera-samples/blob/master/Camera2Basic/utils/src/main/java/com/example/android/camera/utils/YuvToRgbConverter.kt
/**
 * Helper class used to efficiently convert a [Image] object from
 * [ImageFormat.YUV_420_888] format to an RGB [Bitmap] object.
 *
 * The [yuvToRgb] method is able to achieve the same FPS as the CameraX image
 * analysis use case on a Pixel 3 XL device at the default analyzer resolution,
 * which is 30 FPS with 640x480.
 *
 * NOTE: This has been tested in a limited number of devices and is not
 * considered production-ready code. It was created for illustration purposes,
 * since this is not an efficient camera pipeline due to the multiple copies
 * required to convert each frame.
 */
class YuvToRgbConverter(private val context: Context) {

    @Synchronized
    fun yuvToRgb(image: Image, bitmap: Bitmap) {

        val yuvBytes: ByteBuffer = imageToByteBuffer(image)

        val rs = RenderScript.create(context)

        val allocationRgb = Allocation.createFromBitmap(rs, bitmap)

        val allocationYuv = Allocation.createSized(rs, Element.U8(rs), yuvBytes.array().size)
        allocationYuv.copyFrom(yuvBytes.array())

        val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        scriptYuvToRgb.setInput(allocationYuv)
        scriptYuvToRgb.forEach(allocationRgb)

        allocationRgb.copyTo(bitmap)

        allocationYuv.destroy()
        allocationRgb.destroy()
        rs.destroy()
    }

    private fun imageToByteBuffer(image: Image): ByteBuffer {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val rowData = ByteArray(planes[0].rowStride)
        val bufferSize = width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8
        val output = ByteBuffer.allocateDirect(bufferSize)
        var channelOffset = 0
        var outputStride = 0
        for (planeIndex in 0..2) {
            if (planeIndex == 0) {
                channelOffset = 0
                outputStride = 1
            } else if (planeIndex == 1) {
                channelOffset = width * height + 1
                outputStride = 2
            } else if (planeIndex == 2) {
                channelOffset = width * height
                outputStride = 2
            }
            val buffer = planes[planeIndex].buffer
            val rowStride = planes[planeIndex].rowStride
            val pixelStride = planes[planeIndex].pixelStride
            val shift = if (planeIndex == 0) 0 else 1
            val widthShifted = width shr shift
            val heightShifted = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until heightShifted) {
                val length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = widthShifted
                    buffer[output.array(), channelOffset, length]
                    channelOffset += length
                } else {
                    length = (widthShifted - 1) * pixelStride + 1
                    buffer[rowData, 0, length]
                    for (col in 0 until widthShifted) {
                        output.array()[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < heightShifted - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return output
    }
}

fun Bitmap.transform(
    rotation: Int = 0,
    scale: Float = 1f
): Bitmap {
    val changeRotation = rotation % 360 != 0
    val changeScale = !scale.equalsMostly(1f)
    if (!changeRotation && !changeScale) {
        return this
    }
    val matrix = Matrix().apply {
        if (changeRotation) {
            postRotate(rotation.toFloat())
        }
        if (changeScale) {
            postScale(scale, scale)
        }
    }
    val result = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    recycle()
    return result
}

fun Float.equalsMostly(other: Float, tolerance: Float = 1e-5f): Boolean {
    return abs(this - other) <= tolerance
}
