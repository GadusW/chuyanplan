# Android 图标核对与接入说明

目标平台为 Android，软件名为 **栖声 · Stillune**。现已建立 `app` 原生安卓应用模块，并通过 Gradle 的资源源目录直接引用 `assets/android/res/`；同一套经过确认的图标用于实际应用，避免重复复制后不一致。

## 核对结论

原构想中的 Kotlin、Jetpack Compose、Media3、Room、DataStore 皆属于安卓开发方向。首版已采用 Kotlin、Compose、Room 与 Hilt；为内置 ALAC 等软件解码能力，实际播放内核选用 LibVLC，并以 Android 媒体会话提供系统控制。其余构想不代表已实现。名称与品牌设计适用于 Android。

需要纠正的是图标资源的用途：透明圆角图是品牌展示版，不能直接作为商店上传图或自适应图标背景。此前按 Google Play 规则讨论的 30% 圆角，只适用于其商店展示；Android 桌面的最终遮罩由设备与启动器决定。

## 已准备资源

| 路径（相对仓库根目录） | 用途 |
| --- | --- |
| `assets/branding/stillune-icon.png` | 0.17.0 批准的新玻璃声波图标，圆角品牌展示版 |
| `assets/branding/stillune-icon-source-square.png` | 保留的未裁角设计源图 |
| `assets/branding/stillune-foreground-source.png` | 从批准图案提取的透明前景适配稿 |
| `assets/android/play-store-512.png` | 512×512、sRGB、32 位不透明 PNG，无预裁圆角，低于 1024KB |
| `assets/android/res/drawable-nodpi/` | 1080×1080 前景与同形单色 alpha 图；对应 108×108dp 图层 |
| `assets/android/res/drawable/` | 无圆角的满幅背景及前景位图包装 |
| `assets/android/res/mipmap-anydpi-v26/` | Android 8.0+ 双层自适应图标，包括 roundIcon 入口 |
| `assets/android/res/mipmap-anydpi-v33/` | 增加 monochrome 图层的 Android 13+ 自适应图标 |
| `assets/android/res/mipmap-*/` | mdpi 至 xxxhdpi 的 48/72/96/144/192px 旧版图标和圆形回退图 |
| `assets/android/res/values/strings.xml` | 应用名资源 |
| `assets/android/previews/` | 圆形、圆角方形、超椭圆与主题图标的静态裁切预览，不是应用资源 |

0.17.0 按开发者“把这个作为新的应用图标”的明确授权，更换为最终确认的明亮液态玻璃声波图。前景用内置 imagegen 从确认稿提取，保留三道不交叉弧线、中心圆环与香槟金／淡紫／冰蓝透光；方形确认稿直接作为商店图和品牌图的源文件。背景独立为纯色 `#263451`，跟随系统裁切。导出保留源画布的光学位置，完整前景置于直径 66dp 的安全圆内，按可见像素到中心的最大距离校验；图案宽高均达到 48dp。

同步更新了旧版五档密度、圆形回退、Android 8+ 自适应、Android 13+ 单色主题、商店 512px、README 展示、应用内无封面图与通知单色符号。图标来源与生成说明见 [品牌资源记录](../assets/branding/README.md)。

单色版本只携带图案的 alpha 形状，由主题与启动器着色，不保留彩色渐变。前景、背景均未预裁圆角，也未增加外围阴影。

## 应用模块接入

当前 `app/build.gradle.kts` 通过 `sourceSets["main"].res.srcDir("../assets/android/res")` 直接接入资源包，Manifest 使用下列属性。首版 minSdk 26、compileSdk / targetSdk 36。

在应用现有 Manifest 的 `<application>` 上设置：

```xml
android:label="@string/app_name"
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

以上是 Manifest 的属性片段。不要将 `play-store-512.png` 或圆角展示图用于替代上述自适应资源。Google Play 图片单独上传到商店后台。

重新导出：安装 Pillow 后，在仓库根目录运行 `python tools/export_android_icons.py`。原始图片与前景源文件都是输入；导出脚本仅执行资源缩放、对齐、格式转换及预览裁切。

## 验证范围

导出过程检查 PNG 尺寸、alpha、文件大小、安全圆及 XML 语法，生成 `assets/android/export-report.json`。静态预览已检查，所有核心图形位于不同遮罩内。资源还使用本机 Android SDK Build Tools 36.0.0 / android-36 做独立 AAPT2 编译与链接验证。

现已编译完整调试应用并在 API 36 模拟器安装、查看启动器图标与应用界面。不同厂商的实际缩放、深浅色主题和小尺寸可读性仍需真机验收；资源包中的静态预览不是系统截图。图册最后一页记录的是资源准备阶段的历史检查范围，最新应用状态以 README 及首版开发说明为准。

## 官方依据

- [Android 自适应图标：分层、108dp 图层、66dp 安全区及单色图层](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Google Play 图标规范：512px、不透明、未裁角、文件大小及系统圆角](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
