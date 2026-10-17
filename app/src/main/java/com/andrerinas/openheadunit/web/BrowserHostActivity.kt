// SPDX-License-Identifier: AGPL-3.0-or-later
package com.andrerinas.openheadunit.web

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.andrerinas.openheadunit.main.MainActivity
import java.net.NetworkInterface
import java.net.Inet4Address

/** Explicit local opt-in before opening any listener. */
class BrowserHostActivity : Activity() {
    private lateinit var status: TextView
    private val refresh = android.os.Handler(android.os.Looper.getMainLooper())
    private val update = object : Runnable {
        override fun run() { showStatus(); refresh.postDelayed(this, 2000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The upstream historical DiAuto rename dialog does not apply to this independent app.
        com.andrerinas.openheadunit.App.provide(this).settings.renameNoticeShown = true
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        fun text(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, pad / 2)
            layout.addView(this)
        }
        text("wheelaa", 36f)
        text("Android Auto → 车机浏览器 / Browser", 19f)
        text("1. 接收端与车机连接同一局域网。\n2. 启动网页服务，在车机浏览器输入地址与配对码。\n3. 打开 Android Auto 连接页，按 USB / 无线说明连接手机。\n4. 保持接收端投影页面在前台、屏幕常亮。", 16f)
        status = text("", 18f).apply { setTextIsSelectable(true) }
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        button("启动网页服务 / Start web") {
            try { BrowserBridge.start(this); showStatus() }
            catch (_: Exception) { status.text = "端口 8080 无法启动，请关闭占用此端口的应用后重试。" }
        }
        button("Android Auto 连接 / Connect") { startActivity(Intent(this, MainActivity::class.java)) }
        button("停止服务并清除配对 / Stop & unpair") { BrowserBridge.stop(); showStatus() }
        text("首版：JPEG ≤10 fps、单指触控；音频在接收端或车载蓝牙播放。需要独立安卓接收设备；同机自连接仅供实验。\n仅在可信局域网使用，HTTP 未加密。请在停车后配置和测试。\n基于 DiAuto / Open Headunit 与 WheelPlay，许可证和完整源码可在网页底部获取。", 14f)
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    override fun onResume() { super.onResume(); refresh.post(update) }
    override fun onPause() { refresh.removeCallbacks(update); super.onPause() }
    private fun showStatus() {
        val listener = BrowserBridge.server
        status.text = if (listener == null) "网页服务未启动 / Web server stopped" else {
            val addresses = runCatching {
                NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { "http://${it.hostAddress}:8080" }.distinct()
            }.getOrDefault(emptyList())
            "${addresses.joinToString("\n").ifEmpty { "未找到局域网 IPv4，请检查 Wi-Fi / 热点。" }}\n配对码 / Code: ${listener.session.code}\n" +
                if (listener.projectionActive) "投影采集已启用" else "等待前台 Android Auto 投影"
        }
    }
}
