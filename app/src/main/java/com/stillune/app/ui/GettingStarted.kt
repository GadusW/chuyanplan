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
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("从一首歌开始", color = Moon, fontSize = 26.sp)
            Text("音乐留在手机里，按自己的节奏聆听。", color = Quiet, fontSize = 14.sp)
            GuideSection("导入 → 点击歌曲 → 此刻", "先点“导入音乐”，可以扫描本机、选择文件夹或挑选文件。授权只会在需要时请求。选中歌曲后，底部“此刻”打开完整播放页。")
            Button(onClick = onImport) { Text("去导入音乐") }
            GuideSection("听歌时，按需探索", "极简模式专注封面与歌词，点击封面可切换歌词，再点击可返回。音频分析提供离线画像与可选实时数据；这些读数不代表耳内分贝。体感沉浸里单击显示控制，闲置 10 秒隐藏，连续三击或系统返回退出。")
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
    HorizontalDivider(color = Quiet.copy(alpha = .14f))
    Text(title, color = Moon, fontSize = 18.sp, lineHeight = 28.sp)
    Text(body, color = Quiet, fontSize = 14.sp, lineHeight = 24.sp)
}
