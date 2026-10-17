package com.andrerinas.openheadunit.web

import android.app.Application
import android.os.SystemClock
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

// HTTP-only tests use the JVM providers, following LocaleHelperTest; Android .so files cannot run on Linux.
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BrowserServerTest {
    private lateinit var server: BrowserServer
    private lateinit var session: BrowserSession
    @Before fun start() {
        session = BrowserSession(SystemClock::elapsedRealtime) { _, _, _ -> }
        server = BrowserServer(RuntimeEnvironment.getApplication(), session, 0)
        server.start(2000, true)
    }
    @After fun stop() { server.stop() }
    private fun get(path: String, token: String? = null, data: String? = null, origin: String? = null): Pair<Int, ByteArray> {
        val c = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
        c.connectTimeout = 2000; c.readTimeout = 2000
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (origin != null) {
            // HttpURLConnection silently strips Origin. Send the real header to test server rejection.
            java.net.Socket("127.0.0.1", server.listeningPort).use { socket ->
                socket.soTimeout = 2000
                val content = data ?: "{}"
                val wire = "POST $path HTTP/1.1\r\nHost: 127.0.0.1:${server.listeningPort}\r\n" +
                    "Origin: $origin\r\nContent-Type: application/json\r\nContent-Length: ${content.toByteArray().size}\r\nConnection: close\r\n\r\n$content"
                socket.getOutputStream().write(wire.toByteArray())
                val status = socket.getInputStream().bufferedReader().readLine().split(" ")[1].toInt()
                return status to byteArrayOf()
            }
        }
        if (data != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(data.toByteArray()) }
        }
        return try {
            val code = c.responseCode
            code to (if (code < 400) c.inputStream else c.errorStream).use { it?.readBytes() ?: byteArrayOf() }
        } finally { c.disconnect() }
    }
    private fun pair(): String {
        val result = get("/api/pair", data = "{\"code\":\"${session.code}\"}")
        assertEquals(200, result.first)
        return JSONObject(String(result.second)).getString("token")
    }
    @Test fun unauthorizedCannotReadFramesOrControl() {
        assertEquals(401, get("/api/frame").first)
        assertEquals(401, get("/api/touch", data = "{}").first)
    }
    @Test fun pairedBrowserReceivesExactFrameAndDisconnectRevokesAccess() {
        val token = pair()
        assertEquals(503, get("/api/frame", token).first)
        server.projectionActive = true
        server.frameTime = SystemClock.elapsedRealtime()
        server.frame = byteArrayOf(1, 2, 3, 4)
        val result = get("/api/frame", token)
        assertEquals(200, result.first)
        assertArrayEquals(server.frame, result.second)
        assertEquals(200, get("/api/disconnect", token, "{}").first)
        assertEquals(401, get("/api/frame", token).first)
    }
    @Test fun servesPageAndRejectsCrossOriginPairing() {
        assertEquals(200, get("/").first)
        assertEquals(200, get("/geometry.js").first)
        assertEquals(403, get("/api/pair", data = "{\"code\":\"${session.code}\"}", origin = "https://evil.example").first)
    }
    @Test fun validatesTouchSequenceAndCoordinates() {
        val token = pair(); server.projectionActive = true
        assertEquals(400, get("/api/touch", token, "{\"action\":\"move\",\"x\":0.5,\"y\":0.5}").first)
        assertEquals(200, get("/api/touch", token, "{\"action\":\"down\",\"x\":0.5,\"y\":0.5}").first)
        assertEquals(400, get("/api/touch", token, "{\"action\":\"move\",\"x\":99,\"y\":0.5}").first)
        assertEquals(200, get("/api/touch", token, "{\"action\":\"cancel\",\"x\":0.5,\"y\":0.5}").first)
    }
    @Test fun sourceArchiveIncludesBuildableSourceAndLicenses() {
        val result = get("/source.zip")
        assertEquals(200, result.first)
        val names = mutableListOf<String>()
        java.util.zip.ZipInputStream(result.second.inputStream()).use { zip ->
            while (true) names.add(zip.nextEntry?.name ?: break)
        }
        assertTrue(names.contains("app/src/main/java/com/andrerinas/openheadunit/web/BrowserServer.kt"))
        assertTrue(names.contains("LICENSE"))
        assertTrue(names.contains("licenses/WheelPlay-GPL-3.0.txt"))
        assertFalse(names.any { it.contains("local.properties") || it.startsWith(".git/") })
    }
}
