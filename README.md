# 栖声 · Stillune

<p align="center">
  <img src="assets/branding/stillune-icon.png" width="240" alt="栖声 · Stillune 正式图标：深靛蓝背景上，暖白至雾蓝的半透明弧线环绕唱片轴心，隐约构成月牙。">
</p>

<p align="center">让声音停泊，让热爱长久。</p>

## 软件理念

栖声 · Stillune 是一个面向 **Android** 的本地音乐播放器项目，致力于将高品质播放、声音分析、从容的聆听体验与听力关怀融为一体。让珍藏的音乐长久相伴，也让双耳适时休憩。

目前已建立 **Kotlin + Jetpack Compose 原生安卓原型（0.7.0）**，支持五维本地音乐库、全屏／歌词、后台播放、离线声音画像、体感沉浸及播放历史恢复。最低 Android 8.0（API 26），compileSdk / targetSdk 为 36。听力关怀等仍是后续规划，尚未实现。

## 下载安装

当前提供 **0.7.0 开发预览版（调试签名）**：

- [ARM64 手机安装包](https://github.com/GadusW/chuyanplan/releases/download/v0.7.0/Stillune-0.7.0-arm64-debug.apk)：常见 64 位安卓手机优先选择。
- [通用安装包](https://github.com/GadusW/chuyanplan/releases/download/v0.7.0/Stillune-0.7.0-universal-debug.apk)：包含多种 CPU 架构，体积更大。
- [版本说明与 SHA256 校验文件](https://github.com/GadusW/chuyanplan/releases/tag/v0.7.0)：页面底部 **Assets** 展开后可下载；`.sha256` 不是安装包。

最低 Android 8.0。后续推送会同步对应版本的安装包至 [GitHub Releases](https://github.com/GadusW/chuyanplan/releases)。

## 0.7.0 更新

- 加固体感模式切换／退出时的波形回调与资源释放，波纹和粒子共用监听。
- 放大视差封面，增加线性加速度驱动的摇晃反馈，波纹／粒子随专辑主色变化。
- 保存播放队列、位置与随机／循环设置，重开保持暂停；新增最近 100 首播放记录。

已通过 **15 个单元测试、15 个 API 36 模拟器设备测试及 Android Lint 检查**，并完成关闭进程后恢复位置的手动验证。包含 24 次效果器建立／释放和 24 次界面效果切换压力测试；不同厂商真机上的闪退、手感和长期耗电仍需复核。详见 [验证记录](docs/validation/android-0.7.0.json)、[设备测试输出](docs/validation/instrumentation-0.7.0-api36.txt) 与 [更新日志](CHANGELOG.md)。

当前界面：[播放页](docs/screenshots/player-v7.png) · [封面视差](docs/screenshots/immersion-parallax-v7.png) · [专辑色波纹](docs/screenshots/immersion-ripples-v7.png) · [粒子](docs/screenshots/immersion-particles-v7.png) · [最近播放](docs/screenshots/history-v7.png)。预览中的试音和封面为自行合成的测试素材。

## 已实现功能

- **寻找本地音乐**：授权后自动扫描系统音频媒体库；可重新扫描、选择文件夹递归扫描（包括授权的 SD 卡目录），或多选音频文件导入。支持取消扫描，以及可选的时长、大小过滤。
- **保留原文件**：只建立 Room 本地索引，不复制、转码或修改音乐文件；支持重复导入去重，保存文件夹读取授权。扫描不会访问未授权目录或绕过 Android 存储限制。
- **多格式播放**：内置 VideoLAN 官方 LibVLC 3.7.7 软件解码库，已用合成音频在 Android 模拟器验证 MP3、PCM WAV、FLAC、AAC（ADTS）和 ALAC（M4A）播放。M4A 会识别内部 ALAC 编码，不直接按后缀判断无损。
- **播放控制**：播放／暂停、上一首／下一首、拖动进度、队列、随机播放、单曲／列表循环，以及通知栏、系统媒体会话和后台播放。支持音频焦点处理与耳机断开暂停。
- **从容的界面**：常规界面保留靛蓝、月白、雾蓝品牌色，支持五维浏览、关键词搜索、无损音源筛选、迷你播放器及柔和的 Compose 转场；体感波纹／粒子采用专辑取色。
- **沉浸式播放**：播放页右上角可进入／退出全屏，隐藏底部导航和系统栏；返回键先退出全屏。背景保留克制的柔焦与毛玻璃层次，模式使用下划线选择，分析内容以细线分区，减少重复卡片。Android 12+ 使用实际模糊，旧系统以柔和渐变降级。
- **封面与歌词**：读取音频内嵌封面；点封面显示歌词，单击歌词或「返回封面」按钮切回，底部开关保留。支持导入本地 LRC / TXT、已授权目录的同名歌词自动关联、LRC 逐行跟随与双击跳转。没有歌词时提供导入入口，不显示编造歌词，也不联网抓取。
- **五维媒体库**：按歌曲、专辑、艺术家、文件夹和音频编码格式浏览，可与搜索、无损筛选叠加。
- **播放模式**：「极简」「音频分析」之外，新增「体感沉浸」。分析模式后台预先缓存整曲 FFT 频谱概览、代表片段的 DR 估算和立体声声场；频谱可点按跳转，不做实时频谱或 DR 计算。首次入库小批分析，其余在充电空闲时补齐，开始播放会让出计算资源。
- **悬浮导航与系统播放卡片**：半透明底部导航带轻量按压回弹；媒体通知同步歌曲、专辑、封面及播放进度，可从系统控制播放、暂停、切歌或回到播放页。外观由手机系统决定，详见 [通知与交互说明](docs/media-controls.md)。
- **体感沉浸**：封面视差、节奏波纹、节奏粒子三种效果，融合低通滤波后的倾斜与当前播放的实时 RMS。单击显示控制，闲置 10 秒隐藏，连续三击或系统返回退出。仅节奏效果按需请求 Android 波形接口所需的「录音」权限，不采集麦克风。详见 [体感沉浸说明](docs/immersive-mode.md)。
- **播放历史**：保留上次歌曲、队列、位置和随机／循环设置，重开时保持暂停；乐库右上角可浏览最近 100 首音乐。详见 [历史与恢复说明](docs/playback-history.md)。

声音画像的取样范围、计算口径与后台策略见 [音频分析说明](docs/audio-analysis.md)。DR 是约 1/3 处最多 20 秒的**片段估算**，不是整曲认证或音质评分；频谱覆盖整曲时间轴，来自独立的整曲离线抽样 FFT。

实际界面：[五维媒体库](docs/screenshots/library-v4.png) · [频谱概览](docs/screenshots/analysis-v4.png) · [动态与声场](docs/screenshots/field-v4.png)。

实际界面：[播放页](docs/screenshots/player-v4.png) · [全屏](docs/screenshots/fullscreen-v4.png) · [歌词](docs/screenshots/lyrics-v4.png)。操作与兼容性见 [播放页设计说明](docs/player-ui.md)，视觉约定见 [DESIGN.md](DESIGN.md)。

0.5.0 更新：[悬浮导航](docs/screenshots/player-v5.png) · [系统通知中心媒体卡片](docs/screenshots/notification-v5.png)。

0.6.0 体感沉浸：[效果选择](docs/screenshots/immersion-choices-v6.png) · [控制层](docs/screenshots/immersion-controls-v6.png) · [波纹](docs/screenshots/immersion-ripples-v6.png) · [粒子](docs/screenshots/immersion-particles-v6.png)。

0.7.0 更新：[放大封面](docs/screenshots/immersion-parallax-v7.png) · [专辑色波纹](docs/screenshots/immersion-ripples-v7.png) · [专辑色粒子](docs/screenshots/immersion-particles-v7.png) · [恢复播放](docs/screenshots/resume-v7.png) · [最近播放](docs/screenshots/history-v7.png)。

扬声器、蓝牙和有线耳机的输出跟随 Android 系统音频路由。**无损音源解码不等于全链路 bit-perfect**：MP3 / AAC 本来就是有损音源；蓝牙编码、系统混音与重采样以及硬件输出能力由设备决定。本版不实现独占 USB DAC 驱动、绕过系统混音或保证所有设备原采样率直出。

## 构建与运行

安装 JDK 17 或 21、Android SDK 36，并在未跟踪的 `local.properties` 中设置自己的 `sdk.dir`；也可以通过 Android Studio 打开仓库根目录，选择 `app` 运行。

```powershell
# Windows：编译全部 ABI 的调试 APK
.\gradlew.bat :app:assembleDebug

# 仅编译常见 64 位安卓手机版本，减少原生解码库体积
.\gradlew.bat :app:assembleDebug -Pstillune.abi=arm64-v8a

# 单元测试、静态检查，以及连接设备上的集成／界面测试
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

调试包默认生成于 `app/build/outputs/apk/debug/app-debug.apk`。`-Pstillune.abi=arm64-v8a` 的包不适用于 x86_64 模拟器；省略该参数可构建多架构版本。调试 APK 只用于初期验收，正式发布前仍需签名和真机测试。

常见 64 位安卓手机选择 ARM64 包，其他架构可构建通用包；`.sha256` 是校验文件，不是安装包。安装包通过 GitHub Releases 分发，本地导出的 `output/apk/`、构建缓存、SDK 路径和签名密钥不纳入源码仓库。

首次打开时授权音频访问即可自动扫描。若歌曲没有出现在系统媒体库，点「导入音乐 → 选择音乐文件夹」授权对应目录。选取授权无需“所有文件访问”权限；音乐不上传，应用未申请联网权限。体感模式的波纹／粒子通过 Android Visualizer 读取本应用播放波形，需要按需授予系统「录音」权限；不使用麦克风采集。

实现边界、测试结果与后续验收见 [首版开发说明](docs/android-prototype.md)，第三方解码库说明见 [依赖说明](docs/third-party-notices.md)。

## 名称寓意

“栖”是停泊与休憩，“声”是音乐与陪伴，寓意让音乐有所栖，让聆听有归处。**Stillune** 是融合 **Still**（宁静）与 **Lune**（源自法语的月亮意象）的自造英文名，表达静夜与月色中的温柔陪伴。

## 图标设计

正式图标以深靛蓝铺开静夜，暖月白经淡紫向雾蓝柔和过渡。错落的半透明弧线围绕唱片轴心舒展，既像唱片沟槽与声音涟漪，也隐约勾勒出月牙。若隐若现的边缘与轻盈的空间感，呼应“栖声”的安静气质，以及对音乐品质与双耳健康的关照。

正式品牌展示版采用四角对称的圆角正方形，外围透明，让轮廓与月光般的轻盈质感相呼应。它用于 README、图册等展示，不能直接代替 Android 自适应启动图标。

图标文件：[圆角正式版](assets/branding/stillune-icon.png) · [未裁角方形源图](assets/branding/stillune-icon-source-square.png)。方形源图用于后续平台自适应图标制作。

## Android 图标资源

- [启动图标资源包](assets/android/res/)：包含前景、满幅背景、Android 8.0+ 自适应图标、Android 13+ 单色主题图标及旧版多密度资源。外形由设备桌面裁切，不预先固定圆角。
- [Google Play 图标](assets/android/play-store-512.png)：512×512、不透明、未裁角的 PNG，供商店上传；与启动图标分别准备。
- [平台适配说明](docs/android-icon-integration.md)：资源用途、接入方式、检查结果与尚未完成的工作。

圆角展示偏好继续保留；不同安卓桌面可显示圆形、圆角方形等轮廓。此前采用的 30% 圆角比例来自 Google Play 展示规范，并非安卓启动图标的统一形状要求。
