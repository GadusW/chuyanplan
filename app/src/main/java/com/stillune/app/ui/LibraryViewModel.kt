package com.stillune.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillune.app.data.*
import com.stillune.app.playback.*
import com.stillune.app.analysis.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScanState(val running: Boolean = false, val processed: Int = 0, val message: String? = null)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val connection: PlaybackConnection,
    store: PlaybackStore,
    private val assets: PlayerAssetsRepository,
    private val analysisRepository: AnalysisRepository,
    private val scheduler: AnalysisScheduler,
    private val memory: PlaybackMemory,
    val realtimeAnalysis: RealtimeAnalysisRepository,
) : ViewModel() {
    val tracks = library.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playback = store.state
    val recentHistory = memory.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun playHistory(entry: ListeningEntry) { connection.command { it.playQueue(listOf(entry.track), 0, entry.positionMs) } }
    private val _analysisMode = MutableStateFlow(assets.analysisMode())
    val analysisMode = _analysisMode.asStateFlow()
    fun setAnalysisMode(enabled: Boolean) { _analysisMode.value = enabled; assets.setAnalysisMode(enabled) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val analysis = playback.map { it.track?.uri }.distinctUntilChanged().flatMapLatest { uri ->
        if (uri == null) flowOf(null) else analysisRepository.observe(uri)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun requestAnalysis() {
        playback.value.track?.let { track ->
            viewModelScope.launch { analysisRepository.reset(track.uri); scheduler.enqueue(track.uri, immediate = true) }
        }
    }
    private val _lyrics = MutableStateFlow(Lyrics())
    val lyrics = _lyrics.asStateFlow()
    private val _artwork = MutableStateFlow<android.graphics.Bitmap?>(null)
    val artwork = _artwork.asStateFlow()
    private val _lyricsVisible = MutableStateFlow(assets.lyricsVisible())
    val lyricsVisible = _lyricsVisible.asStateFlow()
    private var lyricJob: Job? = null
    private val _scan = MutableStateFlow(ScanState())
    val scan = _scan.asStateFlow()
    private var scanJob: Job? = null
    private var autoScanned = false
    init {
        connection.connect()
        viewModelScope.launch {
            playback.map { it.track?.uri }.distinctUntilChanged().collectLatest {
                lyricJob?.cancel()
                _lyrics.value = Lyrics(); _artwork.value = null
                playback.value.track?.let { track ->
                    coroutineScope {
                        launch { _artwork.value = assets.artwork(track) }
                        lyricJob = launch { _lyrics.value = assets.lyrics(track) }
                    }
                }
            }
        }
    }
    fun toggleLyrics() {
        _lyricsVisible.value = !_lyricsVisible.value
        assets.setLyricsVisible(_lyricsVisible.value)
    }
    fun importLyrics(track: Track, uri: Uri) {
        assets.associateLyrics(track, uri)
        lyricJob?.cancel()
        lyricJob = viewModelScope.launch {
            val result = assets.lyrics(track)
            if (playback.value.track?.uri == track.uri) {
                _lyrics.value = result
                _lyricsVisible.value = true; assets.setLyricsVisible(true)
            }
        }
    }
    fun autoScan() {
        if (!autoScanned) { autoScanned = true; scanAll(ScanOptions(), true) }
    }
    fun scanAll(options: ScanOptions, canReadMedia: Boolean) = launchScan {
        var total = 0; var skipped = 0; var failed = 0
        if (canReadMedia) library.scanMedia(options, ::progress).also { total += it.found; skipped += it.skipped; failed += it.failed }
        for (folder in library.savedFolders()) {
            try {
                library.scanFolder(Uri.parse(folder), options) { progress(total + it) }
                    .also { total += it.found; skipped += it.skipped; failed += it.failed }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed++ }
        }
        if (!canReadMedia && library.savedFolders().isEmpty()) "请授权音频访问，或选择音乐文件夹。"
        else "扫描完成 · 曲库共 ${library.tracks.first().size} 首，过滤 $skipped 项" + if (failed > 0) "，$failed 项未能读取，可重新授权后重试" else ""
    }
    fun scanFolder(uri: Uri, options: ScanOptions) {
        library.rememberFolder(uri)
        launchScan {
            val report = library.scanFolder(uri, options, ::progress)
            "文件夹扫描完成 · 找到 ${report.found} 首，过滤 ${report.skipped} 项" + if (report.failed > 0) "，${report.failed} 项未能读取" else ""
        }
    }
    fun importFiles(uris: List<Uri>) = launchScan {
        val report = library.importFiles(uris)
        "已导入 ${report.found} 首" + if (report.failed > 0) "，${report.failed} 个文件无法读取或不是音频" else ""
    }
    private fun progress(count: Int) { _scan.update { it.copy(processed = count) } }
    private fun launchScan(task: suspend () -> String) {
        if (_scan.value.running) return
        scanJob = viewModelScope.launch {
            _scan.value = ScanState(running = true)
            try { _scan.update { it.copy(message = task()) } }
            catch (e: CancellationException) { _scan.update { it.copy(message = "扫描已取消，已完成的导入仍然保留。") }; throw e }
            catch (e: SecurityException) { _scan.update { it.copy(message = "存储授权已失效，请重新授权或选择文件夹。") } }
            catch (e: Exception) { _scan.update { it.copy(message = e.message ?: "扫描失败，请稍后重试。") } }
            finally { _scan.update { it.copy(running = false) } }
        }
    }
    fun cancelScan() { scanJob?.cancel() }
    fun clearMessage() { _scan.update { it.copy(message = null) } }
    fun message(message: String) { _scan.update { it.copy(message = message) } }
    fun play(track: Track, queue: List<Track> = tracks.value) {
        val actual = queue.ifEmpty { listOf(track) }
        connection.command { it.playQueue(actual, actual.indexOfFirst { t -> t.uri == track.uri }.coerceAtLeast(0)) }
    }
    fun toggle() = connection.command { it.toggle() }
    fun next() = connection.command { it.next() }
    fun previous() = connection.command { it.previous() }
    fun seek(position: Long) = connection.command { it.seek(position) }
    fun shuffle() = connection.command { it.toggleShuffle() }
    fun repeat() = connection.command { it.cycleRepeat() }
    fun clearError() = connection.command { it.clearError() }
    override fun onCleared() { connection.close() }
}
