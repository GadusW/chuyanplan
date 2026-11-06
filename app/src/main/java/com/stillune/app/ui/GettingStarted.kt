package com.stillune.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GettingStartedSheet(
    onClose: () -> Unit, onImport: () -> Unit, onHealth: () -> Unit, onKnowledge: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = GlassSurfaceColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("从一首歌开始", color = InkAccent, fontSize = 26.sp)
            Text("音乐留在手机里，按自己的节奏聆听。", color = InkMuted, fontSize = 14.sp)
            GuideSection("导入 → 点击歌曲 → 此刻", "先点首页右上角的加号，可以扫描本机、选择文件夹或挑选文件。授权只会在需要时请求。选中歌曲后，底部“此刻”打开完整播放页。")
            Button(onClick = onImport) { Text("去导入音乐") }
            GuideSection("听歌时，按需探索", "播放模式按队列展示封面，左滑超过阈值切到下一首，短拖会回弹。点封面翻到歌词，点歌词翻回。长按同步歌词可跳转。音频分析提供离线画像与可选实时数据；这些读数不代表耳内分贝。体感沉浸里单击显示控制，闲置 10 秒隐藏，连续三击或系统返回退出。")
            GuideSection("护耳，随时照看收听习惯", "底部“护耳”可开关收听记录、调节提醒。开启后离开页面仍会工作；环境趋势需另外同意使用麦克风。音量档位不等于安全声压。")
            OutlinedButton(onClick = onHealth) { Text("去设置护耳提醒") }
            GuideSection("休息时，读懂一个小问题", "知识书架自带三分钟文章，打开即可离线阅读。阅读前会暂停音乐，关闭后由你决定何时继续。右上角书签可收藏，字号按钮可放大文字；新知识包从书架右上角导入。")
            OutlinedButton(onClick = onKnowledge) { Text("先读一篇聆听知识") }
            TextButton(onClick = onClose) { Text("我自己看看") }
        }
    }
}

@Composable
private fun GuideSection(title: String, body: String) {
    HorizontalDivider(color = InkMuted.copy(alpha = .14f))
    Text(title, color = InkAccent, fontSize = 18.sp, lineHeight = 28.sp)
    Text(body, color = InkMuted, fontSize = 14.sp, lineHeight = 24.sp)
}
