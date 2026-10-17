// SPDX-License-Identifier: AGPL-3.0-or-later
package com.andrerinas.openheadunit.web

import java.security.MessageDigest
import java.security.SecureRandom

/** One browser controls the session. Inspired by WheelPlay's TouchLease (see NOTICE.md). */
class BrowserSession(
    private val now: () -> Long,
    private val send: (String, Double, Double) -> Unit
) {
    private val random = SecureRandom()
    val code = (100000 + random.nextInt(900000)).toString()
    private var token: String? = null
    private var lastSeen = 0L
    private var pressed = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var attempts = 0
    private var windowStart = now()

    @Synchronized fun pair(candidate: String): String? {
        val time = now()
        if (time - windowStart >= 30_000) { windowStart = time; attempts = 0 }
        // Global budget prevents bypassing the limit by changing source addresses.
        if (++attempts > 5 || !equal(candidate, code)) return null
        expire()
        if (token != null) return null
        return ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }.also {
            token = it
            lastSeen = time
        }
    }

    @Synchronized fun authenticate(candidate: String?): Boolean {
        expire()
        if (candidate == null || token == null || !equal(candidate, token!!)) return false
        lastSeen = now()
        return true
    }

    @Synchronized fun authorizedTouch(candidate: String?, action: String, x: Double, y: Double): Boolean =
        authenticate(candidate) && touch(action, x, y)

    @Synchronized fun authorizedDisconnect(candidate: String?): Boolean {
        if (!authenticate(candidate)) return false
        disconnect()
        return true
    }

    @Synchronized fun touch(action: String, x: Double, y: Double): Boolean {
        if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) return false
        if (action !in listOf("down", "move", "up", "cancel")) return false
        if (action == "down" && pressed || action != "down" && !pressed) return false
        send(action, x, y)
        pressed = action != "up" && action != "cancel"
        lastX = x; lastY = y
        lastTouch = now()
        return true
    }

    private var lastTouch = 0L

    @Synchronized fun releaseTouch() {
        if (pressed) send("cancel", lastX, lastY)
        pressed = false
    }

    @Synchronized fun disconnect() {
        releaseTouch()
        token = null
    }

    @Synchronized fun expire() {
        if (pressed && now() - lastTouch > 1500) releaseTouch()
        if (token != null && now() - lastSeen > 15_000) disconnect()
    }

    private fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
