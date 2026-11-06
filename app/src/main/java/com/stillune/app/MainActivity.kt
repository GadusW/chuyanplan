package com.stillune.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillune.app.data.ScanOptions
import com.stillune.app.data.Track
import com.stillune.app.ui.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openPlayerRequest by mutableIntStateOf(0)
    private var openHealthRequest by mutableIntStateOf(0)
    private val model: LibraryViewModel by viewModels()
    private var options = ScanOptions()
    private var lyricTarget: Track? = null
    private val lyricPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = lyricTarget
        if (uri != null && target != null && persist(uri)) model.importLyrics(target, uri)
        lyricTarget = null
    }
    private val knowledgePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importKnowledge)
    }
    private val audioPermission get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[audioPermission] == true || hasAudioPermission()) model.scanAll(options, true)
        else model.message("未获得全机音频访问权限，仍可选择文件夹或单独导入文件。")
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && persist(uri)) model.scanFolder(uri, options)
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val readable = uris.filter(::persist)
        if (readable.isNotEmpty()) model.importFiles(readable)
    }
    private fun persist(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        model.message("这个文件提供方不支持持久授权，请从设备本地存储重新选择。")
        false
    }
    private fun requestScan() {
        if (hasAudioPermission()) model.scanAll(options, true)
        else permissionRequest.launch(arrayOf(audioPermission))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == OPEN_PLAYER) openPlayerRequest++
        if (intent?.action == OPEN_HEALTH) openHealthRequest++
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            val tracks by model.tracks.collectAsStateWithLifecycle()
            val playback by model.playback.collectAsStateWithLifecycle()
            val scan by model.scan.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                if (hasAudioPermission()) model.autoScan()
            }
            StilluneTheme {
                StilluneApp(tracks, playback, scan, model,
                    onScan = { selected -> options = selected; requestScan() },
                    onFolder = { selected -> options = selected; folderPicker.launch(null) },
                    onImport = { filePicker.launch(arrayOf("*/*")) },
                    onImportLyrics = { model.playback.value.track?.let { lyricTarget = it; lyricPicker.launch(arrayOf("*/*")) } },
                    onImportKnowledge = { knowledgePicker.launch(arrayOf("*/*")) },
                    openPlayerRequest = openPlayerRequest, openHealthRequest = openHealthRequest)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == OPEN_PLAYER) openPlayerRequest++
        if (intent.action == OPEN_HEALTH) openHealthRequest++
    }
    companion object {
        const val OPEN_PLAYER = "com.stillune.app.OPEN_PLAYER"
        const val OPEN_HEALTH = "com.stillune.app.OPEN_HEALTH"
    }
}
