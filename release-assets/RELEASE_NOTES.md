wheelaa 0.1.0-preview：Android Auto 网页串流实验版。

Android Auto 手机 → wheelaa 安卓接收端 → 车机浏览器，支持六位码配对、JPEG 画面、单指触控回传与断线触控取消。

下载 wheelaa-0.1.0-preview-debug.apk，安装到 Android 9 或更新版本的独立接收设备。使用步骤见源码包 README.md。

限制：最高 10 fps / 960 像素宽，保持接收端投影前台和屏幕常亮；音频由接收端或车载蓝牙播放。HTTP 仅用于可信局域网。尚未完成真实 USB/无线 Android Auto 与车机测试。

验证：662 项 Android/JVM 测试、5 项网页坐标测试、Chromium 模拟交互测试和 APK 签名验证通过。

附带与此 APK 一致的源码和许可证，保留 DiAuto、Open Headunit、WheelPlay、libsu 等上游声明。
