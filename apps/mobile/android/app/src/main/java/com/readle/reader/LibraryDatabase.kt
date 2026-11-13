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
    SQLiteOpenHelper(context, databaseName, null, 3) {
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
        createAnnotations(db)
        createPdfEditions(db)
        db.execSQL(
            "CREATE TABLE imports (id TEXT PRIMARY KEY, uri TEXT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, error TEXT NOT NULL DEFAULT '', bookId TEXT NOT NULL DEFAULT '')"
        )
    }

    private fun createPdfEditions(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE pdf_epub_editions (pdfId TEXT PRIMARY KEY REFERENCES books(id), epubId TEXT NOT NULL UNIQUE REFERENCES books(id))"
        )
    }

    private fun createAnnotations(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE annotations (id TEXT PRIMARY KEY, sessionId TEXT NOT NULL REFERENCES sessions(id), bookId TEXT NOT NULL REFERENCES books(id), sourceVersion TEXT NOT NULL, locator TEXT NOT NULL, quote TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, originId TEXT)"
        )
        db.execSQL("CREATE INDEX annotations_session ON annotations(sessionId, createdAt)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        check(old in 1..2 && new == 3) { "Migration required for version $old to $new" }
        if (old == 1) migrateSessionAnnotations(db)
        createPdfEditions(db)
    }

    private fun migrateSessionAnnotations(db: SQLiteDatabase) {
        // v1 did not record an originating session. Preserve what every existing session could see,
        // then let each copy evolve independently. Future sessions receive no copied annotations.
        db.execSQL("ALTER TABLE annotations RENAME TO annotations_v1")
        createAnnotations(db)
        val sessionsByBook = mutableMapOf<String, List<String>>()
        db.rawQuery("SELECT * FROM annotations_v1 ORDER BY createdAt, id", null).use { rows ->
            while (rows.moveToNext()) {
                val bookId = rows.getString(rows.getColumnIndexOrThrow("bookId"))
                val origin = rows.getString(rows.getColumnIndexOrThrow("id"))
                val source = rows.getString(rows.getColumnIndexOrThrow("sourceVersion"))
                val targets =
                    sessionsByBook.getOrPut(bookId) {
                        val ids = mutableListOf<String>()
                        db.rawQuery(
                                "SELECT id FROM sessions WHERE bookId = ? ORDER BY updatedAt, id",
                                arrayOf(bookId),
                            )
                            .use {
                                while (it.moveToNext()) ids.add(it.getString(0))
                            }
                        if (ids.isEmpty()) {
                            val id = UUID.randomUUID().toString()
                            db.insertOrThrow(
                                "sessions",
                                null,
                                sessionValues(id, bookId, "Recovered notes", source),
                            )
                            ids.add(id)
                        }
                        ids
                    }
                targets.forEachIndexed { index, sessionId ->
                    db.insertOrThrow(
                        "annotations",
                        null,
                        ContentValues().apply {
                            put("id", if (index == 0) origin else UUID.randomUUID().toString())
                            put("sessionId", sessionId)
                            put("bookId", bookId)
                            put("sourceVersion", source)
                            for (name in listOf("locator", "quote", "note")) put(
                                name,
                                rows.getString(rows.getColumnIndexOrThrow(name)),
                            )
                            put("createdAt", rows.getLong(rows.getColumnIndexOrThrow("createdAt")))
                            put("originId", origin)
                        },
                    )
                }
            }
        }
        db.execSQL("DROP TABLE annotations_v1")
    }

    private fun sessionValues(
        id: String,
        bookId: String,
        label: String,
        source: String,
        learningCursor: JSONObject? = null,
    ) =
        ContentValues().apply {
            put("id", id)
            put("bookId", bookId)
            put("label", label)
            put(
                "state",
                JSONObject()
                    .put("mode", if (learningCursor == null) "read" else "learn")
                    .put("readingLocator", JSONObject.NULL)
                    .put("learningCursor", learningCursor ?: JSONObject.NULL)
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
                    .put(
                        "preferences",
                        JSONObject()
                            .put("fontSize", 1.0)
                            .put("pdfZoom", 1.0)
                            .put("epubScroll", true),
                    )
                    .put("sourceVersion", source)
                    .toString(),
            )
            put("updatedAt", System.currentTimeMillis())
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

    fun books(): JSONArray = query(
        """
        SELECT books.*, converted.epubId, origin.pdfId AS sourcePdfId FROM books
        LEFT JOIN pdf_epub_editions converted ON converted.pdfId = books.id
        LEFT JOIN pdf_epub_editions origin ON origin.epubId = books.id
        ORDER BY CASE WHEN format = 'epub' THEN 0 ELSE 1 END, title
        """.trimIndent()
    )

    fun convertedEpub(pdfId: String): JSONObject? =
        query(
            "SELECT books.* FROM pdf_epub_editions JOIN books ON books.id = epubId WHERE pdfId = ?",
            arrayOf(pdfId),
        ).optJSONObject(0)

    fun originalPdf(epubId: String): JSONObject? =
        query(
            "SELECT books.* FROM pdf_epub_editions JOIN books ON books.id = pdfId WHERE epubId = ?",
            arrayOf(epubId),
        ).optJSONObject(0)

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
            val mode = state.optString("mode", "read")
            row.put("mode", mode)
            row.put("location", if (mode == "learn") "Walkthrough" else state.optString("locationLabel", "Beginning"))
            val sourceBook = book(row.getString("bookId"))
            row.put(
                "bookTitle",
                sourceBook.getString("title") + " · " + sourceBook.getString("format").uppercase(),
            )
        }
        return rows
    }

    @Synchronized
    fun createSession(bookId: String, learningCursor: JSONObject? = null): JSONObject {
        book(bookId)
        writableDatabase.beginTransaction()
        try {
            val count =
                query("SELECT count(*) AS n FROM sessions WHERE bookId = ?", arrayOf(bookId))
                    .getJSONObject(0)
                    .getInt("n")
            val id = UUID.randomUUID().toString()
            writableDatabase.insertOrThrow(
                "sessions",
                null,
                sessionValues(
                    id,
                    bookId,
                    "Session ${count + 1}",
                    book(bookId).getString("sourceVersion"),
                    learningCursor,
                ),
            )
            writableDatabase.setTransactionSuccessful()
            return session(id)
                .put("mode", if (learningCursor == null) "read" else "learn")
                .put("location", if (learningCursor == null) "Beginning" else "Walkthrough")
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

    private fun annotationScope(sessionId: String): Array<String> {
        val row = session(sessionId)
        val bookId = row.getString("bookId")
        val source = JSONObject(row.getString("state")).getString("sourceVersion")
        check(source == book(bookId).getString("sourceVersion")) {
            "This session requires its original edition."
        }
        return arrayOf(sessionId, bookId, source)
    }

    fun annotate(sessionId: String, locator: JSONObject, quote: String, note: String): String {
        val scope = annotationScope(sessionId)
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow(
            "annotations",
            null,
            ContentValues().apply {
                put("id", id)
                put("sessionId", sessionId)
                put("bookId", scope[1])
                put("sourceVersion", scope[2])
                put("locator", locator.toString())
                put("quote", quote)
                put("note", note)
                put("createdAt", System.currentTimeMillis())
            },
        )
        return id
    }

    fun annotations(sessionId: String) =
        query(
            "SELECT * FROM annotations WHERE sessionId = ? AND bookId = ? AND sourceVersion = ? ORDER BY createdAt, id",
            annotationScope(sessionId),
        )

    fun annotation(sessionId: String, id: String): JSONObject? =
        query(
                "SELECT * FROM annotations WHERE sessionId = ? AND bookId = ? AND sourceVersion = ? AND id = ?",
                annotationScope(sessionId) + id,
            )
            .optJSONObject(0)

    fun updateAnnotationNote(sessionId: String, id: String, expectedNote: String, note: String) {
        val changed =
            writableDatabase.update(
                "annotations",
                ContentValues().apply { put("note", note) },
                "sessionId = ? AND bookId = ? AND sourceVersion = ? AND id = ? AND note = ?",
                annotationScope(sessionId) + arrayOf(id, expectedNote),
            )
        check(changed == 1) {
            "This highlight changed or was removed. Reopen it to edit the latest note."
        }
    }

    @Synchronized
    fun deleteAnnotation(sessionId: String, id: String) {
        writableDatabase.beginTransaction()
        try {
            val row =
                annotation(sessionId, id)
                    ?: error("This highlight is no longer available in this session.")
            writableDatabase.delete(
                "annotations",
                "sessionId = ? AND id = ?",
                arrayOf(sessionId, id),
            )
            val session = session(sessionId)
            val state = JSONObject(session.getString("state"))
            if (
                state.optJSONObject("selection")?.optJSONObject("locator")?.toString() ==
                    row.getString("locator")
            ) {
                state.put("selection", JSONObject.NULL)
                updateSession(sessionId, session.getInt("revision"), state)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

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
