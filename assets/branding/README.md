# Stillune 品牌图标

## 0.23.6 扁平双弧定稿

开发者确认微调稿后授权矢量定稿并应用。以 ImageGen 双弧预览为构图参考，用同心圆弧与半圆端帽重建 SVG。外弧与内弧的中心线半径为 280 和 171，宽度为 96 和 51，单位为 1024 画布坐标。两组端点错开，白色内嵌刻纹沿外弧，泡泡糖粉圆点与双弧共用圆心。色值与来源见 [provenance.json](provenance.json)。

- [stillune-icon.svg](stillune-icon.svg) 为矢量母版。
- `stillune-icon-source-square.png` 和 `stillune-icon.png` 为方形和圆角导出稿。
- `tools/export_android_icons.py` 生成 Android 图标、商店图、通知符号和静态裁切预览。
- 公开检索发现 Pocket Casts 也是双层开口弧形。两者在端点、开口、粗细层次、刻纹与中心元素上有区别，尚未进行目标市场的完整在先权利检索。[Pocket Casts 官方图标](https://apps.apple.com/us/app/pocket-casts-podcast-player/id414834813)
- 定稿参考少量形状、清晰边缘、小尺寸可辨与光学居中原则。[Apple App icons](https://developer.apple.com/design/human-interface-guidelines/app-icons) · [IBM App icons](https://www.ibm.com/design/language/iconography/app-icons/design/)

## 历史设计

0.17.0–0.23.5 的液态玻璃稿及原始位图可通过相应 Git 历史查阅。0.18.0 根据手机圆形图标照片校准前景位置，0.21.0 换成雾白至冰蓝背景。旧版生成和校准记录保存在 `provenance.json` 的历史字段。
