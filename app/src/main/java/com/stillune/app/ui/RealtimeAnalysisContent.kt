package com.stillune.app.ui

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillune.app.analysis.*
import com.stillune.app.playback.PlaybackState
import java.util.Locale
import kotlin.math.*

private val MeterGood = Color(0xFF236348)
private val MeterBad = Color(0xFF9C3F3D)
private fun number(value: Double?) = value?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT,"%.1f",it) } ?: "−Inf"
private fun clock(ms: Long) = String.format(Locale.ROOT,"%d:%02d",ms/60000,ms/1000%60)

@Composable
internal fun AnalysisSwitcher(cache: AnalysisCache?, state: PlaybackState, repository: RealtimeAnalysisRepository,
                              onRequest: () -> Unit, onSeek: (Long) -> Unit,
                              live: Boolean, onLiveChange: (Boolean) -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            listOf("声音画像" to false,"实时分析" to true).forEach { (label,enabled) ->
                Box(Modifier.weight(1f).heightIn(min=48.dp)
                    .background(if (live==enabled) InkAccent.copy(alpha=.08f) else Color.Transparent,CircleShape)
                    .selectable(live==enabled,role=Role.Tab) { onLiveChange(enabled) },contentAlignment=Alignment.Center) {
                    Text(label,color=if (live==enabled) InkAccent else InkMuted,fontSize=13.sp)
                }
            }
        }
        if (live) {
            val flow=remember(repository) { repository.observe() }
            val frame by flow.collectAsStateWithLifecycle(RealtimeFrame())
            val options by repository.options.collectAsStateWithLifecycle()
            RealtimeAnalysisContent(frame,options,repository::configure,repository::reset)
        } else AnalysisContent(cache,state,onRequest,onSeek)
    }
}

/** Operate surface: quiet numeric typography and aligned axes, no decorative animation. */
@Composable
internal fun RealtimeAnalysisContent(frame: RealtimeFrame, options: RealtimeOptions,
                                     onOptions: (RealtimeOptions) -> Unit, onReset: () -> Unit) {
    var targetName by rememberSaveable { mutableStateOf(LoudnessTarget.OFF.name) }
    val target=LoudnessTarget.valueOf(targetName)
    var targetMenu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldMode by rememberSaveable { mutableStateOf("Polar Sample") }
    var camera by rememberSaveable { mutableStateOf("二维") }
    var customLufs by rememberSaveable { mutableFloatStateOf(-16f) }
    var customPeak by rememberSaveable { mutableFloatStateOf(-1f) }
    var customTolerance by rememberSaveable { mutableFloatStateOf(1f) }
    var customDialog by remember { mutableStateOf(false) }
    val desired=if (target==LoudnessTarget.CUSTOM) customLufs.toDouble() else target.lufs
    val tolerance=if (target==LoudnessTarget.CUSTOM) customTolerance.toDouble() else target.tolerance
    val peakLimit=if (target==LoudnessTarget.CUSTOM) customPeak.toDouble() else target.peakLimit(frame.integrated)
    val match=if (desired==null || frame.integrated==null) 0 else if (abs(frame.integrated-desired)<=tolerance) 1 else -1
    val targetColor=when (match) { 1 -> MeterGood; -1 -> MeterBad; else -> InkAccent }

    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("此刻的声音",color=InkAccent,fontSize=18.sp,fontWeight=FontWeight.Medium)
                Text(frame.status,color=if (frame.error) MeterBad else InkMuted,fontSize=12.sp)
            }
            IconButton(onClick={explain=true}) { Icon(Icons.Rounded.Info,"实时分析说明",tint=InkMuted,modifier=Modifier.size(20.dp)) }
            IconButton(onClick=onReset) { Icon(Icons.Rounded.RestartAlt,"重新测量",tint=InkBlue,modifier=Modifier.size(20.dp)) }
        }
        Text("源文件 PCM · 48 kHz / Float · ${when { frame.sourceChannels==1 -> "单声道"; frame.sourceChannels>2 -> "多声道下混立体声"; else -> "立体声" }}",
            color=InkMuted,fontSize=11.sp)
        Text("本次 ${clock(frame.startMs)} 起 · 已测 ${clock(frame.measuredMs)} · 跳转会重新累计",color=InkMuted,fontSize=11.sp)
        MeterHeader("响度", "Loudness")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val largeType=LocalDensity.current.fontScale>1.2f || maxWidth<290.dp
            if (largeType) Column {
                LoudnessReadout("Short Term","3 秒",if (frame.measuredMs<3000) "—" else number(frame.shortTerm),InkAccent,Modifier.fillMaxWidth(),true)
                LoudnessReadout("Integrated","本次片段",if (frame.measuredMs<400) "—" else number(frame.integrated),targetColor,Modifier.fillMaxWidth(),true)
                LoudnessReadout("Momentary","Max · 400 ms",if (frame.measuredMs<400) "—" else number(frame.momentaryMax),InkAccent,Modifier.fillMaxWidth(),true)
            } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                LoudnessReadout("Short Term","3 秒",if (frame.measuredMs<3000) "—" else number(frame.shortTerm),InkAccent,Modifier.weight(1f))
                LoudnessReadout("Integrated","本次片段",if (frame.measuredMs<400) "—" else number(frame.integrated),targetColor,Modifier.weight(1f))
                LoudnessReadout("Momentary","Max · 400 ms",if (frame.measuredMs<400) "—" else number(frame.momentaryMax),InkAccent,Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("LRA  ${if (frame.measuredMs<3000) "—" else frame.lra?.let(::number) ?: "—"} LU",color=InkMuted,fontSize=12.sp)
            Text("True Peak  ${number(frame.truePeak)} dBTP",color=when { peakLimit==null || frame.truePeak==null -> InkBlue
                frame.truePeak>peakLimit -> MeterBad; else -> MeterGood },fontSize=12.sp)
        }
        Text("瞬时当前 ${if (frame.measuredMs<400) "—" else number(frame.momentary)} LUFS${if (frame.measuredMs<30000) " · LRA 样本积累中" else ""}",color=InkMuted,fontSize=11.sp)
        Box {
            TextButton(onClick={targetMenu=true},contentPadding=PaddingValues(0.dp)) {
                Text(target.label,color=InkBlue,fontSize=12.sp)
                Icon(Icons.Rounded.ExpandMore,null,Modifier.size(18.dp),tint=InkBlue)
            }
            DropdownMenu(expanded=targetMenu,onDismissRequest={targetMenu=false}) {
                LoudnessTarget.entries.forEach { preset -> DropdownMenuItem(text={Text(preset.label)},onClick={
                    targetName=preset.name; targetMenu=false; if (preset==LoudnessTarget.CUSTOM) customDialog=true
                }) }
            }
        }
        if (desired!=null) {
            Text("I ${number(desired)} ±${number(tolerance)} LU · TP ≤${number(peakLimit)} dBTP",color=InkMuted,fontSize=11.sp)
            Text(when (match) { 1 -> "本次片段响度在参考范围内"; -1 -> "本次片段响度偏离参考范围"; else -> "等待响度窗口" },
                color=targetColor,fontSize=11.sp)
            Text("仅 I / TP 按目标着色 · 非整曲交付认证",color=InkMuted,fontSize=11.sp)
            if (target==LoudnessTarget.CUSTOM) TextButton(onClick={customDialog=true}) { Text("编辑参考值") }
        }
        MeterHeader("电平","Levels")
        LevelsChart(frame,Modifier.fillMaxWidth().height(220.dp))
        MeterHeader("频谱","Spectrum",{settings=true},{full="频谱"})
        Text("${options.fftSize} 点 · Hann · 平均 ${options.averageMs} ms · ${options.slopeDbOct} dB/oct",color=InkMuted,fontSize=11.sp)
        SpectrumChart(frame,Modifier.fillMaxWidth().height(205.dp))
        MeterHeader("频谱瀑布","Spectrogram",{settings=true},{full="频谱瀑布"})
        SourceSelector(options,onOptions)
        WaterfallChart(frame,camera,options,Modifier.fillMaxWidth().height(225.dp))
        CameraSelector(camera) {camera=it}
        MeterHeader("立体声声场","Sound Field",onFull={full="声场"})
        SoundFieldChart(frame,fieldMode,Modifier.fillMaxWidth().height(205.dp))
        FieldSelector(fieldMode) {fieldMode=it}
        Text("相关 ${frame.correlation?.let { String.format(Locale.ROOT,"%+.2f",it) } ?: "—"} · ${when {
            frame.correlation==null -> "等待有效双声道信号"; frame.correlation<-.2 -> "反相成分较多，合成单声道可能抵消"
            frame.correlation>.95 -> "左右较相似，声像集中"; else -> "左右存在差异，声像有所展开" }}",color=InkMuted,fontSize=11.sp,lineHeight=18.sp)
        Text("离开此页或应用进入后台即停止计算；下次进入重新累计。",color=InkMuted,fontSize=11.sp,lineHeight=18.sp,modifier=Modifier.padding(vertical=12.dp))
    }
    if (settings) SpectrumSettings(options,onOptions) {settings=false}
    if (customDialog) AlertDialog(onDismissRequest={customDialog=false},title={Text("自定义参考")},text={Column {
        Text("整体响度 ${number(customLufs.toDouble())} LUFS")
        Slider(customLufs,{customLufs=(it*10).roundToInt()/10f},valueRange=-30f..-9f)
        Text("容差 ±${number(customTolerance.toDouble())} LU")
        Slider(customTolerance,{customTolerance=(it*10).roundToInt()/10f},valueRange=.1f..3f)
        Text("真峰值上限 ${number(customPeak.toDouble())} dBTP")
        Slider(customPeak,{customPeak=(it*10).roundToInt()/10f},valueRange=-6f..0f)
    }},confirmButton={TextButton(onClick={customDialog=false}) {Text("完成")}})
    if (explain) AlertDialog(onDismissRequest={explain=false},title={Text("实时分析怎样测量")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("独立解码当前源文件，以 48 kHz 浮点双声道 PCM 分析，不录麦克风、不改变音量。读数跟随播放位置，通常有数百毫秒偏差；不代表系统混音、蓝牙编码或 DAC 之后的信号。")
            Text("LUFS：BS.1770 K 加权。M 为 400 ms，S 为 3 秒；I 使用 400 ms / 75% 重叠，−70 LUFS 绝对门限和 −10 LU 相对门限。Momentary 主数值为 Max，当前值另列。")
            Text("LRA：3 秒窗，10 Hz 更新，−70 LUFS / −20 LU 门限后取 95% 与 10% 分位差；短片段仅作观察。真峰值：4× FIR 插值至 192 kHz，累计最大 dBTP。")
            Text("I、LRA、Max 与真峰值只属于本次连续片段；暂停保持，跳转、换曲、重新测量、离页或切后台会重新累计。不是整曲扫描结果，也未经完整标准认证。")
            Text("${target.label}：${target.note}")
            Text("总览频谱取左右能量平均，避免反相抵消；L / R / M / S 只改变频谱和瀑布的数据源。多声道文件目前下混为立体声，不提供独立环绕声测量。")
            Text("Polar 为旋转后的中侧坐标，居中竖线接近单声道；Lissajous 使用原始 X=L、Y=R，单声道是 45° 斜线，反相是反方向斜线。横线或竖线也可能是单侧声道，不能单独据此判定反相。")
        }
    },confirmButton={TextButton(onClick={explain=false}) {Text("知道了")}})
    if (full!=null) Dialog(onDismissRequest={full=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler {full=null}
        Column(Modifier.fillMaxSize().background(GlassGround).safeDrawingPadding().padding(horizontal=20.dp,vertical=8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(full!!,Modifier.weight(1f),color=InkAccent,fontSize=20.sp)
                if (full!="声场") IconButton(onClick={settings=true}) {Icon(Icons.Rounded.Tune,"全屏图表设置",tint=InkMuted)}
                IconButton(onClick={full=null}) {Icon(Icons.Rounded.Close,"关闭放大图表",tint=InkAccent)}
            }
            when (full) {
                "频谱" -> { SourceSelector(options,onOptions); SpectrumChart(frame,Modifier.weight(1f).fillMaxWidth()) }
                "频谱瀑布" -> { SourceSelector(options,onOptions); WaterfallChart(frame,camera,options,Modifier.weight(1f).fillMaxWidth()); CameraSelector(camera) {camera=it} }
                else -> { SoundFieldChart(frame,fieldMode,Modifier.weight(1f).fillMaxWidth()); FieldSelector(fieldMode) {fieldMode=it} }
            }
            Text(frame.status,color=InkMuted,fontSize=12.sp,modifier=Modifier.padding(vertical=12.dp))
        }
    }
}

@Composable
private fun LoudnessReadout(label: String, subtitle: String, value: String, color: Color, modifier: Modifier, horizontal: Boolean=false) {
    if (horizontal) Row(modifier.heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label,color=InkMuted,fontSize=12.sp)
            Text(subtitle,color=InkMuted,fontSize=11.sp)
        }
        Text(value,color=color,fontSize=25.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
        Text(" LUFS",color=InkMuted,fontSize=10.sp)
    } else Column(modifier.padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,color=InkMuted,fontSize=11.sp,maxLines=1)
        Text(subtitle,color=InkMuted,fontSize=10.sp)
        Text(value,color=color,fontSize=25.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,maxLines=1)
        Text("LUFS",color=InkMuted,fontSize=10.sp)
    }
}

@Composable
private fun MeterHeader(title: String, english: String, onSettings: (() -> Unit)?=null, onFull: (() -> Unit)?=null) {
    HorizontalDivider(Modifier.padding(top=16.dp),color=InkMuted.copy(alpha=.16f))
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=InkAccent,fontSize=15.sp,fontWeight=FontWeight.Medium)
        Text(english,Modifier.weight(1f).padding(start=10.dp),color=InkMuted,fontSize=11.sp)
        onSettings?.let { IconButton(onClick=it) {Icon(Icons.Rounded.Tune,"$title 设置",tint=InkMuted,modifier=Modifier.size(19.dp))} }
        onFull?.let { IconButton(onClick=it) {Icon(Icons.Rounded.OpenInFull,"放大$title",tint=InkMuted,modifier=Modifier.size(18.dp))} }
    }
}

@Composable
private fun SourceSelector(options: RealtimeOptions, onOptions: (RealtimeOptions) -> Unit) {
    var menu by remember {mutableStateOf(false)}
    Box {
        TextButton(onClick={menu=true},contentPadding=PaddingValues(0.dp)) {
            Text("输入源 · ${options.source.label}",fontSize=12.sp,color=InkBlue)
            Icon(Icons.Rounded.ExpandMore,null,Modifier.size(18.dp),tint=InkBlue)
        }
        DropdownMenu(menu,{menu=false}) { SpectrumSource.entries.forEach { source ->
            DropdownMenuItem(text={Text(source.label)},onClick={onOptions(options.copy(source=source)); menu=false})
        } }
    }
}

@Composable
private fun CameraSelector(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("二维","前上","前低","斜向","侧低","侧高").forEach { mode ->
            TextButton(onClick={onSelect(mode)},modifier=Modifier.widthIn(min=54.dp),colors=ButtonDefaults.textButtonColors(
                contentColor=if (selected==mode) InkAccent else InkMuted)) {Text(mode,fontSize=12.sp)}
        }
    }
}

@Composable
private fun FieldSelector(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("Polar Sample","Polar Level","Lissajous").forEach { mode ->
            TextButton(onClick={onSelect(mode)},colors=ButtonDefaults.textButtonColors(contentColor=if (selected==mode) InkAccent else InkMuted)) {
                Text(mode,fontSize=11.sp)
            }
        }
    }
}

@Composable
private fun SpectrumSettings(options: RealtimeOptions, onOptions: (RealtimeOptions) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text("频谱与瀑布设置")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("FFT 尺寸 · 大窗口更细，时间响应更慢",fontSize=12.sp)
        SettingChoices(listOf(2048,4096,8192),options.fftSize,{onOptions(options.copy(fftSize=it))}) {"$it"}
        Text("能量平均 / 平滑",fontSize=12.sp)
        SettingChoices(listOf(0,150,300,600),options.averageMs,{onOptions(options.copy(averageMs=it))}) {"$it ms"}
        Text("频谱显示斜率 · 不改变声音",fontSize=12.sp)
        SettingChoices(listOf(0,3,6),options.slopeDbOct,{onOptions(options.copy(slopeDbOct=it))}) {"$it dB/oct"}
        Text("瀑布历史长度",fontSize=12.sp)
        SettingChoices(listOf(6,12,24),options.historySeconds,{onOptions(options.copy(historySeconds=it))}) {"$it 秒"}
        Text("调整 FFT、数据源、平均或斜率会清空图形历史，响度累计不受影响。",fontSize=11.sp,color=InkMuted)
    }},confirmButton={TextButton(onClick=onClose) {Text("完成")}})
}

@Composable
private fun SettingChoices(values: List<Int>, selected: Int, onSelect: (Int) -> Unit, label: (Int) -> String) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { values.forEach { value ->
        TextButton(onClick={onSelect(value)},colors=ButtonDefaults.textButtonColors(contentColor=if (value==selected) InkAccent else InkMuted)) {
            Text(label(value),fontSize=11.sp)
        }
    } }
}

private fun DrawScope.axis(text: String, x: Float, y: Float, color: Color=InkMuted, align: Paint.Align=Paint.Align.LEFT) {
    drawContext.canvas.nativeCanvas.drawText(text,x,y,Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color.toArgb(); textSize=10.sp.toPx(); textAlign=align
    })
}

@Composable
private fun LevelsChart(frame: RealtimeFrame, modifier: Modifier) {
    val levels=frame.levels
    Column(modifier.semantics { contentDescription="左右声道电平：左峰值 ${number(levels.peakL)}，有效值 ${number(levels.rmsL)}；右峰值 ${number(levels.peakR)}，有效值 ${number(levels.rmsR)} dBFS" }) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf("L" to (levels.peakL to levels.rmsL),"R" to (levels.peakR to levels.rmsR)).forEach { (channel,value) ->
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("$channel   Peak ${number(value.first)}",color=InkAccent,fontSize=12.sp,fontFamily=FontFamily.Monospace)
                    Text("RMS ${number(value.second)} dBFS",color=InkMuted,fontSize=11.sp,fontFamily=FontFamily.Monospace)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val top=12.dp.toPx(); val bottom=size.height-24.dp.toPx(); val h=bottom-top
            val left=size.width*.27f; val right=size.width*.61f; val width=size.width*.12f
            fun y(db: Double?) = top+(1-((db ?: -60.0).coerceIn(-60.0,0.0)+60)/60).toFloat()*h
            listOf(0,-10,-20,-40,-60).forEach { db ->
                val yy=y(db.toDouble())
                axis(if (db == -60) "−Inf" else "$db",left-12.dp.toPx(),yy+3.dp.toPx(),align=Paint.Align.RIGHT)
                axis(if (db == -60) "−Inf" else "$db",right+width+12.dp.toPx(),yy+3.dp.toPx())
                drawLine(InkMuted.copy(alpha=.12f),Offset(left,yy),Offset(right+width,yy),1f)
            }
            listOf(Triple(left,levels.peakL,levels.holdL),Triple(right,levels.peakR,levels.holdR)).forEach { (x,peak,hold) ->
                drawRect(InkMuted.copy(alpha=.08f),Offset(x,top),Size(width,h))
                drawRect(InkMuted.copy(alpha=.6f),Offset(x,y(peak)),Size(width,(bottom-y(peak)).coerceAtLeast(0f)))
                if (hold!=null) drawLine(if (hold>=0) MeterBad else InkAccent,Offset(x,y(hold)),Offset(x+width,y(hold)),2.dp.toPx())
            }
            axis("L",left+width/2,size.height-5.dp.toPx(),align=Paint.Align.CENTER)
            axis("R",right+width/2,size.height-5.dp.toPx(),align=Paint.Align.CENTER)
        }
    }
}

@Composable
private fun SpectrumChart(frame: RealtimeFrame, modifier: Modifier) {
    var cursor by remember {mutableFloatStateOf(-1f)}
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics {contentDescription="实时 FFT 对数频谱，25 Hz 至 20 kHz，点按查看频点"}
            .pointerInput(Unit) {detectTapGestures {cursor=((it.x-36.dp.toPx())/(size.width-48.dp.toPx())).coerceIn(0f,1f)}}) {
            val x0=36.dp.toPx(); val w=size.width-x0-12.dp.toPx(); val top=10.dp.toPx(); val h=size.height-top-25.dp.toPx()
            listOf(50,100,200,500,1000,2000,5000,10000,20000).forEach { f ->
                val x=x0+(ln(f/25.0)/ln(800.0)).toFloat()*w
                drawLine(InkMuted.copy(alpha=.13f),Offset(x,top),Offset(x,top+h),1f)
                if (size.width>450.dp.toPx() || f in listOf(50,200,1000,5000,20000))
                    axis(if (f>=1000) "${f/1000}k" else "$f",x,top+h+17.dp.toPx(),align=Paint.Align.CENTER)
            }
            listOf(0,-24,-48,-72,-96).forEach { db ->
                val y=top+(12-db)/112f*h
                drawLine(InkMuted.copy(alpha=.11f),Offset(x0,y),Offset(x0+w,y),1f)
                axis("$db",x0-6.dp.toPx(),y+3.dp.toPx(),align=Paint.Align.RIGHT)
            }
            if (frame.spectrum.isNotEmpty()) {
                val path=Path()
                frame.spectrum.forEachIndexed { index,db ->
                    val x=x0+index.toFloat()/(frame.spectrum.size-1)*w
                    val y=top+((12-db)/112f).coerceIn(0f,1f)*h
                    if (index==0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                drawPath(path,InkBlue,style=Stroke(1.6.dp.toPx()))
            }
            if (cursor>=0) drawLine(InkAccent.copy(alpha=.65f),Offset(x0+cursor*w,top),Offset(x0+cursor*w,top+h),1.dp.toPx())
        }
        if (cursor>=0 && frame.spectrum.isNotEmpty()) {
            val bin=(cursor*(frame.spectrum.size-1)).roundToInt()
            Text("${RealtimeProcessor.frequency(bin).roundToInt()} Hz   ${number(frame.spectrum[bin].toDouble())} dBFS / bin",color=InkBlue,fontSize=11.sp)
        } else Text("幅度 dBFS / bin · 斜率为显示补偿",color=InkMuted,fontSize=10.sp)
    }
}

@Composable
private fun WaterfallChart(frame: RealtimeFrame, camera: String, options: RealtimeOptions, modifier: Modifier) {
    Canvas(modifier.semantics {contentDescription="实时频谱瀑布，${options.source.label}，${options.historySeconds} 秒历史，$camera 视角"}) {
        val left=8.dp.toPx(); val top=12.dp.toPx(); val w=size.width-55.dp.toPx(); val h=size.height-35.dp.toPx()
        val rows=frame.history
        if (camera=="二维") {
            drawRect(GlassSurfaceColor.copy(alpha=.35f),Offset(left,top),Size(w,h))
            val cellW=w/(options.historySeconds*10); val cellH=h/64
            rows.forEachIndexed { time,bands ->
                val x=left+w-(rows.size-time)*cellW
                for (b in 0 until 64) {
                    val strength=((bands.getOrElse(b*2){-100f}+90)/84).coerceIn(0f,1f)
                    if (strength>.02f) drawRect(lerp(GlassSurfaceColor,lerp(InkBlue,InkAccent,strength),strength),
                        Offset(x,top+h-(b+1)*cellH),Size(cellW+1,cellH+1))
                }
            }
            listOf(100,1000,10000).forEach { f ->
                val y=top+h-(ln(f/25.0)/ln(800.0)).toFloat()*h
                drawLine(InkMuted.copy(alpha=.12f),Offset(left,y),Offset(left+w,y),1f)
                axis(if (f>=1000) "${f/1000}k" else "$f",left+w+6.dp.toPx(),y+3.dp.toPx())
            }
        } else {
            // Orthographic camera presets reproject the same immutable time/frequency/energy samples.
            val side=camera.startsWith("侧"); val low=camera.endsWith("低")
            val skew=when (camera) {"斜向" -> .31f; "侧低","侧高" -> .48f; else -> .1f}
            val depth=if (low) .18f else .48f
            val plotHeight=min(h,w*.88f)
            val plotTop=top+(h-plotHeight)/2
            fun project(t: Float,f: Float,e: Float): Offset {
                val xx=if (side) f else t
                val zz=if (side) t else f
                return Offset(left+w*(.04f+xx*(1-skew)*.92f+zz*skew*.9f),plotTop+plotHeight*(.91f-zz*depth)-e*plotHeight*(if (low) .48f else .33f))
            }
            for (f in listOf(.2f,.55f,.9f)) drawLine(InkMuted.copy(alpha=.15f),project(0f,f,0f),project(1f,f,0f),1f)
            val step=max(1,rows.size/40)
            for (time in rows.indices step step) {
                val t=1f-(rows.size-1-time).toFloat()/(options.historySeconds*10)
                val path=Path(); var energy=0f
                for (b in 0 until 64) {
                    val s=((rows[time][b*2]+90)/84).coerceIn(0f,1f); energy+=s
                    val p=project(t,b/63f,s)
                    if (b==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
                }
                drawPath(path,lerp(InkBlue,InkAccent,(energy/64).coerceIn(0f,1f)).copy(alpha=.35f+.65f*t),style=Stroke(1.dp.toPx()))
            }
            listOf(100,1000,10000).forEach { f ->
                val p=project(1f,(ln(f/25.0)/ln(800.0)).toFloat(),0f)
                axis(if (f>=1000) "${f/1000}k" else "$f",p.x+5.dp.toPx(),p.y)
            }
        }
        axis("−${options.historySeconds} s",left,size.height-4.dp.toPx())
        axis("现在",left+w,size.height-4.dp.toPx(),align=Paint.Align.RIGHT)
    }
}

@Composable
private fun SoundFieldChart(frame: RealtimeFrame, mode: String, modifier: Modifier) {
    Canvas(modifier.semantics {contentDescription="实时声场 $mode，相关性 ${frame.correlation?.let {String.format(Locale.ROOT,"%.2f",it)} ?: "无信号"}"}) {
        val cx=size.width*.46f; val availableBottom=size.height-25.dp.toPx(); val availableTop=16.dp.toPx()
        val radius=min(size.width*.39f,(availableBottom-availableTop)/(if (mode=="Lissajous") 2 else 1))
        val cy=(availableTop+availableBottom)/2+if (mode=="Lissajous") 0f else radius/2
        val bottom=if (mode=="Lissajous") cy+radius else cy
        val grid=InkMuted.copy(alpha=.18f)
        if (mode=="Lissajous") {
            drawRect(grid,Offset(cx-radius,cy-radius),Size(radius*2,radius*2),style=Stroke(1f))
            drawLine(grid,Offset(cx-radius,cy),Offset(cx+radius,cy),1f)
            drawLine(grid,Offset(cx,cy-radius),Offset(cx,cy+radius),1f)
            drawLine(grid,Offset(cx-radius,cy+radius),Offset(cx+radius,cy-radius),1f)
        } else {
            listOf(1f,.66f,.33f).forEach { scale ->
                drawArc(grid,180f,180f,false,Offset(cx-radius*scale,cy-radius*scale),Size(radius*scale*2,radius*scale*2),style=Stroke(1f))
            }
            listOf(-1f,0f,1f).forEach { side ->drawLine(grid,Offset(cx,cy),Offset(cx+side*radius*.707f,cy-radius*.707f),1f)}
            drawLine(grid,Offset(cx-radius,cy),Offset(cx+radius,cy),1f)
        }
        if (mode=="Polar Level") {
            frame.polar.forEachIndexed { i,level ->
                val angle=(i+.5f)/48*PI
                val r=(level*1.6f).coerceIn(0f,1f)*radius
                drawLine(InkBlue.copy(alpha=.75f),Offset(cx,cy),Offset(cx+cos(angle).toFloat()*r,cy-sin(angle).toFloat()*r),2.dp.toPx())
            }
        } else {
            for (p in 0 until frame.scatter.size/2) {
                val l=frame.scatter[2*p]; val r=frame.scatter[2*p+1]
                val point=if (mode=="Lissajous") Offset(cx+l.coerceIn(-1f,1f)*radius,cy-r.coerceIn(-1f,1f)*radius) else {
                    var mid=(l+r)*.7071f; var side=(r-l)*.7071f
                    if (mid<0) {mid=-mid;side=-side}
                    val scale=max(1f,sqrt(mid*mid+side*side))
                    Offset(cx+side/scale*radius,cy-mid/scale*radius)
                }
                val age=p.toFloat()/(frame.scatter.size/2).coerceAtLeast(1)
                drawCircle(if (abs(l)>=1 || abs(r)>=1) MeterBad else InkBlue.copy(alpha=.1f+.55f*age),1.1.dp.toPx(),point)
            }
        }
        axis(if (mode=="Lissajous") "X = L" else "L",cx-radius,bottom+18.dp.toPx())
        axis(if (mode=="Lissajous") "Y = R" else "R",cx+radius,bottom+18.dp.toPx(),align=Paint.Align.RIGHT)
        val x=size.width-28.dp.toPx(); val y0=cy-radius; val barH=(bottom-y0).coerceAtLeast(1f)
        drawLine(grid,Offset(x,y0),Offset(x,bottom),3.dp.toPx())
        listOf(1,0,-1).forEach { n ->axis(if (n>0) "+1" else "$n",x+7.dp.toPx(),y0+(1-n)/2f*barH+4.dp.toPx())}
        frame.correlation?.let { c ->
            val yy=y0+(1-c).toFloat()/2*barH
            drawCircle(if (c<0) MeterBad else InkAccent,3.dp.toPx(),Offset(x,yy))
        }
    }
}
