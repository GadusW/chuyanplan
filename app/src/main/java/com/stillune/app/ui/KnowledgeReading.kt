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
    val articles = state.articles.filter {
        when (filter) {
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
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("三分钟，听懂一点", color = Moon, fontSize = 27.sp, lineHeight = 38.sp)
                        Text("一个小问题，一次从容的休息。", Modifier.padding(top = 12.dp), color = Quiet, fontSize = 14.sp)
                        Text(if (state.version == 0L) "内置内容 · ${state.articles.size} 篇"
                            else "知识包第 ${state.version} 版 · ${state.articles.size} 篇",
                            Modifier.padding(top = 10.dp), color = Mist, fontSize = 12.sp)
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("全部", "未读", "收藏").forEach { name ->
                                FilterChip(filter == name, { filter = name }, label = { Text(name) })
                            }
                        }
                    }
                    if (!state.ready) item { CircularProgressIndicator(Modifier.size(24.dp), color = Mist) }
                    else if (articles.isEmpty()) item {
                        Text(if (filter == "收藏") "喜欢的片段，读到时可以收藏。" else "这里的片段已经读完，可以去全部里再看看。",
                            color = Quiet, fontSize = 14.sp)
                    }
                    items(articles, key = { it.id }) { article ->
                        Column(Modifier.fillMaxWidth().clickable { onRead(article.id) }.padding(vertical = 8.dp)) {
                            Text(article.tags.joinToString(" · "), color = Mist, fontSize = 12.sp)
                            Text(article.title, Modifier.padding(top = 8.dp), color = Moon, fontSize = 19.sp, lineHeight = 29.sp)
                            Text("约 3 分钟" + if (article.id in state.read) " · 已读" else "",
                                Modifier.padding(top = 8.dp), color = Quiet, fontSize = 12.sp)
                            HorizontalDivider(Modifier.padding(top = 20.dp), color = Quiet.copy(alpha = .14f))
                        }
                    }
                    item {
                        OutlinedButton(onClick = onImport, enabled = !importing.busy) {
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
}

@Composable
internal fun KnowledgeReader(
    article: KnowledgeArticle, favorite: Boolean, onFavorite: () -> Unit,
    onClose: () -> Unit, onFinished: () -> Unit,
) {
    val list = rememberLazyListState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭知识阅读", tint = Quiet) }
                    Text("三分钟，听懂一点", Modifier.weight(1f), color = Quiet, fontSize = 14.sp)
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
                        Text("约 3 分钟 · 静默阅读", Modifier.padding(top = 12.dp), color = Quiet, fontSize = 12.sp)
                    }
                    items(article.paragraphs) { paragraph ->
                        Text(paragraph, color = Color(0xFFE0E6EF), fontSize = 17.sp, lineHeight = 31.sp)
                    }
                    item {
                        HorizontalDivider(color = Quiet.copy(alpha = .18f))
                        Text(article.takeaway, Modifier.padding(top = 20.dp), color = Moon,
                            fontSize = 18.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
                    }
                    item {
                        Text("知识参考", color = Quiet, fontSize = 12.sp)
                        article.citations.forEach {
                            Text("《${it.title}》 · 第 ${it.pages} 页",
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
