package com.android.example.cameraxbasic.fragments

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    return suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    continuation.resume(cameraProviderFuture.get())
                } catch (e: CancellationException) {
                    continuation.cancel()
                } catch (e: InterruptedException) {
                    continuation.cancel()
                } catch (e: ExecutionException) {
                    continuation.resumeWithException(e.cause!!)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
        continuation.invokeOnCancellation {
            cameraProviderFuture.cancel(false)
        }
    }
}

suspend inline fun retryWithDelays(attempts: Int, delay: Long, block: () -> Unit) {
    var attempt = 0
    var originalException: Throwable? = null
    while (attempt < attempts) {
        try {
            block()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (originalException == null) {
                originalException = e
            }
            delay(delay)
            attempt++
        }
    }
    originalException?.let { throw it }
}
