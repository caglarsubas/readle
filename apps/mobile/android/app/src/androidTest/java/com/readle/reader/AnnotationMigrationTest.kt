package com.readle.reader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "readle-migration-test.db"
    private var upgraded: LibraryDatabase? = null

    @After
    fun cleanup() {
        upgraded?.close()
        context.deleteDatabase(name)
    }

    private fun versionOne(existingSessions: Boolean) {
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE books (id TEXT PRIMARY KEY, title TEXT NOT NULL, format TEXT NOT NULL, path TEXT NOT NULL, sourceVersion TEXT NOT NULL UNIQUE)"
            )
            db.execSQL(
                "CREATE TABLE sessions (id TEXT PRIMARY KEY, bookId TEXT NOT NULL REFERENCES books(id), label TEXT NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX sessions_book ON sessions(bookId, updatedAt DESC)")
            db.execSQL(
                "CREATE TABLE annotations (id TEXT PRIMARY KEY, bookId TEXT NOT NULL REFERENCES books(id), sourceVersion TEXT NOT NULL, locator TEXT NOT NULL, quote TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE imports (id TEXT PRIMARY KEY, uri TEXT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, error TEXT NOT NULL DEFAULT '', bookId TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL(
                "INSERT INTO books VALUES ('book', 'Legacy fixture', 'epub', 'legacy-path', 'source-hash')"
            )
            if (existingSessions)
                for ((index, id) in listOf("one", "two").withIndex()) {
                    db.insertOrThrow(
                        "sessions",
                        null,
                        ContentValues().apply {
                            put("id", id)
                            put("bookId", "book")
                            put("label", "Old session $id")
                            put(
                                "state",
                                JSONObject()
                                    .put("sourceVersion", "source-hash")
                                    .put("readingLocator", JSONObject().put("position", index + 8))
                                    .put("preferences", JSONObject().put("fontSize", 1.3))
                                    .put("assistant", JSONObject().put("draft", "Unsent $id"))
                                    .toString(),
                            )
                            put("revision", 7)
                            put("updatedAt", 123L + index)
                        },
                    )
                }
            db.execSQL(
                "INSERT INTO annotations VALUES ('legacy-id', 'book', 'source-hash', '{\"pageIndex\":1}', 'Original words', 'Original note', 42)"
            )
            db.version = 1
        }
    }

    @Test
    fun upgradePreservesExistingWorkAndNewSessionsAreBlank() {
        versionOne(true)
        val db = LibraryDatabase(context, name).also { upgraded = it }
        assertEquals(2, db.writableDatabase.version)
        for ((index, id) in listOf("one", "two").withIndex()) {
            val row = db.session(id)
            assertEquals(7, row.getInt("revision"))
            assertEquals(123L + index, row.getLong("updatedAt"))
            val state = JSONObject(row.getString("state"))
            assertEquals(index + 8, state.getJSONObject("readingLocator").getInt("position"))
            assertEquals("Unsent $id", state.getJSONObject("assistant").getString("draft"))
            val annotation = db.annotations(id).getJSONObject(0)
            assertEquals("Original note", annotation.getString("note"))
            assertEquals("Original words", annotation.getString("quote"))
            assertEquals("{\"pageIndex\":1}", annotation.getString("locator"))
            assertEquals(42L, annotation.getLong("createdAt"))
            assertEquals("legacy-id", annotation.getString("originId"))
        }
        val first = db.annotations("one").getJSONObject(0).getString("id")
        val second = db.annotations("two").getJSONObject(0).getString("id")
        assertEquals("legacy-id", first)
        assertNotEquals(first, second)
        db.updateAnnotationNote("one", first, "Original note", "Session one only")
        assertEquals("Original note", db.annotation("two", second)!!.getString("note"))
        db.deleteAnnotation("one", first)
        assertEquals(1, db.annotations("two").length())
        val blank = db.createSession("book").getString("id")
        assertEquals(0, db.annotations(blank).length())
        assertEquals(0, db.annotations("one").length())
        db.close()
        upgraded = LibraryDatabase(context, name)
        assertEquals(0, upgraded!!.annotations(blank).length())
        assertEquals("Original note", upgraded!!.annotation("two", second)!!.getString("note"))
    }

    @Test
    fun orphanedLegacyNotesGetARecoverableSessionWithoutLeakingIntoNewWork() {
        versionOne(false)
        val db = LibraryDatabase(context, name).also { upgraded = it }
        val recovered = db.sessions("book").getJSONObject(0)
        assertEquals("Recovered notes", recovered.getString("label"))
        assertEquals(
            "Original note",
            db.annotations(recovered.getString("id")).getJSONObject(0).getString("note"),
        )
        assertEquals(0, db.annotations(db.createSession("book").getString("id")).length())
    }
}
