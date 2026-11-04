package com.readle.reader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: LibraryDatabase
    private lateinit var bookId: String

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("readle-test.db")
        // A separate database preserves all manual UAT library/session data.
        db = LibraryDatabase(context, "readle-test.db")
        val source = java.util.UUID.randomUUID().toString()
        bookId = source
        db.writableDatabase.insertOrThrow(
            "books",
            null,
            ContentValues().apply {
                put("id", source)
                put("title", "Persistence fixture")
                put("format", "pdf")
                put("path", "fixture")
                put("sourceVersion", source)
            },
        )
    }

    @After
    fun teardown() {
        db.close()
        context.deleteDatabase("readle-test.db")
    }

    @Test
    fun twoSessionsSurviveReopenWithIndependentWorkspaceAndSharedAnnotations() {
        val first = db.createSession(bookId).getString("id")
        val second = db.createSession(bookId).getString("id")
        db.mutateSession(first) {
            it.put("readingLocator", JSONObject().put("pageIndex", 1))
            it.getJSONObject("assistant").put("draft", "Why this equation?")
        }
        db.mutateSession(second) { it.put("readingLocator", JSONObject().put("pageIndex", 2)) }
        db.annotate(
            bookId,
            JSONObject().put("pageIndex", 1),
            "The weighted average",
            "Check the assumption",
        )
        db.close()
        db = LibraryDatabase(context, "readle-test.db")
        assertEquals(
            1,
            JSONObject(db.session(first).getString("state"))
                .getJSONObject("readingLocator")
                .getInt("pageIndex"),
        )
        assertEquals(
            2,
            JSONObject(db.session(second).getString("state"))
                .getJSONObject("readingLocator")
                .getInt("pageIndex"),
        )
        assertEquals(
            "Why this equation?",
            JSONObject(db.session(first).getString("state"))
                .getJSONObject("assistant")
                .getString("draft"),
        )
        assertEquals(1, db.annotations(bookId).length())
        assertEquals(2, db.sessions(bookId).length())
    }

    @Test
    fun staleWriterCannotOverwriteSavedPosition() {
        val session = db.createSession(bookId)
        val stale = JSONObject(session.getString("state"))
        db.mutateSession(session.getString("id")) {
            it.put("readingLocator", JSONObject().put("pageIndex", 2))
        }
        try {
            db.updateSession(session.getString("id"), 0, stale)
            fail("Stale revision accepted")
        } catch (_: IllegalStateException) {}
        assertEquals(
            2,
            JSONObject(db.session(session.getString("id")).getString("state"))
                .getJSONObject("readingLocator")
                .getInt("pageIndex"),
        )
    }

    @Test
    fun browsingBookCreatesNoSessionAndOtherBooksAreExcluded() {
        db.createSession(bookId)
        val otherId = java.util.UUID.randomUUID().toString()
        db.writableDatabase.insertOrThrow(
            "books",
            null,
            ContentValues().apply {
                put("id", otherId)
                put("title", "Other book")
                put("format", "pdf")
                put("path", "other-fixture")
                put("sourceVersion", otherId)
            },
        )
        val otherSession = db.createSession(otherId).getString("id")
        db.book(bookId)
        db.book(bookId)
        assertEquals(1, db.sessions(bookId).length())
        assertEquals(1, db.sessions(otherId).length())
        assertEquals(otherSession, db.sessions(otherId).getJSONObject(0).getString("id"))
        assertNotEquals(bookId, db.sessions(otherId).getJSONObject(0).getString("bookId"))
    }

    @Test
    fun corruptItemAndDuplicateDoNotRollbackSuccessfulImports() {
        val importer = BookImporter(context, db)
        val good = File(context.cacheDir, "test-import.epub")
        context.assets.open("fixtures/readle-sample.epub").use { input ->
            good.outputStream().use { input.copyTo(it) }
        }
        val first = importer.register(Uri.fromFile(good), "Fixture.epub")
        importer.process(first)
        val bad = File(context.cacheDir, "bad.pdf").apply { writeText("not a PDF") }
        val failed = importer.register(Uri.fromFile(bad), "bad.pdf")
        importer.process(failed)
        val duplicate = importer.register(Uri.fromFile(good), "copy.epub")
        importer.process(duplicate)
        val row = { id: String ->
            db.query("SELECT * FROM imports WHERE id = ?", arrayOf(id)).getJSONObject(0)
        }
        assertEquals("failed", row(failed).getString("status"))
        assertEquals("duplicate", row(duplicate).getString("status"))
        assertTrue(row(first).getString("status") in listOf("ready", "duplicate"))
        assertEquals(row(first).getString("bookId"), row(duplicate).getString("bookId"))
        importer.process(failed)
        assertEquals(row(first).getString("bookId"), row(duplicate).getString("bookId"))
    }

    @Test
    fun reimportRepairsMissingSourceWithoutLosingSavedSession() {
        val source = File(context.cacheDir, "repair-test.epub")
        context.assets.open("fixtures/readle-sample.epub").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        // A unique ZIP comment keeps the repair fixture separate from manual UAT source blobs.
        val uniqueSource = File(context.cacheDir, "unique-repair-test.epub")
        java.util.zip.ZipFile(source).use { archive ->
            java.util.zip.ZipOutputStream(uniqueSource.outputStream()).use { output ->
                output.setComment(java.util.UUID.randomUUID().toString())
                archive.entries().asSequence().forEach { entry ->
                    val copiedEntry =
                        java.util.zip.ZipEntry(entry.name).apply {
                            method = entry.method
                            if (method == java.util.zip.ZipEntry.STORED) {
                                size = entry.size
                                crc = entry.crc
                            }
                        }
                    output.putNextEntry(copiedEntry)
                    archive.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        val importer = BookImporter(context, db)
        val first = importer.register(Uri.fromFile(uniqueSource), "Repair fixture.epub")
        importer.process(first)
        val sourceBook =
            db.query("SELECT bookId FROM imports WHERE id = ?", arrayOf(first))
                .getJSONObject(0)
                .getString("bookId")
        val session = db.createSession(sourceBook).getString("id")
        db.mutateSession(session) { it.put("readingLocator", JSONObject().put("position", 9)) }
        val stored = File(db.book(sourceBook).getString("path"))
        assertTrue(stored.delete())
        val retry = importer.register(Uri.fromFile(uniqueSource), "Repair fixture.epub")
        importer.process(retry)
        assertTrue(stored.readBytes().contentEquals(uniqueSource.readBytes()))
        assertEquals(
            sourceBook,
            db.query("SELECT bookId FROM imports WHERE id = ?", arrayOf(retry))
                .getJSONObject(0)
                .getString("bookId"),
        )
        assertEquals(
            9,
            JSONObject(db.session(session).getString("state"))
                .getJSONObject("readingLocator")
                .getInt("position"),
        )
        stored.delete()
    }
}
