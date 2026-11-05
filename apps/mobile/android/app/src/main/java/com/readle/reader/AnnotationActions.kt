package com.readle.reader

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** One object-action flow for on-page highlights and the book's annotation list. */
class AnnotationActions(
    private val activity: AppCompatActivity,
    private val database: LibraryDatabase,
    private val book: JSONObject,
    private val sessionId: String,
    private val onChanged: (JSONObject, Boolean) -> Unit,
    private val onOpenSource: (JSONObject) -> Unit,
) {
    fun show(ids: List<String>) {
        val rows = ids.distinct().mapNotNull { database.annotation(sessionId, it) }
        if (rows.isEmpty()) return
        if (rows.size == 1) {
            showObject(rows.single())
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Choose a highlight")
            .setItems(
                rows
                    .map { it.getString("quote").take(80) + "\n" + it.getString("note").take(80) }
                    .toTypedArray()
            ) { _, which ->
                showObject(rows[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showObject(row: JSONObject) {
        val title =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding / 2)
                addView(
                    TextView(activity).apply {
                        text = "Highlight"
                        textSize = 20f
                        setTextColor(0xFF355742.toInt())
                    }
                )
                addView(
                    TextView(activity).apply {
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        text = row.getString("quote").take(240)
                        textSize = 15f
                        setPadding(0, padding / 2, 0, 0)
                    }
                )
                if (row.getString("note").isNotBlank())
                    addView(
                        TextView(activity).apply {
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            text = row.getString("note").take(240)
                            textSize = 14f
                            setPadding(0, padding / 2, 0, 0)
                        }
                    )
            }
        val noteAction = if (row.getString("note").isBlank()) "Add note" else "Edit note"
        AlertDialog.Builder(activity)
            .setCustomTitle(title)
            .setItems(
                arrayOf(noteAction, "Copy quote", "Share quote", "Open source", "Delete highlight")
            ) { _, which ->
                when (which) {
                    0 -> editNote(row)
                    1 ->
                        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(
                                ClipData.newPlainText("Readle quote", row.getString("quote"))
                            )
                    2 ->
                        activity.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(
                                        Intent.EXTRA_TEXT,
                                        row.getString("quote") + "\n\n" + book.getString("title"),
                                    ),
                                "Share quote",
                            )
                        )
                    3 -> onOpenSource(row)
                    4 -> confirmDelete(row)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun editNote(row: JSONObject) {
        val input =
            EditText(activity).apply {
                hint = "Your note"
                minLines = 3
                setText(row.getString("note"))
                setSelection(text.length)
            }
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle("Edit highlight note")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                database.updateAnnotationNote(
                    sessionId,
                    row.getString("id"),
                    row.getString("note"),
                    input.text.toString(),
                )
                onChanged(row, false)
                dialog.dismiss()
            } catch (e: IllegalStateException) {
                input.error = e.message
            }
        }
    }

    private fun confirmDelete(row: JSONObject) {
        AlertDialog.Builder(activity)
            .setTitle("Delete this highlight?")
            .setMessage("Its attached note will also be removed.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ ->
                try {
                    database.deleteAnnotation(sessionId, row.getString("id"))
                    onChanged(row, true)
                } catch (e: IllegalStateException) {
                    AlertDialog.Builder(activity)
                        .setMessage(e.message)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
            .show()
    }
}
