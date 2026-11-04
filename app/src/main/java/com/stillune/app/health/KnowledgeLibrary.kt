package com.stillune.app.health

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.edit
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class KnowledgeCitation(val title: String, val pages: String)
data class KnowledgeArticle(
    val id: String, val title: String, val paragraphs: List<String>, val takeaway: String,
    val tags: List<String>, val citations: List<KnowledgeCitation>,
)
data class KnowledgeState(
    val articles: List<KnowledgeArticle> = emptyList(), val version: Long = 0,
    val read: Set<String> = emptySet(), val favorites: Set<String> = emptySet(),
    val ready: Boolean = false, val notice: String? = null,
)
internal data class KnowledgeEdition(val version: Long, val articles: List<KnowledgeArticle>)

internal object KnowledgePackage {
    const val LIMIT = 2_000_000
    private val id = Regex("[a-z0-9][a-z0-9-]{0,63}")

    fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= LIMIT) { "知识包过大，请使用小于 2 MB 的文件" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun verify(bytes: ByteArray, publicKey: ByteArray): KnowledgeEdition {
        require(bytes.size <= LIMIT) { "知识包过大" }
        val files = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("manifest.json", "articles.json", "signature.ed25519") &&
                    entry.name !in files) { "知识包结构不符合要求" }
                val data = readLimited(zip)
                total += data.size
                require(total <= LIMIT) { "知识包解压后过大" }
                files[entry.name] = data
            }
        }
        require(files.size == 3 && publicKey.size == 32) { "知识包或公钥不完整" }
        val manifest = files.getValue("manifest.json")
        val signature = files.getValue("signature.ed25519")
        require(signature.size == 64) { "知识包签名不完整" }
        val verifier = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
        verifier.initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, EdDSANamedCurveTable.getByName("Ed25519"))))
        verifier.update(manifest)
        require(verifier.verify(signature)) { "知识包签名不匹配，请使用栖声发布者提供的文件" }
        val metadata = JSONObject(manifest.toString(Charsets.UTF_8))
        require(metadata.get("schema") == 1) { "暂不支持这个知识包格式" }
        val number = metadata.get("version")
        require(number is Int || number is Long) { "知识包版本不正确" }
        val version = (number as Number).toLong()
        require(version > 0) { "知识包版本不正确" }
        val payload = files.getValue("articles.json")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == metadata.getString("articles_sha256")) { "知识包内容校验失败" }
        val articles = parse(payload)
        require(metadata.get("count") == articles.size) { "知识包文章数量不一致" }
        return KnowledgeEdition(version, articles)
    }

    fun parse(payload: ByteArray): List<KnowledgeArticle> {
        require(payload.size <= LIMIT)
        val root = JSONObject(payload.toString(Charsets.UTF_8))
        val sourceRows = root.getJSONArray("sources")
        require(sourceRows.length() in 1..200) { "知识来源数量不正确" }
        val sources = mutableMapOf<String, String>()
        for (i in 0 until sourceRows.length()) {
            val row = sourceRows.getJSONObject(i)
            val key = text(row, "id", 1, 64)
            require(id.matches(key) && key !in sources) { "知识来源 ID 重复或不正确" }
            sources[key] = text(row, "title", 1, 300)
        }
        val rows = root.getJSONArray("articles")
        require(rows.length() in 1..200) { "知识包应包含 1–200 篇文章" }
        val ids = mutableSetOf<String>()
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val key = text(row, "id", 1, 64)
            require(id.matches(key) && ids.add(key)) { "文章 ID 重复或不正确" }
            val paragraphs = strings(row.getJSONArray("paragraphs"), 3, 8, 1500)
            val hanCount = paragraphs.sumOf { paragraph -> paragraph.count { it in '\u3400'..'\u9fff' } }
            require(hanCount in 600..1200) { "文章长度不符合三至五分钟阅读规范" }
            val refs = row.getJSONArray("citations")
            require(refs.length() in 1..10) { "文章缺少来源" }
            val citations = (0 until refs.length()).map {
                val reference = refs.getJSONObject(it)
                val source = sources[text(reference, "source_id", 1, 64)]
                require(source != null && reference.get("verified") == true) { "文章引用未核验" }
                val pages = reference.getJSONArray("pdf_pages")
                require(pages.length() in 1..30 && (0 until pages.length()).all { n ->
                    val page = pages.get(n)
                    page is Int && page in 1..100_000
                }) { "参考页码不正确" }
                KnowledgeCitation(source, text(reference, "printed_pages", 1, 100))
            }
            KnowledgeArticle(key, text(row, "title", 1, 40), paragraphs, text(row, "takeaway", 1, 100),
                strings(row.getJSONArray("tags"), 1, 5, 20), citations)
        }
    }

    private fun text(row: JSONObject, field: String, min: Int, max: Int): String {
        val value = row.get(field)
        require(value is String && value.isNotBlank() && value.length in min..max) { "知识内容字段不完整" }
        return value
    }

    private fun strings(rows: JSONArray, min: Int, max: Int, maxLength: Int): List<String> {
        require(rows.length() in min..max) { "知识内容段落或标签数量不正确" }
        return (0 until rows.length()).map {
            val text = rows.get(it)
            require(text is String && text.isNotBlank() && text.length <= maxLength) { "知识内容文本不正确" }
            text
        }
    }
}

@Singleton
class KnowledgeLibrary private constructor(
    private val context: Context, private val io: CoroutineDispatcher, private val publicKey: ByteArray,
) {
    @Inject constructor(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher) :
        this(context, io, context.assets.open("knowledge/publisher.key").use { it.readBytes() })

    internal constructor(context: Context, io: CoroutineDispatcher, testKey: ByteArray, @Suppress("UNUSED_PARAMETER") testing: Unit) :
        this(context, io, testKey)

    private val file = AtomicFile(File(context.filesDir, "knowledge-edition.zip"))
    private val prefs = context.getSharedPreferences("knowledge-reading", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _state = MutableStateFlow(KnowledgeState())
    val state = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + io)

    init { scope.launch { load() } }

    private suspend fun load() = lock.withLock {
        val base = context.assets.open("knowledge/builtin.json").use { KnowledgePackage.parse(KnowledgePackage.readLimited(it)) }
        val baseVersion = context.assets.open("knowledge/builtin-version.json").use {
            JSONObject(it.readBytes().toString(Charsets.UTF_8)).getLong("version")
        }
        var notice: String? = null
        val installed = try {
            file.openRead().use { KnowledgePackage.verify(KnowledgePackage.readLimited(it), publicKey) }
        } catch (_: java.io.FileNotFoundException) { null }
        catch (_: Exception) { notice = "上次导入的知识包不可用，已显示内置内容"; null }
        val current = installed?.takeIf { it.version > baseVersion }
        _state.value = KnowledgeState(current?.articles ?: base, current?.version ?: baseVersion,
            prefs.getStringSet("read", emptySet()).orEmpty().toSet(),
            prefs.getStringSet("favorites", emptySet()).orEmpty().toSet(), true, notice)
    }

    suspend fun import(uri: Uri) = withContext(io) {
        val bytes = context.contentResolver.openInputStream(uri)?.use(KnowledgePackage::readLimited)
            ?: error("无法读取这个知识包")
        install(bytes)
    }

    internal suspend fun install(bytes: ByteArray) = withContext(io) {
        state.first { it.ready }
        lock.withLock {
            val edition = KnowledgePackage.verify(bytes, publicKey)
            require(edition.version > state.value.version) { "请导入版本更高的知识包" }
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            _state.update { it.copy(articles = edition.articles, version = edition.version, notice = null) }
            edition.articles.size
        }
    }

    suspend fun choose(): KnowledgeArticle = withContext(io) {
        state.first { it.ready }
        lock.withLock {
            val current = state.value
            val recent = prefs.getString("recent", "").orEmpty().split('|').filter(String::isNotBlank)
            val unseen = current.articles.filter { it.id !in current.read && it.id !in recent }
            val candidates = unseen.ifEmpty { current.articles.filter { it.id !in recent } }
                .ifEmpty { current.articles.filter { it.id != recent.lastOrNull() } }.ifEmpty { current.articles }
            val article = candidates.random()
            prefs.edit { putString("recent", (recent + article.id).takeLast(10).joinToString("|")) }
            article
        }
    }

    suspend fun markRead(id: String) = withContext(io) {
        lock.withLock {
            val read = state.value.read + id
            prefs.edit { putStringSet("read", read) }
            _state.update { it.copy(read = read) }
        }
    }

    suspend fun toggleFavorite(id: String) = withContext(io) {
        lock.withLock {
            val old = state.value.favorites
            val next = if (id in old) old - id else old + id
            prefs.edit { putStringSet("favorites", next) }
            _state.update { it.copy(favorites = next) }
        }
    }
}
