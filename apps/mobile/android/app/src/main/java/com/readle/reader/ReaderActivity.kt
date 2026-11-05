package com.readle.reader

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity() {
    private val database by lazy { LibraryDatabase.get(this) }
    private lateinit var sessionId: String
    private lateinit var book: JSONObject
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private var epub: EpubNavigatorFragment? = null
    private var publication: Publication? = null
    private var pdf: PdfRenderer? = null
    private var pdfText: PDDocument? = null
    private var pdfPage: PdfPageView? = null
    private var pdfScroll: ScrollView? = null
    private var pageIndex = 0
    private var zoom = 1.0
    private var fontSize = 1.0
    private var selectedLocator: JSONObject? = null
    private var selectedQuote = ""
    private var selectionPrefix: JSONObject? = null
    private var selectionPrefixQuote = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        // Rebuild the navigator from the durable locator after process death.
        super.onCreate(null)
        sessionId =
            intent.getStringExtra("sessionId")
                ?: run {
                    finish()
                    return
                }
        try {
            val session = database.session(sessionId)
            book = database.book(session.getString("bookId"))
            val state = JSONObject(session.getString("state"))
            check(state.getString("sourceVersion") == book.getString("sourceVersion")) {
                "This session requires its original edition."
            }
            zoom = state.getJSONObject("preferences").optDouble("pdfZoom", 1.0)
            fontSize = state.getJSONObject("preferences").optDouble("fontSize", 1.0)
            selectedLocator = state.optJSONObject("selection")?.optJSONObject("locator")
            selectedQuote = state.optJSONObject("selection")?.optString("quote", "") ?: ""
            val root =
                LinearLayout(this).apply {
                    orientation = 1
                    setBackgroundColor(0xFFF5F4F0.toInt())
                    setPadding(0, 32, 0, 24)
                }
            val header = LinearLayout(this)
            header.addView(button("Library") { finish() })
            header.addView(
                TextView(this).apply {
                    text = session.getString("label")
                    textSize = 16f
                    gravity = 17
                },
                LinearLayout.LayoutParams(0, 56, 1f),
            )
            header.addView(button("Notes") { showAnnotations() })
            root.addView(header)
            val tools = LinearLayout(this)
            tools.addView(button("Contents") { showContents() })
            tools.addView(button("A− / −") { resize(-0.15) })
            tools.addView(button("A+ / +") { resize(0.15) })
            tools.addView(button("Find") { findInBook() })
            root.addView(tools)
            content = FrameLayout(this).apply { id = View.generateViewId() }
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            val navigation = LinearLayout(this)
            navigation.addView(button("Previous") { epub?.goBackward() ?: showPdf(pageIndex - 1) })
            navigation.addView(button("Next") { epub?.goForward() ?: showPdf(pageIndex + 1) })
            navigation.addView(button("Selection") { selectionActions() })
            root.addView(navigation)
            status =
                TextView(this).apply {
                    textSize = 12f
                    setPadding(16, 8, 16, 8)
                }
            root.addView(status)
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            setContentView(root)
            root.post {
                WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = true
            }
            val locator = state.optJSONObject("readingLocator")
            if (book.getString("format") == "epub") openEpub(locator) else openPdf(locator)
        } catch (e: Exception) {
            showError(e.message ?: "Source unavailable")
        }
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            elevation = 0f
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE1E7DB.toInt())
            setTextColor(0xFF355742.toInt())
            minHeight = 48
            setOnClickListener { action() }
        }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Reader needs attention")
            .setMessage(message)
            .setPositiveButton("Library") { _, _ -> finish() }
            .show()
    }

    private fun openEpub(initial: JSONObject?) = lifecycleScope.launch {
        try {
            val client = DefaultHttpClient()
            val retriever = AssetRetriever(contentResolver, client)
            val opener =
                PublicationOpener(
                    DefaultPublicationParser(
                        this@ReaderActivity,
                        httpClient = client,
                        assetRetriever = retriever,
                        pdfFactory = null,
                    )
                )
            val asset =
                retriever.retrieve(File(book.getString("path")).toUrl()).getOrElse {
                    throw IllegalStateException(it.toString())
                }
            val pub =
                opener.open(asset, allowUserInteraction = false).getOrElse {
                    throw IllegalStateException(it.toString())
                }
            publication = pub
            supportFragmentManager.fragmentFactory =
                EpubNavigatorFactory(pub)
                    .createFragmentFactory(
                        initialLocator = initial?.let { Locator.fromJSON(it) },
                        initialPreferences = EpubPreferences(fontSize = fontSize, scroll = false),
                        configuration =
                            EpubNavigatorFragment.Configuration(
                                selectionActionModeCallback = selectionCallback
                            ),
                    )
            val navigator =
                supportFragmentManager.fragmentFactory.instantiate(
                    classLoader,
                    EpubNavigatorFragment::class.java.name,
                ) as EpubNavigatorFragment
            epub = navigator
            supportFragmentManager.beginTransaction().replace(content.id, navigator).commitNow()
            navigator.currentLocator.collect { locator ->
                database.mutateSession(sessionId) { state ->
                    state.put("readingLocator", locator.toJSON())
                    state.put(
                        "locationLabel",
                        locator.title ?: "EPUB position ${locator.locations.position ?: 1}",
                    )
                }
                status.text =
                    "${book.getString("title")} · ${locator.title ?: "Reading"} · Saved on device"
                applyEpubHighlights()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isFinishing) showError(e.message ?: "Cannot open EPUB")
        }
    }

    private val selectionCallback =
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, 801, 0, "Highlight")
                menu.add(0, 802, 1, "Note")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId !in listOf(801, 802)) return false
                lifecycleScope.launch {
                    val selection = epub?.currentSelection() ?: return@launch
                    rememberSelection(
                        selection.locator.toJSON(),
                        selection.locator.text.highlight ?: "",
                    )
                    if (item.itemId == 802) addNote() else saveAnnotation("")
                    mode.finish()
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }

    private fun rememberSelection(locator: JSONObject, quote: String) {
        selectedLocator = locator
        selectedQuote = quote
        database.mutateSession(sessionId) {
            it.put(
                "selection",
                JSONObject()
                    .put("sourceVersion", book.getString("sourceVersion"))
                    .put("format", book.getString("format"))
                    .put("locator", locator)
                    .put("quote", quote),
            )
        }
    }

    private suspend fun applyEpubHighlights() {
        val rows = database.annotations(book.getString("id"))
        val decorations =
            (0 until rows.length())
                .mapNotNull { index ->
                    val row = rows.getJSONObject(index)
                    Locator.fromJSON(JSONObject(row.getString("locator")))?.let {
                        Decoration(
                            row.getString("id"),
                            it,
                            Decoration.Style.Highlight(0xFFE2C04F.toInt()),
                        )
                    }
                }
                .toMutableList()
        selectedLocator?.let {
            Locator.fromJSON(it)?.let { anchor ->
                decorations.add(
                    Decoration("selection", anchor, Decoration.Style.Highlight(0xFFB5D4DF.toInt()))
                )
            }
        }
        epub?.applyDecorations(decorations, "readle")
    }

    private fun openPdf(initial: JSONObject?) {
        try {
            val file = File(book.getString("path"))
            pdf = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            PDFBoxResourceLoader.init(applicationContext)
            pdfText = PDDocument.load(file)
            showPdf(initial?.optInt("pageIndex", 0) ?: 0)
        } catch (e: Exception) {
            showError(e.message ?: "Cannot open PDF")
        }
    }

    private fun showPdf(index: Int) {
        val renderer = pdf ?: return
        if (index !in 0 until renderer.pageCount) return
        try {
            val prior =
                JSONObject(database.session(sessionId).getString("state"))
                    .optJSONObject("readingLocator")
            pageIndex = index
            pdfPage?.release()
            val page = renderer.openPage(index)
            val width = page.width.toFloat()
            val height = page.height.toFloat()
            val pixels = (resources.displayMetrics.widthPixels * zoom).toInt().coerceIn(320, 2048)
            val bitmap =
                Bitmap.createBitmap(
                    pixels,
                    (pixels * height / width).roundToInt().coerceAtMost(4096),
                    Bitmap.Config.ARGB_8888,
                )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            val extractor =
                PositionedText().apply {
                    startPage = index + 1
                    endPage = index + 1
                    sortByPosition = true
                }
            extractor.getText(pdfText)
            val marks = mutableListOf<RectF>()
            val annotations = database.annotations(book.getString("id"))
            for (i in 0 until annotations.length()) {
                val locator = JSONObject(annotations.getJSONObject(i).getString("locator"))
                val anchors = locator.optJSONArray("pages") ?: JSONArray().put(locator)
                for (j in 0 until anchors.length()) {
                    val anchor = anchors.getJSONObject(j)
                    if (anchor.optInt("pageIndex", -1) != index) continue
                    val quads = anchor.optJSONArray("quads") ?: continue
                    for (k in 0 until quads.length()) {
                        val q = quads.getJSONArray(k)
                        marks.add(
                            RectF(
                                q.getDouble(0).toFloat(),
                                q.getDouble(1).toFloat(),
                                q.getDouble(2).toFloat(),
                                q.getDouble(3).toFloat(),
                            )
                        )
                    }
                }
            }
            val view =
                PdfPageView(this) { locator, quote ->
                    val prefix = selectionPrefix
                    if (prefix != null)
                        rememberSelection(
                            JSONObject()
                                .put("pages", JSONArray().put(prefix).put(locator))
                                .put("pageIndex", prefix.getInt("pageIndex")),
                            "$selectionPrefixQuote $quote",
                        )
                    else rememberSelection(locator, quote)
                    status.text = "Selected: ${selectedQuote.take(100)}"
                }
            view.extendFromPageStart = selectionPrefix != null
            view.pageIndex = index
            view.display(bitmap, width, height, extractor.glyphs, marks)
            val visualWidth = (resources.displayMetrics.widthPixels * zoom).roundToInt()
            view.layoutParams =
                android.view.ViewGroup.LayoutParams(
                    visualWidth,
                    (visualWidth * height / width).roundToInt(),
                )
            val vertical = ScrollView(this).apply { addView(view) }
            val horizontal = HorizontalScrollView(this).apply { addView(vertical) }
            content.removeAllViews()
            content.addView(horizontal)
            pdfPage = view
            pdfScroll = vertical
            if (prior?.optInt("pageIndex", -1) == index)
                vertical.post {
                    vertical.scrollTo(0, (prior.optDouble("offsetY", 0.0) * view.height).toInt())
                }
            vertical.setOnScrollChangeListener { _, _, _, _, _ -> persistPdfPosition() }
            val selectionPages = selectedLocator?.optJSONArray("pages")
            view.restoreSelection(
                if (selectionPages == null) selectedLocator
                else
                    (0 until selectionPages.length())
                        .map { selectionPages.getJSONObject(it) }
                        .find { it.optInt("pageIndex", -1) == index }
            )
            persistPdfPosition()
            status.text = "Page ${index + 1} of ${renderer.pageCount} · Saved on device"
        } catch (e: Exception) {
            showError(e.message ?: "Page unavailable")
        }
    }

    private fun persistPdfPosition() {
        val offset = (pdfScroll?.scrollY ?: 0).toDouble() / (pdfPage?.height ?: 1).coerceAtLeast(1)
        database.mutateSession(sessionId) {
            it.put(
                "readingLocator",
                JSONObject().put("pageIndex", pageIndex).put("offsetY", offset),
            )
            it.put("locationLabel", "Page ${pageIndex + 1}")
        }
    }

    private fun resize(delta: Double) {
        if (epub != null) {
            fontSize = (fontSize + delta).coerceIn(0.7, 2.5)
            epub?.submitPreferences(EpubPreferences(fontSize = fontSize))
        } else {
            zoom = (zoom + delta).coerceIn(0.5, 3.0)
            showPdf(pageIndex)
        }
        database.mutateSession(sessionId) {
            it.getJSONObject("preferences").put("fontSize", fontSize).put("pdfZoom", zoom)
        }
    }

    private fun selectionActions() = lifecycleScope.launch {
        epub?.currentSelection()?.let {
            rememberSelection(it.locator.toJSON(), it.locator.text.highlight ?: "")
        }
        if (selectedLocator == null) {
            Toast.makeText(this@ReaderActivity, "Long press a passage first.", Toast.LENGTH_SHORT)
                .show()
            return@launch
        }
        val choices = mutableListOf("Highlight", "Add note", "Copy", "Look up", "Search Google")
        if (pdf != null && pageIndex + 1 < pdf!!.pageCount && selectionPrefix == null)
            choices.add("Extend to next page")
        AlertDialog.Builder(this@ReaderActivity)
            .setTitle(selectedQuote.take(100))
            .setItems(choices.toTypedArray()) { _, which ->
                when (which) {
                    0 -> saveAnnotation("")
                    1 -> addNote()
                    2 ->
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                            ClipData.newPlainText("Readle quote", selectedQuote)
                        )
                    3 -> {
                        val intent =
                            Intent(Intent.ACTION_PROCESS_TEXT)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_PROCESS_TEXT, selectedQuote)
                                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                        try {
                            startActivity(Intent.createChooser(intent, "Look up"))
                        } catch (_: Exception) {
                            Toast.makeText(
                                    this@ReaderActivity,
                                    "Install a dictionary with Android text actions.",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                    }
                    4 ->
                        AlertDialog.Builder(this@ReaderActivity)
                            .setTitle("Send selection to Google?")
                            .setMessage(selectedQuote.take(500))
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Search") { _, _ ->
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            "https://www.google.com/search?q=" +
                                                android.net.Uri.encode(selectedQuote)
                                        ),
                                    )
                                )
                            }
                            .show()
                    5 -> {
                        pdfPage?.selectToEnd()
                        selectionPrefix = selectedLocator
                        selectionPrefixQuote = selectedQuote
                        showPdf(pageIndex + 1)
                        Toast.makeText(
                                this@ReaderActivity,
                                "Select the ending passage on this page.",
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                }
            }
            .show()
    }

    private fun addNote() {
        val input =
            EditText(this).apply {
                hint = "Your note"
                minLines = 3
            }
        AlertDialog.Builder(this)
            .setTitle("Note on selection")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> saveAnnotation(input.text.toString()) }
            .show()
    }

    private fun saveAnnotation(note: String) {
        val locator = selectedLocator ?: return
        database.annotate(book.getString("id"), locator, selectedQuote, note)
        selectionPrefix = null
        lifecycleScope.launch { if (epub != null) applyEpubHighlights() else showPdf(pageIndex) }
        Toast.makeText(this, "Saved on this device", Toast.LENGTH_SHORT).show()
    }

    private fun showAnnotations() {
        val rows = database.annotations(book.getString("id"))
        if (rows.length() == 0) {
            Toast.makeText(
                    this,
                    "Long press a passage to highlight or add a note.",
                    Toast.LENGTH_LONG,
                )
                .show()
            return
        }
        val labels =
            (0 until rows.length()).map {
                rows.getJSONObject(it).let { row ->
                    row.getString("quote").take(100) + "\n" + row.getString("note")
                }
            }
        AlertDialog.Builder(this)
            .setTitle("Highlights & notes")
            .setItems(labels.toTypedArray()) { _, which ->
                val row = rows.getJSONObject(which)
                rememberSelection(JSONObject(row.getString("locator")), row.getString("quote"))
                if (epub != null) Locator.fromJSON(selectedLocator!!)?.let { epub?.go(it) }
                else showPdf(selectedLocator!!.getInt("pageIndex"))
            }
            .setNeutralButton("Export") { _, _ ->
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, rows.toString(2)),
                        "Export annotations",
                    )
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showContents() {
        val pub = publication
        if (pub != null) {
            val links = pub.tableOfContents.ifEmpty { pub.readingOrder }
            AlertDialog.Builder(this)
                .setTitle("Contents")
                .setItems(links.map { it.title ?: it.href.toString() }.toTypedArray()) { _, which ->
                    epub?.go(links[which])
                }
                .show()
        } else {
            val input =
                EditText(this).apply {
                    hint = "Page number"
                    inputType = 2
                }
            AlertDialog.Builder(this)
                .setTitle("Go to page")
                .setView(input)
                .setPositiveButton("Open") { _, _ ->
                    input.text.toString().toIntOrNull()?.let { showPdf(it - 1) }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun findInBook() {
        if (pdfText == null) {
            Toast.makeText(this, "EPUB text search is pending this spike.", Toast.LENGTH_LONG)
                .show()
            return
        }
        val input = EditText(this).apply { hint = "Exact phrase" }
        AlertDialog.Builder(this)
            .setTitle("Find in PDF")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    val matches =
                        (0 until pdfText!!.numberOfPages).filter { index ->
                            PositionedText()
                                .apply {
                                    startPage = index + 1
                                    endPage = index + 1
                                }
                                .getText(pdfText)
                                .contains(query, true)
                        }
                    AlertDialog.Builder(this)
                        .setTitle("${matches.size} matching pages")
                        .setItems(matches.map { "Page ${it + 1}" }.toTypedArray()) { _, which ->
                            showPdf(matches[which])
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (pdf != null) showPdf(pageIndex)
    }

    override fun onStop() {
        if (pdf != null) persistPdfPosition()
        super.onStop()
    }

    override fun onDestroy() {
        pdfPage?.release()
        pdf?.close()
        pdfText?.close()
        publication?.close()
        super.onDestroy()
    }
}
