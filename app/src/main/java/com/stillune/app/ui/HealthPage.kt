package com.stillune.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillune.app.health.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }

@Composable
internal fun HealthPage(model: LibraryViewModel, onRead: (String) -> Unit) {
    val settings by model.hearing.settings.collectAsStateWithLifecycle()
    val live by model.hearing.live.collectAsStateWithLifecycle()
    val environment by model.hearing.environment.collectAsStateWithLifecycle()
    val records by model.hearingRecords.collectAsStateWithLifecycle()
    val playback by model.playback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micConsent by rememberSaveable { mutableStateOf(false) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    fun startMonitor() {
        try { ContextCompat.startForegroundService(context, Intent(context, EnvironmentMonitorService::class.java)) }
        catch (_: Exception) { model.hearing.environmentChanged(EnvironmentReading(status = "系统未允许启动，请回到此页面重试")) }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMonitor() else micDenied = true
    }
    HearingContent(settings, live, records, environment, playback.playing, playback.output, notifications,
        onUpdate = { model.hearing.update(it) }, onLower = model::lowerVolume,
        onEnvironment = {
            if (environment.running) context.stopService(Intent(context, EnvironmentMonitorService::class.java)) else micConsent = true
        }, onProfile = { profileOpen = true }, onRead = { id -> model.pauseForRest(); onRead(id) },
        onDelete = { deleteOpen = true }, onInfo = { infoOpen = true }, onNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        })
    if (micConsent) AlertDialog(onDismissRequest = { micConsent = false }, title = { Text("开启环境趋势？") },
        text = { Text("将持续使用手机麦克风，在本机比较声音能量变化；离开此页面、锁屏后仍会继续，通知中可随时停止。不保存或上传录音。\n\n尚未进行声学校准，不能判断是否超过 60 dBA，也不测量耳内音量。移动手机、风声和衣物摩擦会影响趋势。") },
        confirmButton = { TextButton(onClick = {
            micConsent = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startMonitor()
            else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text("同意并开启") } }, dismissButton = { TextButton(onClick = { micConsent = false }) { Text("暂不开启") } })
    if (micDenied) AlertDialog(onDismissRequest = { micDenied = false }, title = { Text("麦克风尚未授权") },
        text = { Text("收听记录和休息提醒仍然可用。若要开启环境趋势，可在系统设置中允许麦克风，再回到此处开启。") },
        confirmButton = { TextButton(onClick = {
            micDenied = false
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
        }) { Text("打开系统设置") } }, dismissButton = { TextButton(onClick = { micDenied = false }) { Text("知道了") } })
    if (profileOpen) HeadphoneProfile(settings, onDismiss = { profileOpen = false }, onSave = { name, isolation ->
        model.hearing.update { it.copy(headphoneName = name.trim(), isolation = isolation) }; profileOpen = false
    })
    if (deleteOpen) AlertDialog(onDismissRequest = { deleteOpen = false }, title = { Text("清除护耳收听记录？") },
        text = { Text("将清除本机护耳时长与本段累计。曲库、播放历史和耳机档案会保留。此操作无法撤销。") },
        confirmButton = { TextButton(onClick = { model.hearing.clearRecords(); deleteOpen = false }) { Text("清除记录") } },
        dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("取消") } })
    if (infoOpen) AlertDialog(onDismissRequest = { infoOpen = false }, title = { Text("这些数据能说明什么") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("记录范围仅为栖声开启护耳后的可观测播放时间，系统音量为零或静音时不累计。连接耳机不等于佩戴；其他应用和日常噪声不在记录内。每 5 秒保存，异常结束可能丢失最后一小段。")
            Text("WHO–ITU 个人音频参考为成人 80 dB / 每周 40 小时，儿童 75 dB / 每周 40 小时。85 dBA / 8 小时是 NIOSH 职业暴露参考，不能直接当作本应用的每日安全保证。")
            Text("没有经验证的设备与耳机声学校准，本版不显示入耳 dBA 或剂量百分比。音量档位、音源 LUFS、动态范围都不能单独换算成入耳声压。提醒间隔是行为偏好，短暂休息不清除历史，也不代表听力恢复。")
            Text("环境趋势只比较麦克风信号相对本次基线的变化；没有可靠输入时显示不可用。若有耳鸣、听声不清等持续不适，应停止高声级暴露并寻求专业评估。")
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.who.int/news-room/questions-and-answers/item/deafness-and-hearing-loss-safe-listening".toUri())) } }) { Text("阅读 WHO 安全聆听资料") }
        } }, confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("知道了") } })
}

@Composable
internal fun HearingContent(
    settings: HearingSettings, live: HearingLive, records: List<HearingMinute>, environment: EnvironmentReading,
    playing: Boolean, output: String, notifications: Boolean,
    onUpdate: ((HearingSettings) -> HearingSettings) -> Unit, onLower: () -> Unit, onEnvironment: () -> Unit,
    onProfile: () -> Unit, onRead: (String) -> Unit, onDelete: () -> Unit, onInfo: () -> Unit, onNotifications: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val todayMs = records.filter { it.startMs >= midnight && it.startMs <= now }.sumOf { it.listenedMs }
    val todayHeadphones = records.filter { it.startMs >= midnight && it.startMs <= now }.sumOf { it.headphoneMs }
    val todayAbove = records.filter { it.startMs >= midnight && it.startMs <= now }.sumOf { it.abovePreferenceMs }
    val weekMs = records.filter { it.startMs >= now - 7L * 86_400_000 && it.startMs <= now }.sumOf { it.listenedMs }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("护耳", fontSize = 28.sp, color = Moon)
                    Text("让热爱，听得更长久。", color = Quiet, fontSize = 13.sp)
                }
                Switch(settings.enabled, { onUpdate { old -> old.copy(enabled = it) } }, modifier = Modifier.semanticsLabel("启用护耳"))
            }
        }
        item {
            Text(if (settings.enabled) "已启用 · 在各播放模式与后台持续记录" else "开启后，栖声会记录收听并适时提醒", color = Mist, fontSize = 12.sp)
            Text("只记录本应用可观测的播放，不评定听力健康。", Modifier.padding(top = 6.dp), color = Quiet, fontSize = 12.sp)
        }
        item {
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 20.dp), color = Quiet.copy(alpha = .18f))
            Row(Modifier.fillMaxWidth()) {
                DurationColumn("今日聆听", todayMs, Modifier.weight(1f))
                DurationColumn("滚动 7 日", weekMs, Modifier.weight(1f))
            }
            Text("本段累计 ${hearingDuration(live.continuousMs)} · 暂停满 5 分钟后重新计段", Modifier.padding(top = 14.dp), color = Quiet, fontSize = 12.sp)
            Text("今日连接耳机 / 蓝牙时 ${hearingDuration(todayHeadphones)}\n超过当时提醒档位 ${hearingDuration(todayAbove)}", Modifier.padding(top = 8.dp), color = Quiet, fontSize = 12.sp)
        }
        item {
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val values = days.map { day ->
                    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    records.filter { it.startMs in start until end }.sumOf { it.listenedMs }
                }
                val largest = values.maxOrNull()?.coerceAtLeast(60_000) ?: 60_000L
                days.forEachIndexed { index, day ->
                    Column(Modifier.weight(1f).fillMaxHeight().semanticsLabel("${day.monthValue}月${day.dayOfMonth}日 ${hearingDuration(values[index])}"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.width(12.dp).height(if (values[index] == 0L) 2.dp else (values[index].toFloat() / largest * 56).coerceAtLeast(3f).dp)
                            .background(if (index == 6) Moon else Mist.copy(alpha = .45f), RoundedCornerShape(3.dp)))
                        Text(if (index == 6) "今天" else day.format(DateTimeFormatter.ofPattern("M/d")), Modifier.padding(top = 10.dp), color = Quiet, fontSize = 10.sp)
                    }
                }
            }
            if (records.isEmpty()) Text("还没有记录。从下一次播放，慢慢积累。", Modifier.padding(top = 12.dp), color = Quiet, fontSize = 12.sp)
            if (live.storageError) Text("记录未能写入本机，请检查存储空间；部分时长可能缺失。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (live.unobservedMs > 0) Text("本次存在未观测时段，未将它们当作零暴露。", color = Quiet, fontSize = 12.sp)
        }
        item { HearingSectionTitle("提醒，按你的节奏") }
        item {
            Text("累计播放多久提醒一次", color = Moon, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(20, 40, 60).forEach { minutes ->
                    FilterChip(settings.breakMinutes == minutes, { onUpdate { it.copy(breakMinutes = minutes) } },
                        label = { Text("$minutes 分钟") }, enabled = settings.enabled)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("提醒时自动暂停", color = Moon, fontSize = 15.sp)
                    Text("默认只提醒；开启后也不会自动恢复播放。", color = Quiet, fontSize = 12.sp)
                }
                Switch(settings.pauseOnReminder, { enabled -> onUpdate { it.copy(pauseOnReminder = enabled) } },
                    enabled = settings.enabled, modifier = Modifier.semanticsLabel("提醒时自动暂停"))
            }
        }
        item {
            Text("音量档位提醒 · ${settings.volumePercent}%", color = Moon, fontSize = 15.sp)
            Slider(settings.volumePercent.toFloat(), { value -> onUpdate { it.copy(volumePercent = value.toInt()) } },
                valueRange = 30f..90f, steps = 11, enabled = settings.enabled, modifier = Modifier.semanticsLabel("音量档位提醒阈值"))
            Text("持续超过所选档位 10 秒后提醒，间隔至少 10 分钟。它是个人偏好，不是安全声压阈值。", color = Quiet, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("当前系统音量 ${live.volumeStep} / ${live.volumeMax}", Modifier.weight(1f), color = Mist, fontSize = 13.sp)
                TextButton(onClick = onLower, enabled = live.volumeStep > 0) { Text("降低一档") }
            }
            Text("入耳声压与剂量：尚无经验证的声学校准，不显示估算值。", color = Quiet, fontSize = 12.sp)
        }
        if (!notifications && settings.enabled) item {
            Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("后台提醒尚未开放", color = Moon, fontSize = 15.sp)
                    Text("系统通知已关闭，锁屏或切到其他应用时可能收不到提醒。", color = Quiet, fontSize = 12.sp)
                    TextButton(onClick = onNotifications) { Text("开启通知") }
                }
            }
        }
        item { HearingSectionTitle("耳机与环境") }
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, null, tint = Mist, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(settings.headphoneName.ifBlank { "填写耳机档案" }, color = Moon, fontSize = 16.sp)
                    Text(settings.isolation, color = Quiet, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChevronRight, "编辑耳机档案", tint = Quiet)
            }
            Text("$output\n${if (live.headphonesConnected) "检测到耳机 / 蓝牙音频连接（可能含扬声器），路由与佩戴仍以设备为准。" else "未检测到常见耳机连接；部分 USB / 外接设备无法判断。"}", color = Quiet, fontSize = 12.sp)
            Text(when (settings.isolation) {
                "开放式" -> "开放式耳机隔音较少。嘈杂时优先换个安静的地方，避免用更高音量盖过环境。"
                "主动降噪" -> "合适的佩戴与降噪可减少调高音量的需要；道路上仍需留意周围。"
                else -> "先确认佩戴舒适与环境合适，再选择较低的音量。耳机解析度不代表声压安全。"
            }, Modifier.padding(top = 10.dp), color = Mist, fontSize = 13.sp)
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Quiet.copy(alpha = .15f))
            Text("环境声音趋势", color = Moon, fontSize = 16.sp)
            environment.changeDb?.let { value ->
                Text(String.format(Locale.ROOT, "%+.1f dB", value), Modifier.padding(top = 12.dp), color = Mist, fontSize = 28.sp)
                Text("相对本次基线的输入能量变化 · 非 dBA", color = Quiet, fontSize = 12.sp)
            }
            Text(environment.status, Modifier.padding(top = 8.dp), color = Quiet, fontSize = 12.sp)
            Text("仅比较手机麦克风变化，不测量耳内声音。风声、移动与摩擦都会影响结果。持续上升约 10 dB 时提示留意音量。", Modifier.padding(top = 8.dp), color = Quiet, fontSize = 12.sp)
            OutlinedButton(onClick = onEnvironment, enabled = settings.enabled) {
                Icon(if (environment.running) Icons.Rounded.MicOff else Icons.Rounded.MicNone, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if (environment.running) "停止环境监测" else "开启环境趋势")
            }
        }
        item { HearingSectionTitle("让音乐，换一种方式留下") }
        item { Text("暂停声音，读一段小札。全为栖声原创，不自动播放，也不自动续播。", color = Quiet, fontSize = 13.sp) }
        items(AppreciationLibrary.articles.size) { index ->
            val article = AppreciationLibrary.articles[index]
            Column(Modifier.fillMaxWidth().clickable { onRead(article.id) }.padding(vertical = 12.dp)) {
                Text(article.title, color = Moon, fontSize = 18.sp)
                Text(article.subtitle, Modifier.padding(top = 6.dp), color = Quiet, fontSize = 12.sp)
                Text("${if (playing) "暂停并阅读" else "静默阅读"} · 约 ${article.minutes} 分钟", Modifier.padding(top = 10.dp), color = Mist, fontSize = 12.sp)
            }
        }
        item {
            HorizontalDivider(color = Quiet.copy(alpha = .18f))
            TextButton(onClick = onInfo) { Text("数据范围与资料依据") }
            TextButton(onClick = onDelete, enabled = records.isNotEmpty() || live.continuousMs > 0) { Text("清除护耳收听记录", color = Quiet) }
            Text("数据只保存在本机，自动保留最近 31 天。休息不清除历史，也不代表听力已经恢复。", color = Quiet, fontSize = 12.sp)
        }
    }
}

@Composable private fun DurationColumn(label: String, value: Long, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Quiet, fontSize = 12.sp)
        Text(hearingDuration(value), Modifier.padding(top = 8.dp), color = Moon, fontSize = 24.sp, fontWeight = FontWeight.Light)
    }
}
@Composable private fun HearingSectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 24.dp), color = Quiet.copy(alpha = .18f))
        Text(text, color = Moon, fontSize = 20.sp)
    }
}
internal fun hearingDuration(ms: Long): String = when {
    ms < 60_000 -> "${ms.coerceAtLeast(0) / 1000} 秒"
    ms < 3_600_000 -> "${ms / 60_000} 分钟"
    else -> "${ms / 3_600_000} 小时 ${ms % 3_600_000 / 60_000} 分"
}

@Composable private fun HeadphoneProfile(settings: HearingSettings, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(settings.headphoneName) }
    var isolation by rememberSaveable { mutableStateOf(settings.isolation) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("我的耳机") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("品牌 / 型号（选填）") }, singleLine = true)
            Text("隔音与佩戴形式", color = Quiet)
            listOf("尚未填写", "开放式", "封闭 / 入耳式", "主动降噪").forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable { isolation = choice }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(isolation == choice, null); Text(choice, Modifier.padding(start = 12.dp))
                }
            }
            Text("档案用于给出环境与佩戴建议，不能替代声学校准。本版不会根据价格、解析度或蓝牙名称推算分贝。", color = Quiet, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, isolation) }) { Text("保存档案") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun AppreciationReader(article: AppreciationArticle, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭音乐小札", tint = Moon) }
                    Text("音乐小札", color = Quiet, fontSize = 14.sp)
                }
                LazyColumn(Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(28.dp, 20.dp, 28.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    item {
                        Text(article.title, color = Moon, fontSize = 27.sp, lineHeight = 38.sp)
                        Text("约 ${article.minutes} 分钟 · 栖声原创 · 静默阅读", Modifier.padding(top = 12.dp), color = Mist, fontSize = 12.sp)
                    }
                    items(article.paragraphs.size) { Text(article.paragraphs[it], color = Color(0xFFE0E6EF), fontSize = 17.sp, lineHeight = 30.sp) }
                    item { TextButton(onClick = onClose) { Text("读完了，继续安静一会儿") } }
                }
            }
        }
    }
}
