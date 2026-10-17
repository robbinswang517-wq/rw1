# wheelaa（实验版 0.1.0）

把 Android Auto 画面串流到车机浏览器，并将单指触控回传手机。基于 [DiAuto](https://github.com/shihabal3amri/DiAuto) 的 Android Auto 接收端，借鉴 [WheelPlay](https://github.com/fython/wheelplay) 的网页串流架构。

```text
Android Auto 手机 ── USB / 无线 AAP ── wheelaa 安卓接收端
                                        │
                                 局域网 HTTP JPEG + 触控
                                        │
                                    车机浏览器
```

需要独立安卓接收设备（Android 9+）、支持 Android Auto 的手机，以及支持 ES modules、Pointer Events、AbortController 且可访问该设备局域网地址的现代车机浏览器。此应用不是将任意网页投屏到原厂 Android Auto 的插件。云环境不能直接操作你的电脑或连接手机/车机。

## Android Studio 打开和构建

1. 在 Android Studio 中打开本仓库根目录，使用 JDK 17 或兼容的 JDK 21。
2. 安装 Android SDK 36（contract 模块还需 SDK 34）、Build Tools 36.0.0、NDK `29.0.14206865` 和 CMake `3.22.1`。
3. 让 Android Studio 自动生成忽略的 `local.properties`，或设置 `ANDROID_HOME`。不要提交机器路径和签名密钥。
4. 选择 `app` 的 `githubDebug` variant，连接接收设备后运行。

```sh
./gradlew :app:testGithubDebugUnitTest :app:assembleGithubDebug --max-workers=4
npm test  # 无第三方 npm 依赖，Node.js 18+
```

Debug application ID：`io.github.robbinswang517.wheelaa.debug`。APK 位于 `app/build/outputs/apk/github/debug/`。首版只验证 githubDebug；发布签名和 Play Store 分发不在本次范围内。

云端重复设置：`bash scripts/setup-cloud.sh`；后续任务先执行 `source scripts/cloud-env.sh`。脚本使用校验过的固定 JDK/SDK、工作区缓存和当前平台代理，不写入仓库源码或密钥。可选浏览器模拟测试：安装 Playwright 后执行 `node scripts/browser-smoke.mjs`（可用 `WHEELAA_CHROMIUM` 指定 Chromium）；它使用模拟帧，不替代实机测试。

## 使用

1. 将 wheelaa 接收设备与车机浏览器连接同一局域网；普通 Wi-Fi 或可相互访问的热点均可。访客网络/客户端隔离可能阻止访问。
2. 在接收端打开 **wheelaa**，点击 **启动网页服务**，记下显示的 IPv4 地址与六位码。默认端口 `8080`；端口冲突时先关闭其他占用程序。地址变化时返回此页面查看。
3. 在车机浏览器访问该地址，输入六位码，点击 **配对并连接**。一次仅支持一个浏览器，不保存配对令牌。
4. 在接收端点击 **Android Auto 连接**，完成继承的引导和必要权限，再使用 USB 数据线或无线方式连接 Android Auto 手机。详细上游连接步骤见 [docs/CONNECTION_SETUP.md](docs/CONNECTION_SETUP.md)；设备及热点兼容性仍需实测。
5. 保持接收端 Android Auto 投影页面在前台且屏幕亮起，车机画面会自动出现。启用网页服务时强制使用可采集的 TextureView。若先连接投影再开启网页服务，返回投影页面会切换到 TextureView。
6. 网页支持单指点击、拖动与可选全屏。音频/麦克风由接收端原生链路处理，车载蓝牙音频按 DiAuto 设置配置。
7. 网页 **断开** 释放浏览器；接收端 **停止服务并清除配对** 关闭监听并撤销令牌。浏览器意外退出后，触控在约 1.5 秒无事件时取消，控制权在约 15 秒无请求后释放。停止再启动会生成新码。

服务是用户显式启动的进程内监听，不随开机自动启动；进程被系统回收后需要重新开启。切换到连接页或系统后台时停止帧采集与输入，网页等待投影恢复。不要以已打开端口判断实机投影已经成功。

## 当前能力和限制

- JPEG 拉取兼容模式，最多 10 fps、宽度最多 960 像素、JPEG quality 70；未承诺实际帧率或延迟。慢客户端仅取最新帧，不累积视频队列。
- 六位码、随机内存令牌、配对请求限流、单浏览器控制、输入校验、断线触控取消。
- 单指触控；不支持多指、网页音频/麦克风、WebRTC、熄屏/后台采集，或专门绕过车机私有网段限制的路由方案。
- HTTP 内容和配对没有传输加密，仅用于可信局域网，不要端口转发到公网。请在停车后配置和验证。
- 云端测试可验证构建和协议行为；USB/无线配对、车机浏览器显示、触控定位和音频必须使用真实设备验证。参见 [实机验证清单](docs/WHEELAA_VALIDATION.md)。

## 许可与来源

参见 [NOTICE.md](NOTICE.md)、[LICENSE](LICENSE)、[DiAuto 第三方声明](docs/THIRD_PARTY.md) 和 `licenses/`。浏览器底部的 **本版本完整源码与许可证** 下载 APK 构建时打包的源码，不包含本机路径、Git 数据、密钥或 Gradle 缓存。分发修改后的 APK 时应同时提供对应源码和所需构建说明。

原始 DiAuto 文档见 [docs/UPSTREAM_DIAUTO_README.md](docs/UPSTREAM_DIAUTO_README.md)；其中上游已验证设备和性能并不代表 wheelaa 已通过相同测试。
