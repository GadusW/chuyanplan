# 第三方依赖说明

栖声使用 Kotlin、AndroidX / Jetpack Compose、Room、WorkManager、Hilt、Kotlin Coroutines 及 VideoLAN LibVLC。精确版本固定于根目录与 `app/build.gradle.kts`，没有使用未知来源的预编译播放器包。FFT、片段动态与声场统计在项目内实现；Audio Lens Seekbar 仅作为交互参考，未引入其代码。

## LibVLC

- 官方 Maven 坐标：`org.videolan.android:libvlc-all:3.7.7`。
- 用于本地文件的解复用、软件解码和 Android AudioTrack 输出；不嵌入 VLC Android 应用界面。
- 官方源码及发布记录：[VideoLAN libvlcjni](https://code.videolan.org/videolan/libvlcjni)、[VLC](https://code.videolan.org/videolan/vlc)、[Android 项目](https://github.com/videolan/vlc-android)。
- 该版本官方 Maven POM 声明 LGPL 2.1；其组件及依赖还可能有单独条款。正式分发前需针对实际 AAR 所包含的组件保留版权、许可证及对应源码／重新链接要求；本说明不替代依赖内的 LICENSE / COPYING 文件。

## 开发技能

界面工作参考已安装的 `mobile-android-design`，并新增 [interaction-design](https://github.com/wshobson/agents/tree/main/plugins/ui-design/skills/interaction-design)。前者提供原生安卓设计指导，后者用于反馈、过渡与动效原则；应用代码采用 Compose，不引入其网页示例框架。技能安装在个人 Codex 目录，不随 APK 打包。

后续界面打磨还参考了 [Impeccable](https://github.com/pbakaus/impeccable) 和 [Taste Skill](https://github.com/Leonxlnx/taste-skill) 的设计与审查指南；未将其运行器、脚本或网页框架打包进应用。专辑取色、传感器处理与绘制逻辑为项目代码。

## 试音文件

`app/src/androidTest/assets/audio/` 是自行合成的低音量试音信号，不含商业歌曲，只打包进测试 APK。生成脚本为 `tools/generate_audio_fixtures.py`，需开发环境安装 `imageio-ffmpeg`；应用本身不依赖 Python 或 FFmpeg 命令行程序。
