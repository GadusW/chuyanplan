# 栖声 · Stillune

<p align="center">
  <img src="assets/branding/stillune-icon.png" width="240" alt="栖声 · Stillune 新图标：纯靛蓝背景，香槟金、淡紫至冰蓝的三道液态玻璃声波与中心圆环。">
</p>

<p align="center">让声音停泊，让热爱长久。</p>

## 软件理念

栖声 · Stillune 是一个面向 **Android** 的本地音乐播放器项目，致力于将高品质播放、声音分析、从容的聆听体验与听力关怀融为一体。让珍藏的音乐长久相伴，也让双耳适时休憩。

目前已建立 **Kotlin + Jetpack Compose 原生安卓原型（0.17.0）**，支持五维本地音乐库、全屏／歌词、后台播放、离线声音画像、可选实时分析、体感沉浸、历史恢复及护耳起步功能。最低 Android 8.0（API 26），compileSdk / targetSdk 为 36。护耳首版提供收听记录与行为提醒，尚不提供经过声学校准的入耳分贝或声学剂量。

## 下载安装

开发者知识制作入口见 [零基础使用说明](docs/knowledge-workflow/使用说明.md)。可在电脑上选择文件导入、查看原件与整理笔记、用表单改稿、跨 agent 制作、审核并下载签名知识包。支持检测本机模型和后台生成任务。手机版从 0.13.0 起支持在知识书架导入签名知识包。

当前提供 **0.17.0 开发预览版（调试签名）**：

- [ARM64 手机安装包](https://github.com/GadusW/chuyanplan/releases/download/v0.17.0/Stillune-0.17.0-arm64-debug.apk)：常见 64 位安卓手机优先选择。
- [通用安装包](https://github.com/GadusW/chuyanplan/releases/download/v0.17.0/Stillune-0.17.0-universal-debug.apk)：包含多种 CPU 架构，体积更大。
- [版本说明与 SHA256 校验文件](https://github.com/GadusW/chuyanplan/releases/tag/v0.17.0)：页面底部 **Assets** 展开后可下载；`.sha256` 不是安装包。

最低 Android 8.0。后续推送会同步对应版本的安装包至 [GitHub Releases](https://github.com/GadusW/chuyanplan/releases)。

## 0.17.0 更新 · 明亮玻璃与透明导航

- 采用已确认的明亮液态玻璃声波图标，同步桌面、自适应、主题图标、通知、无封面图及商店资源。
- 界面统一玻璃亮面与细薄反射边；底部移除整体底板与分隔线，仅当前图标展开文字、显示半透明白色圆角底，切换使用弹簧过渡。
- 从点击图标中心圆形扩散至目标页面；乐库列表在底部操作区附近渐进高斯模糊，Android 8–11 使用渐隐兼容效果。
- 修复再次点击当前暂停歌曲会重载解码器的问题，保留暂停位置，并处理快速暂停／恢复的延迟回调；删去重复提示，统一主要边距、标题与横屏对齐。
- 实际界面：[乐库](docs/screenshots/glass-library-v17.png) · [播放](docs/screenshots/glass-player-v17.png) · [护耳](docs/screenshots/glass-hearing-v17.png) · [横屏](docs/screenshots/glass-landscape-v17.png)。[实现与验证范围](docs/android-glass-ui.md)。

## 0.16.0 更新 · 阅读库扩展至 60 篇

- 保留前版 28 篇，新增 32 篇独立讲解，共 60 篇。补充声压／声强／分贝、反射与相位、设备响应与指向性、声音评价方法、曲式与乐器，以及大乐队、冷爵士、自由爵士、融合爵士、民谣摇滚、前卫摇滚、后朋克、自赏、采样、合作、业余参与和专辑组织等主题。
- 每篇独立可读，正文符合 600–1200 汉字规范；当前实际为 626–822 汉字，约 3–4 分钟。每篇注明 PDF 原页或 EPUB 章节位置，全部编号、标题与整段文本已查重。选题讲解不是全书摘要或逐章重建。完整清单见[内置阅读目录](docs/knowledge-workflow/内置阅读目录.md)。
- 全部 60 篇已收入新版 APK 与第 3 版签名知识包，沿用开发者对此批资料的直接发布授权。覆盖安装时旧第 2 版及更早内容包不会遮蔽新目录；更高版签名内容包继续优先。
- 实际界面：[60 篇目录](docs/screenshots/knowledge-catalog-v16.png) · [实际阅读](docs/screenshots/knowledge-reading-v16.png)。

## 0.15.0 更新 · 批量制作与新阅读库

- 替换旧 6 篇内置样稿，内置 28 篇独立原创讲解，依据 8 本资料的选定基础概念与章节编写，覆盖声学、耳机、音乐要素、爵士／摇滚、录音与音乐社会背景。正文 638–822 个汉字，估计约 3–4 分钟；规范允许 600–1200 字、约 3–5 分钟。每篇标注 PDF 或 EPUB 定位，非全书摘要或完整改写。见[内置阅读目录](docs/knowledge-workflow/内置阅读目录.md)。
- 工作台取消原始资料 80 MB 固定上限，支持多文件选择、流式上传与 EPUB 导入／按阅读顺序提取；不取消磁盘空间与内容包解压大小校验。
- 工作台将最新知识包排在最前，明确显示每包篇数，避免误下载早期的单篇包。截图：[28 篇手机目录](docs/screenshots/knowledge-catalog-v15.png) · [实际阅读](docs/screenshots/knowledge-reading-v15.png) · [批量工作台](docs/screenshots/workbench-batch-v15.png)。
- 多选 JSON 可一次收入多篇，新增勾选与批量审核；一个知识包始终收录全部有效已审核文章，手机选一次 ZIP 即更新整个目录。任务与技能不再设置两篇／三篇的默认样稿配额，按主题清单持续分批完成。
- 本批次按开发者明确授权直接发布，旧草稿备份归档；后续仍按批次授权或审核。新版内置知识目录为第 2 版，更旧的手机导入包不会遮蔽它，更新的已验证导入包优先。

## 0.14.0 更新 · 更容易开始

- 乐库新增可随时打开的“使用指南”，直接前往导入音乐、护耳设置和知识阅读，不强制首次弹窗。
- 首次打开不再主动申请全机音频权限；点击扫描音乐时再申请，文件夹／单文件导入仍使用系统选择器。
- 知识书架支持标题／主题搜索、未读推荐，顶部即可打开导入说明；清楚区分 ZIP 知识包、PDF 教材和 APK 安装包。失败保留已有内容。
- 阅读支持标准／大字号切换，偏好在本机保留；已有收藏、已读状态和暂停后不自动续播的行为保持。
- 电脑工作台按“导入资料 → 制作草稿 → 阅读审核 → 交付手机”显示真实进度与下一步，可直接跳转操作。新增短版帮助，折叠可选模型操作，审核按钮旁显示缺项原因。用户仍需亲自核验、批准；下载知识包后仍须在手机导入。
- [零基础工作流说明](docs/knowledge-workflow/使用说明.md)已同步。界面：[软件指南](docs/screenshots/getting-started-v14.png) · [知识书架](docs/screenshots/knowledge-shelf-v14.png) · [工作台](docs/screenshots/workbench-guided-v14.png)。

## 0.13.0 更新 · 三分钟，听懂一点

- 休息提醒转为 6 篇独立知识样稿，每篇正文 600–900 个汉字，约三分钟，选题包括声波传播、频率、音色、响度、双耳定位和声音描述。依据开发者提供的两本教材的基础概念重新组织，采用原创解释与例子，标明参考书页。
- 「护耳 → 打开知识书架」可按全部、未读、收藏浏览；「此刻」右上角书本按钮也能进入。打开文章会暂停播放，关闭与读完均由用户决定何时继续播放。
- 休息提醒优先选择近期未展示的未读片段，阅读完成和收藏在本机保留。旧小札、乐评检索与翻译入口已移除。
- 知识书架可从系统文件选择器导入 ZIP 知识包。校验固定发布公钥的 Ed25519 签名、正文 SHA256、字段、大小和版本后，原子替换本地内容；失败保留原有目录。当前 APK 无联网权限，内容包先通过文件分发。
- 工作台草稿可审核后签名打包，沿用本机私钥；完整操作见 [零基础工作流说明](docs/knowledge-workflow/使用说明.md)。

## 0.12.0 更新 · 中文导读与外文乐评

- 「关于这首歌」新增 13 首华语曲目的离线导读，收录澎湃新闻、羊城晚报和 Billboard 中国的相关阅读入口。支持常用简繁曲目与艺人标签。没有内置导读时仍可查开放乐评、用百度搜索，或阅读本地聆听提示。
- 外文开放乐评节选支持切换中文与原文。附带《Midnights》一条社区乐评及 CC BY-SA 3.0 中文译文。其他支持语种使用 Google Translate 提供的 [ML Kit 设备端翻译](https://developers.google.com/ml-kit/language/translation)，首次可能下载语言包，之后可本机翻译，评论正文留在手机。见 [Google 翻译署名与说明](https://cloud.google.com/translate/attribution)。
- 语言包下载和开放库访问受网络环境影响。已有中文导读与随包译文无需网络。语言包不可用时可复制原文，自行选择百度翻译等工具。无需栖声服务器、翻译 API 密钥或按量付费。

## 0.11.0 更新 · 关于这首歌

- 「此刻」的曲目信息按钮和护耳提醒的「暂停并阅读」可打开聆听卡片。护耳入口保留触发提醒时的曲目，关闭卡片后由用户决定何时继续播放。
- 有可靠片段数据时，「下次听，可以留意」与「一分钟看懂音频分析」交替出现。缺少数据时展示教学卡，内容可离线阅读。
- 点「查找开放乐评」才联网。用歌名、艺人、专辑及本地时长核对录音和专辑，查询 MusicBrainz 和 CritiqueBrainz。开放乐评包含社区投稿，卡片区分录音和专辑评论，显示出处和适用许可。覆盖取决于开放库，也可以用浏览器继续搜索。
- 结果保存在手机上。应用只发送匹配需要的曲目标签，不上传音频、文件路径或播放历史。无需自建后端、付费搜索和云端模型。

## 0.10.0 更新：护耳，先从真实记录开始

底部新增 **乐库｜此刻｜护耳**。开启护耳后，离开页面、切换播放模式和后台播放都会继续记录本应用可观测的收听时长。

- **收听与提醒**：今日、滚动 7 日及最近七个自然日记录；20 / 40 / 60 分钟提醒，可选提醒时自动暂停。另有自选系统音量档位提醒与“降低一档”。暂停、静音不累计，拖动歌曲进度不补算时长。
- **耳机与环境**：本机耳机档案与佩戴建议。环境趋势需另行同意并授权麦克风，通过前台服务持续比较输入能量，不保存录音；支持在页面或通知中停止，总开关关闭时一起停止。
- **静默赏析**：三篇栖声原创音乐小札，约 3–5 分钟。阅读前暂停，退出不自动续播；也可以只休息，不必阅读。

**音量百分比不等于入耳声压；未校准的环境变化也不等于 dBA。** 本版不以 60 dB 作医学禁用线，不显示虚构分贝、听力评分或剂量百分比；没有录到的数据不会当作零暴露。详细范围、隐私说明、权威依据与后续校准路线见 [护耳说明](docs/hearing-care.md)。

验证通过 **35 项单元测试、27 项 API 36 模拟器设备测试，Lint 无错误**。最终文案另复核 2 项原生护耳界面测试；实际后台计时与麦克风服务启停已覆盖，真实耳机声压及厂商功耗仍需真机验证。见 [0.10.0 验证记录](docs/validation/android-0.10.0.json)。

实际界面：[护耳概览](docs/screenshots/hearing-overview-v10.png) · [环境与赏析](docs/screenshots/hearing-environment-v10.png) · [麦克风同意说明](docs/screenshots/hearing-microphone-consent-v10.png) · [静默小札](docs/screenshots/hearing-reading-v10.png) · [大字体](docs/screenshots/hearing-large-font-v10.png)。截图中的时长来自模拟器实际试音，未伪造历史。

## 0.9.0 更新

在「音频分析 → 实时分析」开启：

- **响度**：同时显示 Short Term、Integrated、Momentary Max 与当前 M，另含 LRA、4× 过采样 True Peak；提供 EBU R128、ATSC A/85 短节目、Spotify Normal / Quiet 及自定义目标参考。
- **频谱与电平**：实时对数 FFT 曲线、频点查看、FFT / 平均 / 斜率设置；L / R Peak、RMS 与峰值保持电平条。
- **瀑布与声场**：二维／三维频谱历史、五个相机角度、L / R / M / S 数据源；Polar Sample、Polar Level、原始 X=L / Y=R 的 Lissajous 与相关性。图表可独立放大。

测量使用独立的 48 kHz 浮点源文件 PCM，不采集麦克风，不改变播放输出。**累计读数属于本次连续测量片段，跳转、换曲或离开实时页面会重新累计；不代表系统／蓝牙／硬件实际输出，也不是交付认证。** 实时处理会增加计算开销，后台停止，原有离线缓存选项保留。

算法、输入范围、目标口径、官方资料与验证依据见 [实时分析说明](docs/realtime-analysis.md)。

验证通过 **30 项单元测试、21 项 API 36 模拟器设备测试，Lint 无错误**。包括五格式浮点 PCM、实际播放时暂停／跳转、FFT 与目标／视角操作；合成稳态及动态节目读数与 FFmpeg ebur128 对照。详见 [0.9.0 验证记录](docs/validation/android-0.9.0.json)。真机耗电、发热及长时间同步偏差尚待验收。

实际运行截图：[响度](docs/screenshots/realtime-loudness-v9.png) · [频谱](docs/screenshots/realtime-spectrum-v9.png) · [三维瀑布](docs/screenshots/realtime-waterfall-v9.png) · [极坐标声场](docs/screenshots/realtime-field-v9.png) · [李萨如](docs/screenshots/realtime-lissajous-v9.png)。试音与封面为原创合成素材。当前多声道源下混为立体声，未提供独立环绕声通道测量。

## 0.8.0 更新

- **跟手更连贯**：倾斜采用自适应滤波，主动绘制跟随屏幕刷新，不再逐帧额外等待 24 ms；封面和效果共享平滑后的运动数据。
- **重音更鲜明**：从当前播放的波形块检测音量上升，结合自适应音量参考与触发间隔，让安静和强节奏曲目都有起伏。持续音不反复冒充拍点。
- **水波与喷散粒子**：重音推出不规则、有干涉起伏的水波；粒子受冲量炸开并渐慢消散。倾斜带动流向，摇晃产生偏转、涡流与额外扰动，继续使用专辑配色。

验证通过 **21 个单元测试、17 个 API 36 模拟器设备测试，Lint 无错误**。新增真实脉冲音频重音检测、波纹／粒子画面响应，以及持续音不重复触发的回归检查。详见 [0.8.0 验证记录](docs/validation/android-0.8.0.json)。

模拟器短时绘制检查仍有掉帧；新增绘制预算会自动减少细节，真机手感、帧率及长期耗电仍待验收。

实现与交互边界见 [体感沉浸说明](docs/immersive-mode.md)。界面保持单击显示控制、闲置 10 秒隐藏和连续三击退出。

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
- **播放模式**：「极简」「音频分析」之外，提供「体感沉浸」。音频分析可选离线声音画像，或按需开启实时响度、频谱、电平、瀑布与声场。离线画像缓存整曲 FFT 概览、代表片段 DR 估算及声场，频谱可点按跳转；首次入库小批分析，其余充电空闲补齐，开始播放让出离线计算资源。实时分析不计算 DR，按本次连续片段累计测量。
- **悬浮导航与系统播放卡片**：半透明底部导航带轻量按压回弹；媒体通知同步歌曲、专辑、封面及播放进度，可从系统控制播放、暂停、切歌或回到播放页。外观由手机系统决定，详见 [通知与交互说明](docs/media-controls.md)。
- **体感沉浸**：封面视差、节奏波纹、节奏粒子三种效果，融合低通滤波后的倾斜与当前播放的实时 RMS。单击显示控制，闲置 10 秒隐藏，连续三击或系统返回退出。仅节奏效果按需请求 Android 波形接口所需的「录音」权限，不采集麦克风。详见 [体感沉浸说明](docs/immersive-mode.md)。
- **播放历史**：保留上次歌曲、队列、位置和随机／循环设置，重开时保持暂停；乐库右上角可浏览最近 100 首音乐。详见 [历史与恢复说明](docs/playback-history.md)。
- **护耳起步功能**：独立入口、跨页面与后台收听记录、时长／音量档位提醒、耳机档案、可选麦克风环境趋势与静默知识阅读。声学 SPL、能量剂量及经过校准的智能音量推荐尚待硬件验证，详见 [护耳说明](docs/hearing-care.md)。

声音画像的取样范围、计算口径与后台策略见 [音频分析说明](docs/audio-analysis.md)。DR 是约 1/3 处最多 20 秒的**片段估算**，不是整曲认证或音质评分；频谱覆盖整曲时间轴，来自独立的整曲离线抽样 FFT。

实际界面：[五维媒体库](docs/screenshots/library-v4.png) · [频谱概览](docs/screenshots/analysis-v4.png) · [动态与声场](docs/screenshots/field-v4.png)。

实际界面：[播放页](docs/screenshots/player-v4.png) · [全屏](docs/screenshots/fullscreen-v4.png) · [歌词](docs/screenshots/lyrics-v4.png)。操作与兼容性见 [播放页设计说明](docs/player-ui.md)，视觉约定见 [DESIGN.md](DESIGN.md)。

0.5.0 更新：[悬浮导航](docs/screenshots/player-v5.png) · [系统通知中心媒体卡片](docs/screenshots/notification-v5.png)。

0.6.0 体感沉浸：[效果选择](docs/screenshots/immersion-choices-v6.png) · [控制层](docs/screenshots/immersion-controls-v6.png) · [波纹](docs/screenshots/immersion-ripples-v6.png) · [粒子](docs/screenshots/immersion-particles-v6.png)。

0.8.0 画面：[封面视差](docs/screenshots/immersion-parallax-v8.png) · [起伏水波](docs/screenshots/immersion-ripples-v8.png) · [喷散粒子](docs/screenshots/immersion-particles-v8.png)。

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

首次打开时授权音频访问即可自动扫描。若歌曲没有出现在系统媒体库，点「导入音乐 → 选择音乐文件夹」授权对应目录。选取授权无需“所有文件访问”权限；音乐不上传。当前应用无联网权限，知识内容可以离线阅读或通过系统文件选择器更新。体感模式的波纹／粒子通过 Android Visualizer 读取本应用播放波形，需要按需授予系统「录音」权限；不使用麦克风采集。

实现边界、测试结果与后续验收见 [首版开发说明](docs/android-prototype.md)，第三方解码库说明见 [依赖说明](docs/third-party-notices.md)。

## 名称寓意

“栖”是停泊与休憩，“声”是音乐与陪伴，寓意让音乐有所栖，让聆听有归处。**Stillune** 是融合 **Still**（宁静）与 **Lune**（源自法语的月亮意象）的自造英文名，表达静夜与月色中的温柔陪伴。

## 图标设计

0.17.0 正式图标采用纯靛蓝背景，香槟金经淡紫向冰蓝过渡。三道独立嵌套的液态玻璃弧线围绕圆环舒展，以清楚的间隔、透光和细薄折射边保留声波与唱片的识别。图案按视觉重量居中，配合界面的明亮玻璃表面。

正式品牌展示版采用四角对称的圆角正方形，外围透明，让轮廓与月光般的轻盈质感相呼应。它用于 README、图册等展示，不能直接代替 Android 自适应启动图标。

图标文件：[圆角正式版](assets/branding/stillune-icon.png) · [未裁角方形源图](assets/branding/stillune-icon-source-square.png)。方形源图用于后续平台自适应图标制作。

## Android 图标资源

- [启动图标资源包](assets/android/res/)：包含前景、满幅背景、Android 8.0+ 自适应图标、Android 13+ 单色主题图标及旧版多密度资源。外形由设备桌面裁切，不预先固定圆角。
- [Google Play 图标](assets/android/play-store-512.png)：512×512、不透明、未裁角的 PNG，供商店上传；与启动图标分别准备。
- [平台适配说明](docs/android-icon-integration.md)：资源用途、接入方式、检查结果与尚未完成的工作。

圆角展示偏好继续保留；不同安卓桌面可显示圆形、圆角方形等轮廓。此前采用的 30% 圆角比例来自 Google Play 展示规范，并非安卓启动图标的统一形状要求。
