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
}
