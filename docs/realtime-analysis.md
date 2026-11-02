# 实时音频分析

从 0.9.0 开始，在播放页的「音频分析」中选择「实时分析」。原有「声音画像」继续使用离线缓存；只有打开实时页面时才启动以下计算。竖屏纵向滚动，实时界面收起重复封面并压缩底部播放控制，歌词／队列／输出设备移入播放选项；横屏保留播放器控制。频谱、瀑布和声场可分别放大。进度拖动在松手后提交跳转，避免手指滑动时反复重建解码器。

## 信号来自哪里

实时分析使用独立、静音的 LibVLC 源文件解码器，通过本机内存管道输出 **48 kHz / Float32 / 双声道 PCM**。不保存整曲 PCM，不采集麦克风，不打开第二个音频输出，也不改变原播放器的音量或解码输出。分析以播放位置为时钟，100 ms 数据块更新，使用播放位置的单调时钟估计平稳推进；落后超过 1.2 秒时重新对齐并重置片段。

这是**源音频的近实时测量**，通常存在数百毫秒偏差，并非从实际 AudioTrack 输出截获的逐样本同步信号。系统混音、播放器临时降音量、蓝牙编码、DAC、耳机与扬声器的影响不包含在读数中。保留独立源分析通路，是因为 Android Visualizer 提供的低精度波形不足以承担完整双声道 LUFS、真峰值与相关性测量。

原始单声道分析时只计入一个声道的 K 加权能量，电平视图标明单声道；多声道暂下混为立体声并标注，不宣称原始环绕声合规。源菜单提供左右声道能量平均、L、R、M=(L+R)/2、S=(L−R)/2，**只控制频谱和瀑布**，不改变响度、电平、声场或播放输出；不虚构不存在的环绕声输入。

## 响度与真峰值

- **Short Term**：3 秒滑动窗口，不满窗口显示破折号。
- **Integrated**：本次连续片段的累计响度。48 kHz 下使用 BS.1770-5 表 1 / 2 的两级 K 加权滤波；400 ms 块，100 ms 步进（75% 重叠），先 −70 LUFS 绝对门限，再相对绝对门限后均值低 10 LU 的门限。
- **Momentary Max**：400 ms 窗口的最大保持值，下方另外显示当前瞬时值，避免把最大值当成当前值。
- **LRA**：3 秒窗、10 Hz 更新，−70 LUFS 绝对门限与 −20 LU 相对门限后，95% 与 10% 分位数之差。前 30 秒显示「样本积累中」；不是 DR、峰均比或音质评分。
- **True Peak**：BS.1770-5 Annex 2 示例四相、共 48 个 FIR 系数，将 48 kHz 分析信号插值至 192 kHz；浮点计算无需整数预衰减。以 dBTP 保持本次最大值，能够检测采样点之间超过 sample peak 的峰值。

积分与 LRA 分布使用固定 **0.01 LU** 直方图，保留每格的真实功率和，不让内存随播放时长增长；门限与分位有约 0.01 LU 的量化误差。仪表只显示有效读数；静音为 −Inf／无有效相关性，不以 0 冒充测量值。

暂停保持结果，不插入静音。跳转、换曲、重新测量、关闭实时页面或切入后台会结束连续片段，下次重新累计。I、LRA、M Max、TP Max 都只描述已测量的片段；从歌曲中间开启时不能称为整曲积分值。若要对整首音乐作比较，应从开头连续测量至结束。当前没有导出交付报告或正式仪表认证。

## 目标标准

| 选择 | I 参考 | TP 上限 | 适用说明 |
|---|---|---|---|
| 不启用 | 无 | 无 | 默认，不把任意音乐判作不合格 |
| EBU R128 | −23 LUFS ±0.2 LU | −1 dBTP | 节目参考；实时片段不能代替完整节目交付判断 |
| ATSC A/85 · 短节目 | −24 LUFS / LKFS，约 ±2 dB | −2 dBTP | 按 2026-07 官方速查表的短节目口径；未实现长节目的对白门控 |
| Spotify Normal 参考 | −14 LUFS | −1 dBTP；I 高于 −14 时采用 −2 dBTP 建议 | 播放归一化／母带建议，不是普遍交付拒收线；界面 ±1 LU 是本软件参考范围 |
| Spotify Quiet 参考 | −19 LUFS | −1 dBTP | 安静档播放参考；界面 ±1 LU 是本软件参考范围 |
| 自定义参考 | 可调 −30 至 −9 LUFS，容差 0.1–3 LU | 可调 −6 至 0 dBTP | 由用户定义，不冒用平台规范 |

绿色／红色仅用于 I 的参考范围和 TP 的上限比较，同时有文字提示。M、S、LRA 没有通用的同一合规门限，保持中性色。更换目标只改变比较，不改变声音或清空读数。各规范仍需按节目类型、合同和测量范围使用。

## 频谱、电平与瀑布

频谱使用 Hann 窗，2048 / 4096 / 8192 点 FFT；横轴为 25 Hz–20 kHz 对数轴，纵轴 dBFS / bin。左右总览取功率平均，避免反相内容在单声道混合后被误当作静音。显示每个对数区间中的最强 bin，按 Hann 相干增益校正，属于离散频点幅度读数，**不是带宽归一化的功率谱密度**。

平滑选项为 0 / 150 / 300 / 600 ms，在功率域指数平均；显示斜率为 0 / 3 / 6 dB/oct，以 1 kHz 为基准。斜率仅改变图形。点按频谱可查看对应频点。更大的 FFT 提升频率分辨率，也增加窗口时长。

L / R 电平数值每 100 ms 给出 sample Peak 与未加权 RMS（dBFS），两侧有刻度。灰色填充为当前块峰值，月白保持线保留 1.5 秒后按约 20 dB/s 回落；TP 与 M Max 则保持本次累计最大值，两种保持含义不同。

瀑布保留 6 / 12 / 24 秒历史，二维以亮度表示能量，三维以高度和亮度表示能量；视角有前上、前低、斜向、侧低、侧高。只重投影同一组时间／频率／能量数据，不重新测量或清空历史。修改 FFT、平均、斜率、输入源会清空图形历史以避免混用坐标，响度累计不受影响。

## 声场与相位

- **Polar Sample**：中侧坐标 X=(R−L)/√2，Y=(L+R)/√2，折叠至上半平面，保留约 400 ms 的衰减点云。
- **Polar Level**：在同一极坐标的 48 个方向区间累计当前数据块的均方能量，以射线显示方向 RMS。
- **Lissajous**：明确使用 X=L、Y=R 的原始坐标；完全同相单声道为 +45° 斜线，完全反相为 −45° 斜线，正交正弦可形成圆／椭圆。

右侧为去直流后的声道相关性 −1 至 +1。**坐标旋转会改变直线方向**：Insight 的旋转示波图中，单声道为竖线、反相为横线；不能把该结论直接套到原始 X=L / Y=R 坐标。原始图的横线／竖线还可能来自某侧声道没有信号。圆形也不能单独证明音乐「声场宽广」，需结合信号与相关性判断。

## 资源与验证边界

每个数据块在工作线程处理，主线程只接收不可变快照；UI 最多 10 Hz 更新，无常驻实时 FFT。瀑布最多 240 列、每列 128 个频点，声场最多 4 组 ×384 点，直方图固定长度。离开页面或生命周期停止时取消读取，并在停止原生解码器时排空管道，避免管道写入线程与 stop 相互等待。

新增测试覆盖 K 加权标定、动态节目门限／LRA、静音与非有限数据、反相频谱、M/S 选择、峰值保持、过采样峰值、历史上限、五格式浮点 PCM、播放位置读取、原生停止／描述符释放和仪表交互。独立参考脚本为 [tools/realtime_reference.py](../tools/realtime_reference.py)：用相同合成信号调用 FFmpeg ebur128，不将 FFmpeg 打包进应用。参考值与测试结果见 [验证记录](validation/android-0.9.0.json)。这些回归不等于通过 EBU 全套测试信号认证；真机耗电、发热、厂商解码差异及长时间延迟仍待验证。

## 研究依据与可核对证据

原生模拟器画面：[响度](screenshots/realtime-loudness-v9.png)、[FFT](screenshots/realtime-spectrum-v9.png)、[三维瀑布](screenshots/realtime-waterfall-v9.png)、[极坐标](screenshots/realtime-field-v9.png)、[李萨如](screenshots/realtime-lissajous-v9.png)。素材为原创合成的立体声试音，画面不是桌面插件截图或模拟数据界面。另已检查 [1.3 倍字体](screenshots/realtime-large-font-v9.png) 与 [横屏布局](screenshots/realtime-landscape-v9.png)，后两张处于暂停／待测状态，仅作为排版证据。

以下为官方规范与公开手册的功能研究，没有安装、破解或反编译 Insight 2，也未使用其图形资源或私有代码。

1. [ITU-R BS.1770-5](https://www.itu.int/rec/R-REC-BS.1770-5-202311-I)：Annex 1 的 K 加权、−0.691 标定和门限积分；Annex 2 的 4× 插值与 FIR 系数。用于 `LoudnessMeter.kt`。
2. [EBU Tech 3342（2023）](https://tech.ebu.ch/docs/tech/tech3342.pdf)：§3.1 定义 3 秒窗、至少 10 Hz、−20 LU 相对门限与 10% / 95% 分位。用于 LRA，未用 DR 代替。
3. [EBU R128（2023）](https://tech.ebu.ch/docs/r/r128.pdf)：−23 LUFS 目标、测量容差、最大真峰值。用于 EBU 比较预设。
4. [ATSC A/85:2026-07 官方速查表](https://www.atsc.org/wp-content/uploads/2026/07/A85-2026-07-Annex-M.pdf)：Table M.1 与脚注 31–33 区分长节目对白与短节目全混音，给出 −24 LKFS 与 −2 dBTP。界面据此限定为「短节目」。
5. [Spotify 官方响度归一化说明](https://support.spotify.com/au/artists/article/loudness-normalization/)：Normal −14、Quiet −19 LUFS 与真峰值母带建议，说明这是播放归一化而非统一拒收门槛。
6. [Insight 2 Loudness 手册](https://s3.amazonaws.com/izotopedownloads/docs/insight200/en/loudness/index.html)：M / S / I、目标比较与最大值显示，作为功能组织参考。
7. [Insight 2 Spectrum 手册](https://s3.amazonaws.com/izotopedownloads/docs/insight200/en/spectrum/index.html)：平均时间、窗口大小与时频分辨率取舍。
8. [Insight 2 Spectrogram 手册](https://s3.amazonaws.com/izotopedownloads/docs/insight200/en/spectrogram/index.html)：二维以颜色、三维以高度表达能量；相机只控制视角，输入来自真实来源。
9. [Insight 2 Sound Field 手册](https://s3.amazonaws.com/izotopedownloads/docs/insight200/en/sound-field/index.html)：Polar Sample、Polar Level、Lissajous、相关性和坐标方向的解释。
