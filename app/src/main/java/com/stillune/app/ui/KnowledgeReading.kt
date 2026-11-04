package com.stillune.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stillune.app.health.*

data class KnowledgeImportState(val busy: Boolean = false, val message: String? = null)

@Composable
internal fun KnowledgeShelf(
    state: KnowledgeState, importing: KnowledgeImportState,
    onRead: (String) -> Unit, onImport: () -> Unit, onClose: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("全部") }
    var query by rememberSaveable { mutableStateOf("") }
    var importHelp by rememberSaveable { mutableStateOf(false) }
    val articles = state.articles.filter {
        (query.isBlank() || (listOf(it.title) + it.tags).any { text -> text.contains(query, true) }) && when (filter) {
            "未读" -> it.id !in state.read
            "收藏" -> it.id in state.favorites
            else -> true
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.safeDrawingPadding().widthIn(max = 720.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭知识书架", tint = Quiet) }
                    Text("聆听知识", Modifier.weight(1f), color = Moon, fontSize = 18.sp)
                    IconButton(onClick = { importHelp = true }) { Icon(Icons.Rounded.FileDownload, "导入知识更新", tint = Mist) }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("三分钟，听懂一点", color = Moon, fontSize = 27.sp, lineHeight = 38.sp)
                        Text("每篇约 3–5 分钟，一个小问题，一次从容的休息。", Modifier.padding(top = 12.dp), color = Quiet, fontSize = 14.sp)
                        Text(if (state.version == 0L) "内置内容 · ${state.articles.size} 篇"
                            else "知识包第 ${state.version} 版 · ${state.articles.size} 篇",
                            Modifier.padding(top = 10.dp), color = Mist, fontSize = 12.sp)
                        if (state.ready && query.isBlank() && filter == "全部") {
                            val next = state.articles.firstOrNull { it.id !in state.read }
                            next?.let { article ->
                                TextButton(onClick = { onRead(article.id) }, modifier = Modifier.padding(top = 8.dp)) {
                                    Icon(Icons.Rounded.AutoStories, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("读一篇未读 · ${article.title}", maxLines = 2)
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("搜索知识或主题") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, "清除知识搜索")
                            } })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("全部", "未读", "收藏").forEach { name ->
                                FilterChip(filter == name, { filter = name }, label = { Text(name) })
                            }
                        }
                    }
                    if (!state.ready) item { CircularProgressIndicator(Modifier.size(24.dp), color = Mist) }
                    else if (articles.isEmpty()) item {
                        Text(if (query.isNotBlank()) "没有找到相关知识，试试“音色”或清除搜索。" else if (filter == "收藏") "还没有收藏。阅读时点右上角书签，喜欢的片段就会留在这里。" else "这里的片段已经读完，可以去全部里再看看。",
                            color = Quiet, fontSize = 14.sp)
                        TextButton(onClick = { query = ""; filter = "全部" }) { Text("查看全部知识") }
                    }
                    items(articles, key = { it.id }) { article ->
                        Column(Modifier.fillMaxWidth().clickable { onRead(article.id) }.padding(vertical = 8.dp)) {
                            Text(article.tags.joinToString(" · "), color = Mist, fontSize = 12.sp)
                            Text(article.title, Modifier.padding(top = 8.dp), color = Moon, fontSize = 19.sp, lineHeight = 29.sp)
                            Text("约 ${readingMinutes(article)} 分钟" + if (article.id in state.read) " · 已读" else "",
                                Modifier.padding(top = 8.dp), color = Quiet, fontSize = 12.sp)
                            HorizontalDivider(Modifier.padding(top = 20.dp), color = Quiet.copy(alpha = .14f))
                        }
                    }
                    item {
                        OutlinedButton(onClick = { importHelp = true }, enabled = !importing.busy) {
                            Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (importing.busy) "正在检查知识包…" else "导入知识更新")
                        }
                        Text("选择栖声提供的知识包，更新后即可离线阅读。", color = Quiet, fontSize = 12.sp)
                        (importing.message ?: state.notice)?.let {
                            Text(it, Modifier.padding(top = 10.dp), color = Mist, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
    if (importHelp) AlertDialog(onDismissRequest = { importHelp = false },
        title = { Text("添加新的三分钟知识") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("内置文章可以直接阅读，不需要先导入。")
            Text("1. 将栖声提供的知识包 ZIP 保存到手机“下载”文件夹。\n2. 点击下方“选择 ZIP 文件”，找到并选中它。\n3. 等待校验完成，成功后显示新版内容与篇数。")
            Text("保留 ZIP 原样。PDF 教材、APK 安装包都不能在这里导入。更新失败时，已有知识会保留。", color = Quiet)
            if (importing.busy) Text("正在检查知识包，请稍候。", color = Mist)
        } },
        confirmButton = { TextButton(enabled = !importing.busy, onClick = { importHelp = false; onImport() }) { Text("选择 ZIP 文件") } },
        dismissButton = { TextButton(onClick = { importHelp = false }) { Text("先读内置文章") } })
}

@Composable
internal fun KnowledgeReader(
    article: KnowledgeArticle, favorite: Boolean, onFavorite: () -> Unit,
    onClose: () -> Unit, onFinished: () -> Unit,
) {
    val list = rememberLazyListState()
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("knowledge-reading-ui", 0) }
    var largeText by rememberSaveable { mutableStateOf(preferences.getBoolean("large-text", false)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭知识阅读", tint = Quiet) }
                    Text("三分钟，听懂一点", Modifier.weight(1f), color = Quiet, fontSize = 14.sp)
                    IconButton(onClick = { largeText = !largeText; preferences.edit().putBoolean("large-text", largeText).apply() }) {
                        Icon(Icons.Rounded.TextFields, if (largeText) "使用标准字号" else "放大阅读字号", tint = Mist)
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(if (favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            if (favorite) "取消收藏" else "收藏这篇", tint = if (favorite) Moon else Quiet)
                    }
                }
                LazyColumn(Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.CenterHorizontally),
                    state = list, contentPadding = PaddingValues(28.dp, 20.dp, 28.dp, 40.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    item {
                        Text(article.tags.joinToString(" · "), color = Mist, fontSize = 12.sp)
                        Text(article.title, Modifier.padding(top = 12.dp), color = Moon, fontSize = 27.sp, lineHeight = 38.sp)
                        Text("约 ${readingMinutes(article)} 分钟 · 静默阅读", Modifier.padding(top = 12.dp), color = Quiet, fontSize = 12.sp)
                    }
                    items(article.paragraphs) { paragraph ->
                        Text(paragraph, color = Color(0xFFE0E6EF), fontSize = if (largeText) 21.sp else 17.sp,
                            lineHeight = if (largeText) 37.sp else 31.sp)
                    }
                    item {
                        HorizontalDivider(color = Quiet.copy(alpha = .18f))
                        Text(article.takeaway, Modifier.padding(top = 20.dp), color = Moon,
                            fontSize = 18.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
                    }
                    item {
                        Text("知识参考", color = Quiet, fontSize = 12.sp)
                        article.citations.forEach {
                            Text("《${it.title}》 · ${it.pages}",
                                Modifier.padding(top = 8.dp), color = Quiet, fontSize = 12.sp, lineHeight = 20.sp)
                        }
                        Text("依据基础概念独立编写", Modifier.padding(top = 12.dp), color = Quiet, fontSize = 12.sp)
                    }
                    item { TextButton(onClick = onFinished) { Text("读完了，歇一会儿") } }
                }
            }
        }
    }
}

private fun readingMinutes(article: KnowledgeArticle): Int =
    ((article.paragraphs.sumOf { p -> p.count { it in '\u3400'..'\u9fff' } } + 249) / 250).coerceIn(3, 5)
