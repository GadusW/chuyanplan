---
name: Stillune
description: 栖声 Android 本地音乐播放器
colors:
  brand-navy: "#122B62"
  brand-gray: "#E8E8E8"
  brand-pink: "#F779B4"
  background: "#F3F3F5"
  surface: "#FCFCFE"
  selected: "#FBE3EF"
  ink: "#1C2335"
  muted-ink: "#535A6B"
  outline: "#D8D9E2"
spacing:
  scale: [4, 8, 12, 16, 20, 24, 28, 32]
  page: 24
  minimum-touch-target: 48
rounded:
  scale: [4, 8, 12, 20, 28]
---

# Stillune · Android 设计约定

乐库用于查找、筛选与导入音乐。播放页以专辑封面、进度和播放控制为主。护耳页在第一屏展示记录状态和收听时长，测量口径放在「数据范围与资料依据」。

## Design Token

- [Theme.kt](app/src/main/java/com/stillune/app/ui/Theme.kt) 定义品牌色、Material 3 语义色、间距和圆角。深蓝用于主要操作和标题。浅粉底搭配深蓝前景表示选中状态，并提供可读标签和无障碍选中语义。图标原粉用于小面积图形和沉浸效果。错误和分析结果使用各自的状态色。
- 浅灰用于页面背景，近白用于内容面板与导航。播放页可轻量混入当前封面色，保持文字和控件的对比度。系统窗口使用相同浅灰底。
- 间距以 4 dp 为基准。相邻键值或图标与标签使用 4–8 dp，同组条目使用 12–16 dp，页面边距为 24 dp，主要触控区域至少 48 dp。图表轴线、进度条描边及手势几何按各自的绘制要求取值。
- 卡片、内容面板和选中底复用连续圆角。内外形状使用同一个间距推导半径，例如专辑卡片的外圆角为 20 dp、封面内缩 8 dp、内圆角为 12 dp。系统组件采用同一圆角标尺，底部弹层仅上缘圆角。

## 组件选型

| 语义 | Do | Don't |
| --- | --- | --- |
| 一级目的地 | 底栏固定乐库、播放、护耳，保留文字与选中语义 | 选中后撑开入口，移动相邻入口的位置 |
| 同页视图 | 用共用的 `TabStrip` 切换歌曲／专辑、播放模式／音频分析 | 用普通文本按钮模拟互斥选中 |
| 条件筛选与单选 | 无损用 `FilterChip`，提醒间隔用单选分段按钮，持续设置用 `Switch` | 用三个独立筛选标签表达同一提醒间隔 |
| 主要动作和可点条目 | 主要动作使用填充按钮，导入方式使用可点击的 `ListItem`，次要操作使用文本或图标按钮 | 给每个可点条目相同的主按钮强调 |
| 帮助与反馈 | 字段保留状态、读数和单位。长说明用对话框，导入结果用 Snackbar，风险与授权说明仍在相应动作处展示 | 在每张卡片重复测量说明或用弹窗展示普通进度 |
| 静态内容 | 用 `ContentPanel` 承载一组相关读数或操作，用留白区分相邻区域 | 为单行标签再加一层卡片 |

组件语义参考 [Android Material 组件](https://developer.android.com/develop/ui/compose/components) 与 [Compose Material 3 主题](https://developer.android.com/develop/ui/compose/designsystems/material3)。

## 动效

| 场景 | 调整 | 理由 |
| --- | --- | --- |
| 底栏导航 | 页面即时切换。移除 340 ms 圆形揭幕、图标缩放和标签展开 | 高频导航保持目标稳定，保留点按涟漪 |
| 同步歌词 | 高亮颜色即时更新，保留随播放定位到当前行的滚动 | 颜色反映当前行，滚动帮助定位连续歌词 |
| 专辑卡片 | 保留拖拽跟手、取消回弹、切歌与封面／歌词翻转 | 手势反馈和正背面关系需要过渡 |
| 沉浸和弹层 | 沉浸保留主动选择的视差及音乐响应，弹层保留原生进出过渡 | 用户能控制沉浸效果，弹层过渡提示空间关系 |

动效与减少动态效果参考 [Apple Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)。
