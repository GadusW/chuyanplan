# Android 图标核对与接入说明

目标平台为 Android，软件名为 **栖声 · Stillune**。当前仓库只有品牌素材、图册及图标资源，没有 Gradle 工程、应用模块、播放器代码或可安装 APK。

## 核对结论

原构想中的 Kotlin、Jetpack Compose、Media3、Room、DataStore 皆属于安卓开发方向；目前仅是技术规划，未实现或验证功能。名称与品牌设计适用于 Android，无须重新命名或改变已经确认的图案。

需要纠正的是图标资源的用途：透明圆角图是品牌展示版，不能直接作为商店上传图或自适应图标背景。此前按 Google Play 规则讨论的 30% 圆角，只适用于其商店展示；Android 桌面的最终遮罩由设备与启动器决定。

## 已准备资源

| 路径（相对仓库根目录） | 用途 |
| --- | --- |
| `assets/branding/stillune-icon.png` | 已批准的圆角品牌展示图，保持不变 |
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

前景适配稿使用图像生成工具从已批准的方形原图提取，保留原有月光色彩与弧线。正式品牌图未被重绘覆盖。背景独立为靛蓝渐变，跟随系统裁切。完整前景置于中央直径 66dp 的安全圆内，按可见像素到中心的最大距离校验，并保留缩放余量；图案宽高均达到 48dp，避免过度缩小。

单色版本只携带图案的 alpha 形状，由主题与启动器着色，不保留彩色渐变。前景、背景均未预裁圆角，也未增加外围阴影。

## 接入未来的应用模块

创建 Android 工程后，将 `assets/android/res/` 的内容合并到应用模块的 `src/main/res/`。合并已有 `strings.xml`，不要覆盖其他字符串。使用 compileSdk 33 或以上编译带单色图层的资源；实际 minSdk、targetSdk 在开发时按产品需求与最新发布要求确定，当前未作产品版本承诺。

在应用现有 Manifest 的 `<application>` 上设置：

```xml
android:label="@string/app_name"
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

这些是属性片段，不是一个可独立安装的应用。不要将 `play-store-512.png` 或圆角展示图用于替代上述自适应资源。Google Play 图片单独上传到商店后台。

重新导出：安装 Pillow 后，在仓库根目录运行 `python tools/export_android_icons.py`。原始图片与前景源文件都是输入；导出脚本仅执行资源缩放、对齐、格式转换及预览裁切。

## 验证范围

导出过程检查 PNG 尺寸、alpha、文件大小、安全圆及 XML 语法，生成 `assets/android/export-report.json`。静态预览已检查，所有核心图形位于不同遮罩内。资源还使用本机 Android SDK Build Tools 36.0.0 / android-36 做独立 AAPT2 编译与链接验证。

尚未创建或构建完整应用，也未进行真机或模拟器安装测试。启动器动画、不同厂商的实际缩放、深浅色主题和小尺寸可读性，需在应用模块建立后继续验收；静态预览不等于系统截图。

## 官方依据

- [Android 自适应图标：分层、108dp 图层、66dp 安全区及单色图层](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Google Play 图标规范：512px、不透明、未裁角、文件大小及系统圆角](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
