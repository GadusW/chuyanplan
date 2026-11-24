package com.stillune.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stillune.app.health.HearingHistory
import com.stillune.app.health.ListeningDay
import java.time.LocalDate

private val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
private val calendarCellShape = SquircleShape(7.dp)

@Composable
internal fun ListeningCalendar(history: HearingHistory, continuousMs: Long) {
    val days = history.days
    val today = days.last().date
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = days.firstOrNull { it.date.toEpochDay() == selectedEpochDay } ?: days.last()

    Box(Modifier.fillMaxWidth().glassPanel(22.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("聆听日历", Modifier.weight(1f), color = InkAccent, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("最近 ${days.size} 天", color = InkMuted, fontSize = 12.sp)
            }
            ListeningCalendarGrid(days, selected.date) { date ->
                selectedEpochDay = date.takeUnless { it == today }?.toEpochDay()
            }
            CalendarLegend(Modifier.align(Alignment.End).padding(top = 8.dp, end = 4.dp))
            HorizontalDivider(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), color = InkMuted.copy(alpha = .17f))
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val dateLabel = if (selected.date == today) "今日聆听" else selected.date.calendarLabel(today)
                Text("$dateLabel · ${calendarDuration(selected.listenedMs)}", color = InkAccent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("耳机 / 蓝牙 ${calendarDuration(selected.headphoneMs)}", color = InkMuted, fontSize = 12.sp)
                Text("超过当时提醒档位 ${calendarDuration(selected.abovePreferenceMs)}", color = InkMuted, fontSize = 12.sp)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("滚动 7 日 ${hearingDuration(history.rollingWeekMs)}", Modifier.weight(1f), color = InkBlue, fontSize = 13.sp)
        Text("本段累计 ${hearingDuration(continuousMs)}", Modifier.weight(1f), color = InkBlue, fontSize = 13.sp)
    }
    Text("暂停满 5 分钟后重新计段", Modifier.padding(top = 4.dp), color = InkMuted, fontSize = 12.sp)
}

@Composable
private fun ListeningCalendarGrid(days: List<ListeningDay>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = days.last().date
    val leadingSlots = days.first().date.dayOfWeek.value - 1
    val rows = (leadingSlots + days.size + 6) / 7

    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp).clearAndSetSemantics {}) {
        weekdays.forEach { weekday ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(weekday, color = InkMuted, fontSize = 11.sp)
            }
        }
    }
    Column(Modifier.selectableGroup()) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = days.getOrNull(row * 7 + column - leadingSlots)
                    val slot = Modifier.weight(1f).heightIn(min = 48.dp)
                    if (day == null) {
                        Box(slot)
                    } else {
                        CalendarDay(day, today, day.date == selected, slot) { onSelect(day.date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(day: ListeningDay, today: LocalDate, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val level = calendarLevel(day.listenedMs)
    val date = day.date
    val timeLabel = if (day.listenedMs > 0) calendarDuration(day.listenedMs) else "无聆听记录"
    val dayLabel = if (date == today) "今天，${date.calendarLabel(today)}" else date.calendarLabel(today)

    Box(
        modifier.clip(calendarCellShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "$dayLabel，$timeLabel" }
            .padding(horizontal = 2.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        val outline = if (selected) Modifier.border(1.5.dp, InkAccent, calendarCellShape) else Modifier
        Box(Modifier.fillMaxWidth().then(outline).padding(3.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().background(calendarTint(level), calendarCellShape).padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    Modifier.clearAndSetSemantics {},
                    color = if (level >= 3) Color.White else InkAccent,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (date == today || selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend(modifier: Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "聆听时长从浅到深依次为无记录、不到 30 分钟、30 分钟至不足 1 小时、1 小时至不足 2 小时、2 小时及以上"
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("无记录", Modifier.clearAndSetSemantics {}, color = InkMuted, fontSize = 11.sp)
        repeat(5) { level -> Box(Modifier.size(12.dp).background(calendarTint(level), SquircleShape(3.dp))) }
        Text("更久", Modifier.clearAndSetSemantics {}, color = InkMuted, fontSize = 11.sp)
    }
}

private fun calendarLevel(durationMs: Long): Int = when {
    durationMs <= 0 -> 0
    durationMs < 30 * 60_000L -> 1
    durationMs < 60 * 60_000L -> 2
    durationMs < 120 * 60_000L -> 3
    else -> 4
}

private fun calendarTint(level: Int): Color = when (level) {
    0 -> lerp(GlassSurfaceColor, InkBlue, .09f)
    1 -> lerp(GlassSurfaceColor, InkBlue, .23f)
    2 -> lerp(GlassSurfaceColor, InkBlue, .39f)
    3 -> lerp(GlassSurfaceColor, InkBlue, .84f)
    else -> InkAccent
}

private fun LocalDate.calendarLabel(today: LocalDate): String {
    val monthDay = "${monthValue}月${dayOfMonth}日"
    return if (year == today.year) monthDay else "${year}年$monthDay"
}

private fun calendarDuration(durationMs: Long): String =
    if (durationMs in 1..999) "不足 1 秒" else hearingDuration(durationMs)
