package com.readle.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** The same in-reader controls open from a saved highlight or the session's notes list. */
internal class AnnotationActions(
    private val activity: AppCompatActivity,
    private val panel: ReaderPanel,
    private val database: LibraryDatabase,
    private val book: JSONObject,
    private val sessionId: String,
    private val onDeleted: (JSONObject) -> Unit,
    private val onOpenSource: (JSONObject) -> Unit,
) {
    fun show(ids: List<String>) {
        val rows = ids.distinct().mapNotNull { database.annotation(sessionId, it) }
        if (rows.isEmpty()) return
        if (rows.size == 1) {
            showObject(rows.single())
            return
        }
        panel.show("Choose a highlight") {
            rows.forEach { row ->
                addView(panel.action(row.getString("quote").take(100)) { showObject(row) })
            }
        }
    }

    private fun showObject(row: JSONObject) {
        val quote = row.getString("quote")
        val note = row.getString("note")
        panel.show("Highlight") {
            addView(panel.text(quote.take(280)).apply { maxLines = 4 })
            if (note.isNotBlank()) addView(panel.text(note.take(280)).apply { maxLines = 3 })
            panel.addActions(
                this,
                (if (note.isBlank()) "Add note" else "Edit note") to { editNote(row) },
                "Open source" to
                    {
                        panel.close()
                        onOpenSource(row)
                    },
            )
            panel.addActions(
                this,
                "Copy quote" to
                    {
                        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Readle quote", quote))
                        panel.close()
                    },
                "Share quote" to
                    {
                        activity.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(
                                        Intent.EXTRA_TEXT,
                                        quote + "\n\n" + book.getString("title"),
                                    ),
                                "Share quote",
                            )
                        )
                    },
            )
            addView(panel.action("Delete highlight") { confirmDelete(row) })
        }
    }

    private fun editNote(row: JSONObject) {
        val input =
            EditText(activity).apply {
                hint = "Your note"
                minLines = 2
                maxLines = 5
                setText(row.getString("note"))
                setSelection(text.length)
            }
        panel.show("Edit highlight note") {
            addView(panel.text(row.getString("quote").take(160)).apply { maxLines = 2 })
            addView(input)
            panel.addActions(
                this,
                "Cancel" to panel::close,
                "Save" to
                    {
                        try {
                            database.updateAnnotationNote(
                                sessionId,
                                row.getString("id"),
                                row.getString("note"),
                                input.text.toString(),
                            )
                            panel.close()
                        } catch (e: IllegalStateException) {
                            input.error = e.message
                        }
                    },
            )
        }
    }

    private fun confirmDelete(row: JSONObject) {
        panel.show("Delete this highlight?") {
            val message = panel.text("Its attached note will also be removed.")
            addView(message)
            panel.addActions(
                this,
                "Keep" to panel::close,
                "Delete" to
                    {
                        try {
                            database.deleteAnnotation(sessionId, row.getString("id"))
                            onDeleted(row)
                            panel.close()
                        } catch (e: IllegalStateException) {
                            message.text = e.message
                        }
                    },
            )
        }
    }
}
