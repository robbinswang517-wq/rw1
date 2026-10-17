// SPDX-License-Identifier: AGPL-3.0-or-later
package com.andrerinas.openheadunit.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Process-local listener. The visible projection owns capture; nothing starts at boot. */
object BrowserBridge {
    private val main = Handler(Looper.getMainLooper())
    private val encoder = Executors.newSingleThreadExecutor()
    private val encoding = AtomicBoolean(false)
    var server: BrowserServer? = null
        private set
    private var view: TextureView? = null
    @Volatile private var input: ((String, Double, Double) -> Unit)? = null
    private var foreground = false
    private var lastTextureUpdate = 0L
    private var capturedUpdate = 0L
    private var captureVersion = 0L

    fun start(context: Context) {
        if (server != null) return
        val session = BrowserSession(SystemClock::elapsedRealtime) { action, x, y ->
            val target = input
            val dispatch = Runnable {
                if (input === target && (foreground || action == "cancel")) target?.invoke(action, x, y)
            }
            // Teardown must cancel before clearing the input target; queued events cannot jump views.
            if (Looper.myLooper() == Looper.getMainLooper()) dispatch.run() else main.post(dispatch)
        }
        val listener = BrowserServer(context.applicationContext, session)
        listener.start(2000, true) // bind before publishing a live session
        server = listener
        main.post(tick)
    }

    fun stop() {
        server?.session?.disconnect()
        server?.stop()
        server = null
        main.removeCallbacks(tick)
    }

    fun attach(texture: TextureView, send: (String, Double, Double) -> Unit) {
        server?.session?.releaseTouch()
        captureVersion++
        view = texture
        input = send
        lastTextureUpdate = 0
        capturedUpdate = 0
        server?.frame = null
    }

    fun updated(texture: TextureView) {
        if (view === texture) lastTextureUpdate = SystemClock.elapsedRealtime()
    }

    fun foreground(active: Boolean) {
        foreground = active
        if (active) capturedUpdate = 0
        server?.projectionActive = active && view != null
        if (!active) { captureVersion++; server?.session?.releaseTouch(); server?.frame = null }
    }

    fun detach(texture: TextureView) {
        if (view !== texture) return
        server?.session?.releaseTouch()
        captureVersion++
        view = null
        input = null
        server?.projectionActive = false
        server?.frame = null
    }

    private val tick = object : Runnable {
        override fun run() {
            val listener = server ?: return
            listener.session.expire()
            val texture = view
            val connected = texture?.let { com.andrerinas.openheadunit.App.provide(it.context).commManager.isConnected } == true
            listener.projectionActive = foreground && texture != null && connected
            if (!listener.projectionActive) {
                listener.frame = null
                listener.session.releaseTouch()
                if (!connected) { captureVersion++; lastTextureUpdate = 0; capturedUpdate = 0 }
            }
            if (listener.projectionActive && texture != null && texture.isAvailable && lastTextureUpdate > capturedUpdate && !encoding.get()) {
                val width = texture.width
                val height = texture.height
                if (width > 0 && height > 0) {
                    // Keep the actual encoded frame ratio, independently of receiver screen shape.
                    val decoder = com.andrerinas.openheadunit.App.provide(texture.context).videoDecoder
                    val videoW = decoder.videoWidth.takeIf { it > 0 } ?: width
                    val videoH = decoder.videoHeight.takeIf { it > 0 } ?: height
                    val outW = minOf(videoW, 960)
                    val outH = (outW.toDouble() * videoH / videoW).toInt().coerceAtLeast(1)
                    val bitmap = runCatching { texture.getBitmap(outW, outH) }.getOrNull()
                    if (bitmap != null) {
                        val version = captureVersion
                        capturedUpdate = lastTextureUpdate
                        encoding.set(true)
                        encoder.execute {
                            try {
                                val stream = ByteArrayOutputStream()
                                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)) {
                                    main.post {
                                        if (captureVersion == version && server === listener && view === texture && foreground && listener.projectionActive) {
                                            listener.frame = stream.toByteArray()
                                            listener.frameTime = SystemClock.elapsedRealtime()
                                        }
                                    }
                                }
                            } finally { bitmap.recycle(); encoding.set(false) }
                        }
                    }
                }
            }
            main.postDelayed(this, 100) // 10 fps compatibility mode, not a 60 fps claim
        }
    }
}
