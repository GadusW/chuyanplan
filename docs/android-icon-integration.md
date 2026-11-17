# Android 图标接入

0.23.6 起，栖声 · Stillune 的圆头双弧图标使用 [SVG 母版](../assets/branding/stillune-icon.svg)。`app/build.gradle.kts` 直接引用 `assets/android/res/`。Manifest 的 `android:icon` 和 `android:roundIcon` 分别指向 `@mipmap/ic_launcher` 和 `@mipmap/ic_launcher_round`。

| 资源 | 用途 |
| --- | --- |
| `assets/branding/stillune-icon.png` 与 `stillune-icon-source-square.png` | 圆角展示图与完整方形 PNG |
| `assets/android/res/drawable/ic_launcher_*.xml` | 矢量前景、纯色背景、挖空白色刻纹的主题单色矢量 |
| `assets/android/res/mipmap-anydpi-v26/` 与 `mipmap-anydpi-v33/` | Android 8+ 自适应图标及 Android 13+ 主题图标 |
| `assets/android/res/mipmap-*/` | 48 至 192 px 多密度 PNG 及圆形回退 |
| `assets/android/res/drawable-nodpi/stillune_foreground.png` | Compose 无封面图与媒体会话默认封面 |
| `app/src/main/res/drawable/ic_notification.xml` | 同形单色通知符号 |
| `assets/android/play-store-512.png` | 512×512 的 sRGB 不透明商店图，未预裁圆角 |
| `assets/android/previews/` | 圆形、圆角方形、超椭圆与主题图标蒙版预览 |

Android 自适应图标使用 108dp 图层，母版映射到中央 72dp 视口。导出工具检查前景像素位于直径 66dp 的安全区内，同时检查商店图透明度与文件大小。测试和签名结果见 [Android 构建记录](validation/android-0.23.6.json)。品牌方形稿、旧版及商店图均重用同一视口构图。

在仓库根目录运行：

```sh
python -m pip install Pillow resvg-py
python tools/export_android_icons.py
```

生成 [导出报告](../assets/android/export-report.json)。设备启动器负责实际遮罩，`previews/` 中的图像为静态模拟。商店图用于单独上传商店后台。

[Android 自适应图标](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) · [Google Play 图标规范](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
