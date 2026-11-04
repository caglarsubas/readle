package com.readle.reader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** All content in this spike belongs to the single device-local profile. */
class LibraryDatabase(context: Context, databaseName: String = "readle.db") :
    SQLiteOpenHelper(context, databaseName, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        error("Migration required for version $old to $new")
    }

    fun query(sql: String, args: Array<String> = emptyArray()): JSONArray {
        val rows = JSONArray()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                cursor.columnNames.forEachIndexed { index, name ->
                    row.put(
                        name,
                        when (cursor.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            else -> cursor.getString(index) ?: JSONObject.NULL
                        },
                    )
                }
                rows.put(row)
            }
        }
        return rows
    }

    fun book(id: String): JSONObject =
        query("SELECT * FROM books WHERE id = ?", arrayOf(id)).optJSONObject(0)
            ?: error("Book unavailable")

    fun session(id: String): JSONObject =
        query("SELECT * FROM sessions WHERE id = ?", arrayOf(id)).optJSONObject(0)
            ?: error("Session unavailable")

    fun sessions(bookId: String?): JSONArray {
        val rows =
            if (bookId == null) query("SELECT * FROM sessions ORDER BY updatedAt DESC, id")
            else
                query(
                    "SELECT * FROM sessions WHERE bookId = ? ORDER BY updatedAt DESC, id",
                    arrayOf(bookId),
                )
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val state = JSONObject(row.getString("state"))
            row.put("location", state.optString("locationLabel", "Beginning"))
            val sourceBook = book(row.getString("bookId"))
            row.put(
                "bookTitle",
                sourceBook.getString("title") + " · " + sourceBook.getString("format").uppercase(),
            )
        }
        return rows
    }

    @Synchronized
    fun createSession(bookId: String): JSONObject {
        book(bookId)
        writableDatabase.beginTransaction()
        try {
            val count =
                query("SELECT count(*) AS n FROM sessions WHERE bookId = ?", arrayOf(bookId))
                    .getJSONObject(0)
                    .getInt("n")
            val id = UUID.randomUUID().toString()
            val state =
                JSONObject()
                    .put("mode", "read")
                    .put("readingLocator", JSONObject.NULL)
                    .put("learningCursor", JSONObject.NULL)
                    .put("selection", JSONObject.NULL)
                    .put("visualState", JSONObject())
                    .put("quizDraft", JSONObject.NULL)
                    .put("detours", JSONArray())
                    .put(
                        "assistant",
                        JSONObject()
                            .put("threadId", JSONObject.NULL)
                            .put("model", JSONObject.NULL)
                            .put("draft", ""),
                    )
                    .put("preferences", JSONObject().put("fontSize", 1.0).put("pdfZoom", 1.0))
                    .put("sourceVersion", book(bookId).getString("sourceVersion"))
            writableDatabase.insertOrThrow(
                "sessions",
                null,
                ContentValues().apply {
                    put("id", id)
                    put("bookId", bookId)
                    put("label", "Session ${count + 1}")
                    put("state", state.toString())
                    put("updatedAt", System.currentTimeMillis())
                },
            )
            writableDatabase.setTransactionSuccessful()
            return session(id).put("location", "Beginning")
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun updateSession(id: String, expectedRevision: Int, state: JSONObject) {
        val changed =
            writableDatabase.update(
                "sessions",
                ContentValues().apply {
                    put("state", state.toString())
                    put("revision", expectedRevision + 1)
                    put("updatedAt", System.currentTimeMillis())
                },
                "id = ? AND revision = ?",
                arrayOf(id, expectedRevision.toString()),
            )
        check(changed == 1) { "Session changed. Reopen it to continue safely." }
    }

    @Synchronized
    fun mutateSession(id: String, change: (JSONObject) -> Unit) {
        val row = session(id)
        val state = JSONObject(row.getString("state"))
        change(state)
        updateSession(id, row.getInt("revision"), state)
    }

    fun annotate(bookId: String, locator: JSONObject, quote: String, note: String) {
        writableDatabase.insertOrThrow(
            "annotations",
            null,
            ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("bookId", bookId)
                put("sourceVersion", book(bookId).getString("sourceVersion"))
                put("locator", locator.toString())
                put("quote", quote)
                put("note", note)
                put("createdAt", System.currentTimeMillis())
            },
        )
    }

    fun annotations(bookId: String) =
        query("SELECT * FROM annotations WHERE bookId = ? ORDER BY createdAt", arrayOf(bookId))

    companion object {
        @Volatile private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            instance
                ?: synchronized(this) {
                    instance
                        ?: LibraryDatabase(context.applicationContext).also {
                            instance = it
                            it.setWriteAheadLoggingEnabled(true)
                        }
                }
    }
}
