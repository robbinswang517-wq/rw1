// SPDX-License-Identifier: AGPL-3.0-or-later
package com.andrerinas.openheadunit.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.URI

/** JPEG pull streaming deliberately has no frame queue: slow browsers always receive the latest. */
class BrowserServer(private val context: Context, val session: BrowserSession, port: Int = 8080) : NanoHTTPD(port) {
    @Volatile var frame: ByteArray? = null
    @Volatile var frameTime = 0L
    @Volatile var projectionActive = false

    override fun serve(request: IHTTPSession): Response {
        return try { handle(request) } catch (_: Exception) { reply(Response.Status.BAD_REQUEST, "Invalid request").apply { closeConnection(true) } }
    }

    private fun handle(request: IHTTPSession): Response {
        // Consume every POST body, including disconnect/rejected requests, before reusing a socket.
        val postBody = if (request.method == Method.POST) body(request)
            ?: return reply(Response.Status.BAD_REQUEST, "Expected small JSON body").apply { closeConnection(true) }
            else null
        if (request.method == Method.GET) {
            val asset = when (request.uri) {
                "/" -> "wheelaa/index.html" to "text/html; charset=utf-8"
                "/geometry.js" -> "wheelaa/geometry.js" to "text/javascript; charset=utf-8"
                "/app.js" -> "wheelaa/app.js" to "text/javascript; charset=utf-8"
                "/style.css" -> "wheelaa/style.css" to "text/css; charset=utf-8"
                "/source.zip" -> "wheelaa-source.zip" to "application/zip"
                else -> null
            }
            if (asset != null) return headers(newChunkedResponse(Response.Status.OK, asset.second, context.assets.open(asset.first)))
        }
        // JSON + same-origin checking prevents drive-by pairing/control via a foreign website.
        val origin = request.headers["origin"]
        if (origin != null && runCatching { URI(origin).rawAuthority != request.headers["host"] }.getOrDefault(true)) {
            return reply(Response.Status.FORBIDDEN, "Origin rejected")
        }
        if (request.uri == "/api/pair" && request.method == Method.POST) {
            val token = session.pair(postBody!!.optString("code")) ?: return reply(Response.Status.FORBIDDEN, "Incorrect code, browser already connected, or too many attempts. Wait 30 seconds.")
            return json(JSONObject().put("token", token))
        }
        val token = request.headers["authorization"]?.removePrefix("Bearer ")
        if (!session.authenticate(token)) return reply(Response.Status.UNAUTHORIZED, "Pair on the receiving device")
        return when {
            request.uri == "/api/frame" && request.method == Method.GET -> {
                val bytes = frame
                if (!projectionActive || bytes == null) {
                    reply(Response.Status.SERVICE_UNAVAILABLE, "Waiting for Android Auto; keep projection visible on the receiver")
                } else headers(newFixedLengthResponse(Response.Status.OK, "image/jpeg", bytes.inputStream(), bytes.size.toLong()))
            }
            request.uri == "/api/touch" && request.method == Method.POST -> {
                val b = postBody!!
                if (!projectionActive || !session.authorizedTouch(token, b.optString("action"), b.optDouble("x"), b.optDouble("y"))) {
                    reply(Response.Status.BAD_REQUEST, "Touch unavailable")
                } else reply(Response.Status.OK, "ok")
            }
            request.uri == "/api/disconnect" && request.method == Method.POST -> {
                if (session.authorizedDisconnect(token)) reply(Response.Status.OK, "Disconnected")
                else reply(Response.Status.UNAUTHORIZED, "Pair again")
            }
            else -> reply(Response.Status.NOT_FOUND, "Not found")
        }
    }

    private fun body(request: IHTTPSession): JSONObject? {
        if (request.headers["content-type"]?.substringBefore(';') != "application/json") return null
        val size = request.headers["content-length"]?.toIntOrNull() ?: return null
        if (size !in 1..2048 || request.headers.containsKey("transfer-encoding")) return null
        val files = HashMap<String, String>()
        request.parseBody(files)
        return JSONObject(files["postData"] ?: return null)
    }

    private fun json(value: JSONObject) = headers(newFixedLengthResponse(Response.Status.OK, "application/json", value.toString()))
    private fun reply(status: Response.Status, text: String) = headers(newFixedLengthResponse(status, "text/plain; charset=utf-8", text))
    private fun headers(response: Response): Response = response.apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
        addHeader("Referrer-Policy", "no-referrer")
        addHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' blob:; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
    }
}
