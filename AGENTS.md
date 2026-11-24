# 栖声 · Stillune 项目知识库

本文件写给接手本仓库的 agent 和人：先读这里，再按需读 `docs/` 下的专题文档。
（原 `AGENTS.md` 的项目协作约定保留在文末「协作约定」一节，内容未改动。）

## 1. 项目是什么

- **栖声 · Stillune**：本地离线 Android 音乐播放器，用 Kotlin + Jetpack Compose 写，界面以「正在播放的专辑封面作为环境光的浅色玻璃」为核心视觉语言。
- 仓库：<https://github.com/GadusW/chuyanplan>，默认分支 `main`，提交信息用英文短句。
- 当前版本 **0.24.15**（`versionCode 47`，开发预览版，debug 签名）。版本号规则：`versionName` 语义化三段，`versionCode` 每次发布自增。
- 应用**没有 INTERNET 权限**：播放、分析、知识内容全部离线；知识包靠文件选择器手动导入。
- 主要能力：
  - 多目录 / 多文件导入，五种浏览维度（歌曲、专辑、艺术家、文件夹、格式），搜索与「仅无损」筛选；
  - LibVLC 解码 + AudioTrack 输出 + MediaSession，播放进度记忆与历史；
  - 离线声学分析（频谱、动态范围估计、立体声宽度、散点、签名）；
  - 实时分析（LUFS、真峰值、响度范围、频谱、瀑布、声像图）；
  - 沉浸式播放（视差、节奏波纹、全息马赛克）；
  - 护耳（聆听记录、31 天日历、环境声监测，默认关闭）；
  - 知识书架（60 篇内置文章 + 可导入签名知识包）。

## 2. 技术栈与工具链

| 项 | 值 |
| --- | --- |
| 语言 / UI | Kotlin 2.1.20，Jetpack Compose（BOM 2025.04.01，Material 3） |
| 构建 | AGP 8.13.2，Gradle 8.13（wrapper 带 `distributionSha256Sum`），KSP 2.1.20-1.0.32 |
| JDK | 17（README 允许 17/21），`compileOptions`/`kotlinOptions` 均为 JVM 17 |
| SDK | `compileSdk 36`、`targetSdk 36`、`minSdk 26` |
| DI / 存储 | Hilt 2.56.2、Room 2.7.1（`exportSchema = true`）、WorkManager 2.10.1、SharedPreferences |
| 播放 | `org.videolan.android:libvlc-all:3.7.7`（MediaPlayer + `setAudioOutput("audiotrack")`） |
| 加密 | `net.i2p.crypto:eddsa:0.3.0`（Ed25519 知识包验签） |
| 协程 | kotlinx-coroutines 1.10.2 |
| 本机 SDK | `local.properties`（已忽略）里 `sdk.dir=D:\Android\Sdk`，Build Tools 36.0.0 / android-36 |

关键 Gradle 细节（`app/build.gradle.kts`）：

- `namespace = "com.stillune.app"`，`applicationId` 相同。
- `sourceSets["main"].res.srcDir("../assets/android/res")`：图标与部分资源在仓库根的 `assets/android/res`，不是 `app/src/main/res`。
- `androidTest` 的 assets 指向 `app/schemas`（Room 导出的 schema 供迁移测试）。
- 可选参数 `-Pstillune.abi=arm64-v8a` 设置 `ndk.abiFilters`，产出单架构小包；不带参数即多架构通用包。
- **没有 `signingConfig`**：Release 之外的 APK 都用本机 debug keystore 签名（历史上一直沿用同一证书，见第 8 节）。

## 3. 构建、测试、运行

在 Windows PowerShell、仓库根目录：

```powershell
.\gradlew.bat :app:assembleDebug                       # 通用包
.\gradlew.bat :app:assembleDebug -Pstillune.abi=arm64-v8a  # ARM64 包
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug    # 单元测试 + lint
.\gradlew.bat :app:connectedDebugAndroidTest           # 仪器测试，需 API 36 x86_64 模拟器/真机
```

- 产物：`app/build/outputs/apk/debug/app-debug.apk`。
- **ARM64 包不能装进 x86_64 模拟器**；要在模拟器跑仪器测试就不加 `-Pstillune.abi`。
- 单元测试（JVM，当前 40 个）：`AudioMetricsTest`、`AudioRulesTest`、`CurlWakeTest`、`HearingRulesTest`、`ImmersionDynamicsTest`、`ImmersionSignalsTest`、`LrcParserTest`、`Mp4AudioCodecTest`、`RealtimeAnalysisTest`。仪器测试 20 个类，位于 `app/src/androidTest/java/`，依赖 `app/src/androidTest/assets/audio/` 里的样例音频（mp3/flac/wav/aac/m4a/alac）与 `assets/knowledge/edition-*.zip`。
- 改了行为就补测试，并把验证结果写进 `docs/validation/android-<version>.json`（字段：`version`、`versionCode`、`unitTests`、`artifacts[]`（`name`/`bytes`/`sha256`/`abis`/`signer_sha256`）、`originalSigningCertificatePreserved`、`identicalDexAcrossAbis`、`androidInstrumentationTestsPassed`、`device`、`instrumentationTests[]`、`verified[]`、`limitations[]`）。

## 4. 代码结构

包根：`app/src/main/java/com/stillune/app/`

| 包 | 职责 | 关键文件 |
| --- | --- | --- |
| 根 | 单 Activity 入口、应用类 | `MainActivity.kt`、`StilluneApplication.kt` |
| `data` | 音乐库扫描/去重、Room、播放记忆、歌词、M4A 编解码、封面 | `LibraryRepository.kt`、`LibraryDatabase.kt`、`LibraryGrouping.kt`、`PlaybackMemory.kt`、`Lyrics.kt`、`Mp4AudioCodec.kt`、`Track.kt`、`PlayerAssetsRepository.kt` |
| `playback` | 前台播放服务、状态、媒体会话 | `PlaybackService.kt`、`PlaybackState.kt`、`PlaybackConnection.kt` |
| `analysis` | 离线声学分析（WorkManager 队列 + 缓存）、实时分析 | `AnalysisScheduler.kt`、`AnalysisRepository.kt`、`NativePcmAnalyzer.kt`、`AnalysisCache.kt`、`RealtimeAnalysisRepository.kt`、`RealtimeProcessor.kt` |
| `health` | 护耳记录、听音时长、环境声监测、知识书架 | `HearingRepository.kt`、`HearingRecords.kt`、`HearingModels.kt`、`KnowledgeLibrary.kt`、`EnvironmentMonitorService.kt`、`HearingNotifications.kt` |
| `immersion` | 沉浸效果信号与运动 | `ImmersionSignals.kt`、`CurlWake.kt`、`HoloMosaicMotion.kt`、`LiveAudioEnergy.kt`、`MotionSensor.kt`、`WaterSurface.kt` |
| `ui` | Compose 界面、玻璃效果、导航、各页面 | `StilluneApp.kt`、`LibraryViewModel.kt`、`PlayerPage.kt`、`ImmersivePlayer.kt`、`AlbumDeck.kt`、`HealthPage.kt`、`KnowledgeReading.kt`、`GlassEffects.kt`、`CoverRefraction.kt`、`CurlRefraction.kt`、`Theme.kt` |
| `di` | Hilt 模块 | `AppModule.kt` |

### 4.1 界面与导航

`ui/StilluneApp.kt` 是唯一组合根，单个 state 对象驱动全部页面切换：

- 三个底部目的地：`0` 曲库、`1` 播放器、`2` 护耳；叠加层有全屏、沉浸、沉浸样式选择、导入、历史、引导、队列、输出。
- 返回键优先级：沉浸 → 全屏 → 护耳 → 播放器 → 专辑分组。
- 曲库筛选：`query` 同时匹配标题、艺术家、专辑、文件名；`losslessOnly`；`group` 由 `LibraryGrouping.key(track, tab)` 计算。
- 专辑列宽：`((screenWidthDp - 48) / 152).coerceIn(2, 5)`。
- 外部入口 Intent：`com.stillune.app.OPEN_PLAYER`、`com.stillune.app.OPEN_HEALTH`。
- 播放器/护耳打开时会抑制曲库 snackbar，避免遮挡。

### 4.2 播放链路

`MainActivity` → `PlaybackConnection`（绑定 `PlaybackService`）→ LibVLC `MediaPlayer`（`--no-video --no-audio-time-stretch --audiotrack-session-id=<id>`）→ AudioTrack → `MediaSessionCompat`。

- 前台服务通知渠道：`音乐播放`（IMPORTANCE_LOW）、`stillune_hearing`（IMPORTANCE_DEFAULT）；`stopWithTask=false`。
- 持有 partial wake lock（`Stillune:playback`）、AudioFocus、`becoming-noisy` 广播暂停、音频设备回调更新输出描述。
- 进度 checkpoint 约 3 秒落库，重开应用回到上次位置且保持暂停。
- 队列策略集中在 `PlaybackState.QueuePolicy.next(index, count, repeat, automatic)`：随机模式用预先算好的 `shuffleNext`/`shuffleRemaining`，避免连续重复。

### 4.3 曲库扫描

`LibraryRepository.scanMedia()` 用 `Mutex` 串行化，扫 MediaStore（API 29+ 用 `VOLUME_EXTERNAL`）：

- 只收 `AudioFiles.accepts(name, mime)` 的文件，构建 `Track`（`identity = "$volume:$folder$name"`，旧版本用 `uri`；`modifiedAt = DATE_MODIFIED * 1000`，M4A 额外识别 ALAC/AAC）。
- 与已有行合并后再删除「本次没见到且 `source == "media"`」的行——**只清理自己的媒体库记录，绝不删除手机文件**。
- 用户目录导入走 SAF `OpenDocumentTree`，`takePersistableUriPermission` 持久化；`SecurityException` 要转成中文提示。
- 返回 `ScanReport(found, skipped, failed)`，扫描可取消，按来源逐批提交。

### 4.4 分析

- **离线**：`AnalysisScheduler` 用 WorkManager 在扫描后排队，每轮最多 3 首，仅在「电量不低 + 存储可用」时跑，空闲/充电时续跑。整轨经 LibVLC 解码进本地管道（不占用音频输出与焦点、不落 PCM 文件），结果按曲目缓存进 Room。参数：44.1 kHz/16-bit/立体声，FFT 2048 + Hann，32 个对数频带（20 Hz–20 kHz），160 列时间轴，动态范围为 TT 风格片段估计，相关度/宽度。**这些是估计值，不能表述为认证 LUFS/DR 测量。**
- **实时**：`RealtimeAnalysisRepository` 另起静音 LibVLC 解码，48 kHz Float32 立体声，100 ms 块（整体延迟数百毫秒）。BS.1770-5 K 加权，400 ms 块 / 100 ms 跳，-70 LUFS 绝对值门限 + -10 LU 相对门限；真峰值 4 倍 FIR 上采样到 192 kHz；LRA 3 s/10 Hz。FFT 2048/4096/8192，25 Hz–20 kHz 对数轴，平滑 0/150/300/600 ms，斜率 0/3/6 dB/oct；瀑布 6/12/24 s，上限 240 列 × 128 bin；声像图上限 4×384 点。UI 刷新 ≤10 Hz，跳转/换曲/离开页面/进后台即重置。**测的是解码数据，不是扬声器输出。**

### 4.5 沉浸式

- 三种样式在 `immersion/ImmersionSignals.kt:44-49`：`PARALLAX`（封面视差）、`RIPPLES`（节奏波纹）、`HOLO_MOSAIC`（全息马赛克）。历史值 `PARTICLES` 迁移映射为 `HOLO_MOSAIC`，新增样式必须保留旧值映射。
- `Visualizer` 只读本应用 `AudioSessionId`（≤50 Hz，`AS_PLAYED`，8-bit 波形 RMS，失败回落 PCM RMS）。它需要 `RECORD_AUDIO` 运行时权限，但**不采集麦克风**——文案和权限说明要讲清楚，别写成录音。
- 运动数据取旋转矢量/陀螺仪，缺失时退回重力；信号插值 32 ms，瞬态触发间隔约 160 ms；水波用高度场，马赛克用 GLES3 shader（`res/raw/holo_mosaic_fragment.glsl`）。
- 退出必须释放全部传感器、Visualizer、GL 资源；系统开启「移除动画」时退化为静态。

### 4.6 玻璃效果（性能与兼容红线）

`ui/GlassEffects.kt`：

- 真实折射依赖 `android.graphics.RuntimeShader`，只有 `SDK_INT >= 33` 且拿到 backdrop 才启用（Android 13+）；
- 模糊/遮罩用 `RenderEffect`（31+），更低版本退回离屏合成 + 半透明玻璃；
- 折角歌词用 `CurlRefraction`/`CurlWake`，动画时长上限：封面沉降 230 ms、翻专辑 300 ms、页面揭示 340 ms；
- 想保持「同一帧一致」，不要在组合里做每帧重建 shader；低端机必须仍可读，不能靠重模糊遮住问题。

## 5. 数据模型（Room v4）

库文件 `stillune-library.db`，`exportSchema = true`，schema 落在 `app/schemas/`。

| 表 | 说明 |
| --- | --- |
| `tracks` | 曲目元数据 + `source`（`media` / SAF）+ `identity` + `modifiedAt` |
| `audio_analysis` | 离线声学分析缓存（频谱、DR 估计、相关度、宽度、平衡、散点、签名） |
| `playback_resume` | 断点续播记录 |
| `listening_history` | 播放历史 |
| `hearing_minutes` | 护耳分钟桶（主键 `startMs`，含 `listenedMs`、`headphoneMs`、`abovePreferenceMs`） |

迁移必须齐全并保留：`MIGRATION_1_2`（`tracks.modifiedAt` + `audio_analysis`）、`MIGRATION_2_3`（`playback_resume` + `listening_history`）、`MIGRATION_3_4`（`hearing_minutes`）。加表/加列时同步升 `version`、补 `androidTest` 迁移测试、提交新 schema JSON。

## 6. 护耳模块

- 独立底部入口，**默认关闭**，用户主动开启才记录。
- 时长基于单调时钟 `ListeningClock`，按墙钟分桶，500 ms 分辨率；暂停/静音不累计；每 5 秒写 Room，暂停与退出时 flush；历史只保留 31 天。
- 环境声监测是独立的 `microphone` 前台服务：48 kHz PCM16 单声道，优先 `UNPROCESSED`，失败退 `VOICE_RECOGNITION`；约 3 秒基线，展示相对值 `10*log10(P/P0)`，上升约 10 dB 持续 10 秒才提醒，冷却 10 分钟。
- 没有校准，**不得展示 dBA、剂量或「安全音量」结论**；只能说记录与提醒。
- `allowBackup=false`：护耳数据不参与系统备份。

## 7. 知识书架与知识包

`health/KnowledgeLibrary.kt` 负责校验与导入：

- 包是 ZIP，必须**恰好**包含 `manifest.json`、`articles.json`、`signature.ed25519`；解压总量上限 2 MB（`KnowledgePackage.LIMIT = 2_000_000`）。
- 签名：Ed25519（EdDSA over SHA-512），签名 64 字节；公钥固定为 `app/src/main/assets/knowledge/publisher.key`（32 字节）。换密钥等于需要发新版 APK。
- `manifest.json`：`schema == 1`、`version > 0` 的整数、`articles_sha256` 必须等于 `articles.json` 的 SHA-256、`count` 必须与实际条数一致；文章数 1–200，文章 id 匹配 `[a-z0-9][a-z0-9-]{0,63}`。
- 任一校验失败就保留原目录、报中文错误——**原子替换**，不能留下半套内容。
- 内置内容：`app/src/main/assets/knowledge/builtin.json`（60 篇、8 本书）+ `builtin-version.json`（当前 `{"version":3}`）；更高版本的签名包优先于内置目录，旧版包不得遮蔽新目录。
- 手机端从 0.13.0 起支持手动导入；生成本机包 ≠ 已推到用户设备，线上分发与自动更新尚未接入。

## 8. 版本、签名与发布

先完成构建与验证，再推源码并上传 APK：

1. 升 `versionCode`（每次 +1）与 `versionName`；更新 `README.md` 下载链接、`CHANGELOG.md`；写 `docs/validation/android-<version>.json`。
2. 构建两个包并生成校验文件：`Stillune-<version>-arm64-debug.apk`、`Stillune-<version>-universal-debug.apk`，各自 `.sha256`（内容为十六进制摘要）。
3. 在 `https://github.com/GadusW/chuyanplan/releases` 的 tag `v<version>` 上传 4 个资产（APK 不进 Git 历史）。用 draft → 上传 → 校验 `state=uploaded`、`size`、`digest: sha256:<hash>` → 再 `draft=false, prerelease=true` 的顺序最稳；不要覆盖内容不同的同名资产。
4. 发布后核对资产名称、大小、校验值，并确认 README 链接可用。

签名约束：

- 历史版本签名证书 SHA-256：`ec952e205f9c487f588f9a74ead304334bac3aab769d011fb043817a54924121`（0.24.15 两个包一致）。**必须沿用同一 keystore**，否则用户无法覆盖安装。
- 密钥、`local.properties`、SDK 路径、访问令牌一律不提交；发布脚本可临时放在已忽略的 `tmp/`，用完不入库。
- 参考体积：0.24.15 arm64 ≈ 75.3 MB，universal ≈ 230.2 MB。

## 9. 设计系统

`DESIGN.md` 是唯一设计真相来源（`schemaVersion 2`），核心 token：

- 底色 `#EDF4F8`，玻璃面 `#F7FAFD`，墨色 `#192E42`，强调色 `#214B68`，次级文字 `#41576C`。
- 字体：品牌字 Parisienne（42sp，`res/font/parisienne_regular.ttf`，OFL 许可见 `assets/parisienne-ofl.txt`）。
- 圆角：封面 12dp，玻璃容器 22dp；页面留白 24dp。
- 原则：亮玻璃而非暗色模糊；真实封面当光源；Android 13+ 曲面折射，8–12 走半透明回退；底部三层导航带玻璃滑块；播放器封面翻转露出歌词；缺封面用固定占位，不随机配色。
- 改视觉先改 `DESIGN.md`，再改 `Theme.kt`/`GlassEffects.kt`，不要在页面里散落魔法色值。

图标：源文件在 `assets/android/res`（通过 `sourceSets` 引入），另有 `assets/branding/`；重新生成用 `python tools/export_android_icons.py`（需 Pillow，输出 `export-report.json`）。自适应图标 108dp、安全圆 66dp、半径 ≤300 px、光学偏移 (10, 16)，禁止预圆角和用 `play-store-512.png` 当图标。

## 10. 知识内容工作流

处理护耳阅读、书籍知识转化、知识包或开发者投送时，先读 `skills/stillune-knowledge/SKILL.md`，再读 `docs/knowledge-workflow/使用说明.md` 与 `内容规范.md`。

```powershell
python tools/knowledge/workflow.py status
python tools/knowledge/workflow.py ingest <文件路径> [--title 标题]
python tools/knowledge/workflow.py template <source-id> --id <文章id>
python tools/knowledge/workflow.py save <JSON路径>
python tools/knowledge/workflow.py check
python tools/knowledge/workflow.py approve <id> --reviewer <姓名> --note <批准依据>
python tools/knowledge/workflow.py keygen          # 仅首次
python tools/knowledge/workflow.py package --version <递增整数>
python tools/knowledge/workflow.py verify <包路径> --public-key <可信公钥>
python tools/knowledge/workflow.py local-draft <source-id> --model <Ollama 模型名>
python tools/knowledge/workflow.py serve           # 本机工作台
```

- 工作台：双击 `tools/knowledge/启动知识工作台.cmd`（或 `start-workbench.ps1`），本地浏览器界面；`tools/knowledge/workbench_api.py` 只监听 localhost，路由含 `/upload /ingest /extract /save /approve /approve-batch /draft-new /draft-import /source-info /notes /task /archive /restore /keygen /package /models /generate /status`，带 revision 守卫防止旧页面覆盖新稿。
- 依赖：Python 3.10+ 与 `tools/knowledge/requirements.txt`（`cryptography`）；本地模型可选，只连 `127.0.0.1:11434` 的 Ollama，不调云端 API。
- 文稿规范：每篇一个独立问题，600–1200 汉字、3–8 段、约 3–5 分钟，原创生活例子 + 结尾；`citations` 记录来源、PDF 页、书上页码与核验结果；EPUB 的 `pdf_pages` 填 spine 阅读序号，`printed_pages` 记章节标题与 EPUB 序号，不得虚构纸书页码。
- 不可伪称：批量识别、模板校验、模型生成都不等于完成事实核验；改稿后指纹失效需重新审核。
- 扫描 PDF 若无可提取文字层，必须用识图/OCR 真读原页，不能把空文字层当已读。

## 11. 目录与安全边界

| 路径 | 说明 |
| --- | --- |
| `.knowledge-workspace/` | **已忽略**：原始资料、草稿、审核记录、`keys/private.pem`、备份。绝不提交。 |
| `output/` | **已忽略**：`apk/`、`knowledge/`、`pdf/` 等本地产物。 |
| `tmp/` | **已忽略**：构建日志、发布脚本、截图等临时文件。 |
| `ocrpages/` | PDF 页面渲染结果，供阅读核对。注意它**还没写进 `.gitignore`**，`git status` 会显示为未跟踪，提交时不要顺手加进去。 |
| `.pdf-libs/`、`.tmp/`、`.workbuddy/` | 本机临时环境/工具目录，同样未被忽略，只用于本地跑工具。 |
| `app/schemas/` | Room 导出 schema，**要提交**。 |
| `assets/android/res` | 实际生效的图标资源目录。 |

其他注意：

- 别把整本书逐章压缩成「替代原书」的版本；保留出处，图表/长段引用先处理授权。
- 报告要区分「已生成包」与「已到达用户手机」。
- 无法完成的上传/构建要明确说明未完成部分，不要把「源码已推送」说成「安装包已发布」。

## 12. 文档索引

- `README.md`：功能历史、下载、构建说明；`CHANGELOG.md`：逐版变更；`DESIGN.md`：设计系统。
- `docs/android-prototype.md`：整体架构、导入/解码/播放规则。
- `docs/audio-analysis.md`：离线声学分析；`docs/realtime-analysis.md`：实时 LUFS/频谱/声像。
- `docs/immersive-mode.md`：三种沉浸效果与权限、资源释放。
- `docs/player-ui.md`、`docs/album-cards.md`、`docs/media-controls.md`、`docs/playback-history.md`：播放器与曲库交互。
- `docs/android-glass-ui.md`：玻璃与折射实现；`docs/android-icon-integration.md`：图标规范。
- `docs/hearing-care.md`：护耳规则；`docs/knowledge-workflow/`：知识制作、内容规范、内置目录。
- `docs/third-party-notices.md`：第三方许可；`docs/validation/`：每版构建与安装包验证记录。

## 13. 已知限制

- 只在 API 36 x86_64 模拟器上跑过仪器测试，仍需真机验收；`limitations` 字段要如实写。
- 无水印、无联网、无云同步、无音频输出校准，因此不提供 dBA、剂量或「比特完美」类结论。
- 知识包只能手动导入，没有在线分发和自动更新。
- 玻璃折射仅 Android 13+ 完整生效，低版本是视觉近似。

---

# 协作约定

## 知识内容工作流

- 处理护耳休息阅读、书籍知识转化、知识包或开发者投送时，先读 `skills/stillune-knowledge/SKILL.md`，接着读 `docs/knowledge-workflow/使用说明.md`。
- 工作流对不同 agent 使用同一套 JSON、校验和签名工具。原始资料、草稿、审核记录和私钥放在已忽略的 `.knowledge-workspace/`。
- 自动转化的结果进入待审核状态。用户明确审核通过后才能记录通过和打包，后续改稿必须重新审核。
- 用户明确授权某一批次免逐篇审核、直接编入安装包或发布时，按该次授权执行，并记录“开发者授权 agent 执行”的真实依据，不伪称开发者亲自审阅；该例外不自动适用于后续批次。
- 知识制作不设两篇或三篇的固定篇数配额；按独立主题和 3–5 分钟阅读量划分，较长任务分批完成并记录剩余选题。
- 手机端从 0.13.0 支持手动导入签名知识包。生成包不等于已经向用户设备推送成功；线上分发地址与自动更新还需单独接入。

## GitHub 推送与安装包

- 每次完成项目改动后，都要创建 Git 提交并推送到对应的 GitHub 仓库。

用户已明确要求：以后向本项目 GitHub 仓库推送更改时，同步上传对应版本的 Android 安装包，不能只推送源码后就报告完成。

- APK 放在 GitHub Releases 的 Assets 中，不加入 Git 提交历史。
- 提供 ARM64 手机包、通用包，以及各自的 SHA256 校验文件；README 保留可用的下载链接和版本说明。
- 功能版本更新时，先完成构建与适当验证，再推送源码并上传对应版本；发布完成后核对资产名称、大小和校验值。
- 仅文档更改、版本号不变时，也要检查对应 Release 的安装包是否齐全；已有且校验一致的资产可以复用，无须重复上传或覆盖。
- 延用已有安装包签名，避免更换签名导致用户无法覆盖安装。不提交签名密钥、SDK 路径或凭据。
- 如果上传失败，明确报告未完成的部分，不把“源码已推送”描述为安装包已发布。
