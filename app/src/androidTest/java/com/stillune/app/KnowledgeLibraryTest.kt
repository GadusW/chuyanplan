package com.stillune.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.health.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class KnowledgeLibraryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(name: String) = instrumentation.context.assets.open("knowledge/$name").use { it.readBytes() }
    private fun testContext(): Context = object : ContextWrapper(context) {
        private val suffix = "knowledge-test-${System.nanoTime()}"
        override fun getFilesDir() = File(super.getFilesDir(), suffix).apply { mkdirs() }
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            super.getSharedPreferences(suffix, mode)
    }

    @Test fun builtinArticlesAreIndependentLengthCheckedAndRandomSelectionAvoidsEarlyRepeats() = runBlocking {
        val library = KnowledgeLibrary(testContext(), Dispatchers.IO)
        val state = withTimeout(5_000) { library.state.first { it.ready } }
        assertEquals(60, state.articles.size)
        assertTrue(state.articles.all { it.citations.isNotEmpty() })
        val selected = (1..6).map { library.choose().id }
        assertEquals(6, selected.toSet().size)
    }

    @Test fun pythonSignedPackageIsAcceptedAndWrongPublisherOrEditedContentIsRejected() {
        val key = fixture("test-publisher.key")
        val bytes = fixture("edition-1.zip")
        assertEquals(2, KnowledgePackage.verify(bytes, key).articles.size)
        val wrongKey = key.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { KnowledgePackage.verify(bytes, wrongKey) }
        val changed = ByteArrayOutputStream()
        ZipOutputStream(changed).use { output ->
            ZipInputStream(bytes.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val data = input.readBytes()
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(if (entry.name == "articles.json") data + ' '.code.toByte() else data)
                    output.closeEntry()
                }
            }
        }
        assertThrows(Exception::class.java) { KnowledgePackage.verify(changed.toByteArray(), key) }
        assertThrows(Exception::class.java) { KnowledgePackage.readLimited(ByteArray(KnowledgePackage.LIMIT + 1).inputStream()) }
    }

    @Test fun importsAreAtomicMonotonicAndPersistWithReadAndFavoriteState() = runBlocking {
        val isolated = testContext()
        val key = fixture("test-publisher.key")
        val library = KnowledgeLibrary(isolated, Dispatchers.IO, key, Unit)
        withTimeout(5_000) { library.state.first { it.ready } }
        library.install(fixture("edition-1.zip"))
        val first = library.state.value.articles.first()
        library.markRead(first.id)
        library.toggleFavorite(first.id)
        assertTrue(first.id in library.state.value.read)
        try {
            library.install(fixture("edition-1.zip"))
            fail("Old edition was accepted")
        } catch (_: IllegalArgumentException) { }
        val invalid = fixture("edition-2.zip").apply { this[0] = 0 }
        try {
            library.install(invalid)
            fail("Invalid edition was accepted")
        } catch (_: Exception) { }
        assertEquals(4L, library.state.value.version)
        library.install(fixture("edition-2.zip"))
        assertEquals(1, library.state.value.articles.size)
        val reopened = KnowledgeLibrary(isolated, Dispatchers.IO, key, Unit)
        val state = withTimeout(5_000) { reopened.state.first { it.ready } }
        assertEquals(5L, state.version)
        assertTrue(first.id in state.read && first.id in state.favorites)
    }

    @Test fun updatedBuiltinReplacesPreviouslyImportedOlderCatalog() = runBlocking {
        val isolated = testContext()
        File(isolated.filesDir, "knowledge-edition.zip").writeBytes(fixture("edition-legacy.zip"))
        val library = KnowledgeLibrary(isolated, Dispatchers.IO, fixture("test-publisher.key"), Unit)
        val state = withTimeout(5_000) { library.state.first { it.ready } }
        assertEquals(3L, state.version)
        assertEquals(60, state.articles.size)
        assertFalse(state.articles.any { it.id == "air-and-sound" })
        try {
            library.install(fixture("edition-legacy.zip"))
            fail("Older content replaced the new builtin catalog")
        } catch (_: IllegalArgumentException) { }
    }
}
