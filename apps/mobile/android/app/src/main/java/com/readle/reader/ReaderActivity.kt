package com.readle.reader

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.search.search
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
    private lateinit var panel: ReaderPanel
    private var findJob: Job? = null
    private var turningPage = false
    private var selectionKept = false
    private var selectionToResume: JSONObject? = null
    private var epub: EpubNavigatorFragment? = null
    private var publication: Publication? = null
    private var pdf: PdfRenderer? = null
    private var pdfText: PDDocument? = null
    private var pdfPage: PdfPageView? = null
    private var pdfScroll: ScrollView? = null
    private var pdfRenderedWidth = 0
    private var pdfRenderGeneration = 0
    private val resizePdf = Runnable {
        if (!isFinishing && pdf != null && content.width > 0 && content.width != pdfRenderedWidth)
            showPdf(pageIndex)
    }
    private val annotationActions by lazy {
        AnnotationActions(
            this,
            panel,
            database,
            book,
            sessionId,
            ::annotationDeleted,
            ::openAnnotationSource,
        )
    }
    private var pageIndex = 0
    private val referencePageIndex by lazy {
        intent.getIntExtra("referencePageIndex", -1).takeIf { it >= 0 }
    }
    private var epubScroll = true
    private var fixedLayout = false
    private var fontSize = 1.0
    private var selectedLocator: JSONObject? = null
    private var selectedQuote = ""

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
            epubScroll = state.getJSONObject("preferences").optBoolean("epubScroll", true)
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
            header.addView(button(if (referencePageIndex == null) "Library" else "Back to walkthrough") { finish() })
            header.addView(
                TextView(this).apply {
                    text =
                        session.getString("label") +
                            if (book.getString("format") == "pdf") " · PDF source" else ""
                    textSize = 16f
                    gravity = 17
                },
                LinearLayout.LayoutParams(0, 56, 1f),
            )
            header.addView(button("Notes") { showAnnotations() })
            root.addView(header)
            val isPdf = book.getString("format") == "pdf"
            val tools = LinearLayout(this)
            tools.addView(button("Contents") { showContents() })
            if (isPdf) tools.addView(button("Open original") { openOriginalPdf() })
            else {
                tools.addView(button("Text −") { resize(-0.15) })
                tools.addView(button("Text +") { resize(0.15) })
            }
            tools.addView(button("Find") { findInBook() })
            for (index in 0 until tools.childCount) {
                tools.getChildAt(index).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            root.addView(tools)
            content = FrameLayout(this).apply { id = View.generateViewId() }
            val workspace = FrameLayout(this)
            workspace.addView(content, FrameLayout.LayoutParams(-1, -1))
            root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
            panel = ReaderPanel(this, workspace) { findJob?.cancel() }
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (panel.visible) panel.close() else finish()
                    }
                },
            )
            val navigation = LinearLayout(this)
            navigation.addView(button("Previous") { turnPage(false) })
            navigation.addView(button("Next") { turnPage(true) })
            navigation.addView(button("Selection") { selectionActions() })
            if (!isPdf) navigation.addView(button("Layout") { chooseEpubLayout() })
            for (index in 0 until navigation.childCount) {
                navigation.getChildAt(index).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            root.addView(navigation)
            status =
                TextView(this).apply {
                    textSize = 12f
                    setPadding(16, 8, 16, 8)
                }
            root.addView(status)
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars =
                    insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                    )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            setContentView(root)
            root.post {
                WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = true
            }
            val locator = referencePageIndex?.let { JSONObject().put("pageIndex", it) }
                ?: state.optJSONObject("readingLocator")
            content.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (pdf != null && right - left != oldRight - oldLeft) {
                    content.removeCallbacks(resizePdf)
                    content.post(resizePdf)
                }
            }
            if (book.getString("format") == "epub") openEpub(locator)
            else content.post { openPdf(locator) }
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
            minHeight = (48 * resources.displayMetrics.density).toInt()
            minWidth = 0
            minimumWidth = 0
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
            fixedLayout = pub.metadata.presentation.layout == EpubLayout.FIXED
            supportFragmentManager.fragmentFactory =
                EpubNavigatorFactory(pub)
                    .createFragmentFactory(
                        initialLocator = initial?.let { parseEpubLocator(it) },
                        initialPreferences =
                            EpubPreferences(fontSize = fontSize, scroll = epubScroll),
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
            navigator.readlePageTurn = { forward -> turnPage(forward) }
            supportFragmentManager.beginTransaction().replace(content.id, navigator).commitNow()
            navigator.addDecorationListener(
                "readle",
                object : DecorableNavigator.Listener {
                    override fun onDecorationActivated(
                        event: DecorableNavigator.OnActivatedEvent
                    ): Boolean {
                        if (database.annotation(sessionId, event.decoration.id) == null)
                            return false
                        // Readium 3.1 delivers decoration activation on WebView's JavaBridge
                        // thread.
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                navigator.clearSelection()
                                annotationActions.show(listOf(event.decoration.id))
                            }
                        }
                        return true
                    }
                },
            )
            navigator.currentLocator.collect { locator ->
                database.mutateSession(sessionId) { state ->
                    state.put("readingLocator", locator.toJSON())
                    state.put(
                        "locationLabel",
                        locator.title ?: "EPUB position ${locator.locations.position ?: 1}",
                    )
                }
                resumeTurnSelection(navigator)
                updateEpubStatus(locator)
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
                    mode.finish()
                    if (item.itemId == 802) addNote() else saveAnnotation("")
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }

    // Readium 3.1's Locations.fromJSON consumes known fields from its input.
    // The reader's durable locators must remain intact after rendering or navigation.
    private fun parseEpubLocator(json: JSONObject): Locator? =
        Locator.fromJSON(JSONObject(json.toString()))

    private fun rememberSelection(locator: JSONObject, quote: String) {
        val range = locator.optJSONObject("locations")?.optJSONObject("readleRange")
        // Reading progression can move while this exact source range remains active.
        // Retain its original locator instead of moving the selection with the viewport.
        val anchor =
            selectedLocator?.takeIf {
                range != null &&
                    quote == selectedQuote &&
                    it.optString("href") == locator.optString("href") &&
                    it.optJSONObject("locations")?.optJSONObject("readleRange")?.toString() ==
                        range.toString()
            } ?: locator
        selectedLocator = anchor
        selectedQuote = quote
        database.mutateSession(sessionId) {
            it.put(
                "selection",
                JSONObject()
                    .put("sourceVersion", book.getString("sourceVersion"))
                    .put("format", book.getString("format"))
                    .put("locator", anchor)
                    .put("quote", quote),
            )
        }
    }

    private suspend fun applyEpubHighlights() {
        val rows = database.annotations(sessionId)
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
        selectedLocator
            ?.takeUnless { selectionIsSaved(rows) }
            ?.let {
                parseEpubLocator(it)?.let { anchor ->
                    decorations.add(
                        Decoration(
                            // An ID reused across chapters is treated as an update in
                            // the new resource, leaving a stale marker in the old one.
                            "selection:" + Base64.encodeToString(
                                anchor.href.toString().toByteArray(),
                                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                            ),
                            anchor,
                            Decoration.Style.Highlight(0xFFB5D4DF.toInt()),
                        )
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

    private fun pdfStatus() =
        "PDF reference · Page ${pageIndex + 1} of ${pdf?.pageCount ?: 0} · Fits screen width"

    private fun showPdf(index: Int) {
        val renderer = pdf ?: return
        if (index !in 0 until renderer.pageCount || content.width <= 0) return
        try {
            val prior =
                JSONObject(database.session(sessionId).getString("state"))
                    .optJSONObject("readingLocator")
            val viewportWidth = content.width
            val generation = ++pdfRenderGeneration
            val page = renderer.openPage(index)
            val width = page.width.toFloat()
            val height = page.height.toFloat()
            val pixels = viewportWidth.coerceIn(320, 2048)
            val bitmap =
                try {
                    Bitmap.createBitmap(
                            pixels,
                            (pixels * height / width).roundToInt().coerceAtMost(4096),
                            Bitmap.Config.ARGB_8888,
                        )
                        .also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                } finally {
                    page.close()
                }
            val extractor =
                PositionedText().apply {
                    startPage = index + 1
                    endPage = index + 1
                    sortByPosition = true
                }
            extractor.getText(pdfText)
            val annotations = database.annotations(sessionId)
            val marks = mutableListOf<PdfHighlight>()
            for (i in 0 until annotations.length()) {
                val row = annotations.getJSONObject(i)
                val rectangles = mutableListOf<RectF>()
                val locator = JSONObject(row.getString("locator"))
                val anchors = locator.optJSONArray("pages") ?: JSONArray().put(locator)
                for (j in 0 until anchors.length()) {
                    val anchor = anchors.getJSONObject(j)
                    if (anchor.optInt("pageIndex", -1) != index) continue
                    val quads = anchor.optJSONArray("quads") ?: continue
                    for (k in 0 until quads.length()) {
                        val q = quads.getJSONArray(k)
                        rectangles.add(
                            RectF(
                                q.getDouble(0).toFloat(),
                                q.getDouble(1).toFloat(),
                                q.getDouble(2).toFloat(),
                                q.getDouble(3).toFloat(),
                            )
                        )
                    }
                }
                if (rectangles.isNotEmpty())
                    marks.add(PdfHighlight(row.getString("id"), row.getString("quote"), rectangles))
            }
            // Keep one surface for this activity, including fold/resize events. Never stack page
            // views.
            val view =
                pdfPage
                    ?: PdfPageView(
                            this,
                            onAnnotationTap = { annotationActions.show(it) },
                            onClearSelection = { clearPdfSelection() },
                            onSelection = { locator, quote ->
                                rememberSelection(locator, quote)
                                status.text = "Selected: ${quote.take(100)}"
                            },
                        )
                        .also { pageView ->
                            pdfPage = pageView
                            pdfScroll = ScrollView(this).apply { addView(pageView) }
                            content.removeAllViews()
                            content.addView(pdfScroll)
                        }
            pageIndex = index
            pdfRenderedWidth = viewportWidth
            view.pageIndex = index
            view.layoutParams =
                FrameLayout.LayoutParams(
                    viewportWidth,
                    (viewportWidth * height / width).roundToInt(),
                )
            view.display(bitmap, width, height, extractor.glyphs, marks)
            val selectionPages = selectedLocator?.optJSONArray("pages")
            if (!selectionIsSaved(annotations))
                view.restoreSelection(
                    if (selectionPages == null) selectedLocator
                    else
                        (0 until selectionPages.length())
                            .map { selectionPages.getJSONObject(it) }
                            .find { it.optInt("pageIndex", -1) == index }
                )
            val offsetY = if (referencePageIndex == null && prior?.optInt("pageIndex", -1) == index) {
                prior.optDouble("offsetY", 0.0)
            } else 0.0
            if (referencePageIndex == null) {
                database.mutateSession(sessionId) {
                    it.put(
                        "readingLocator",
                        JSONObject()
                            .put("pageIndex", pageIndex)
                            .put("offsetX", 0)
                            .put("offsetY", offsetY),
                    )
                    it.put("locationLabel", "Page ${pageIndex + 1}")
                    it.getJSONObject("preferences").put("pdfZoom", 1.0)
                }
            }
            pdfScroll!!.setOnScrollChangeListener(null)
            view.post {
                if (generation == pdfRenderGeneration && !isFinishing && !isDestroyed) {
                    pdfScroll!!.scrollTo(0, (offsetY * view.height).roundToInt())
                    pdfScroll!!.setOnScrollChangeListener { _, _, _, _, _ -> persistPdfPosition() }
                    persistPdfPosition()
                }
            }
            status.text = pdfStatus()
        } catch (e: Exception) {
            showError(e.message ?: "Page unavailable")
        }
    }

    private fun persistPdfPosition() {
        if (referencePageIndex != null) return
        val page = pdfPage ?: return
        val offset = (pdfScroll?.scrollY ?: 0).toDouble() / page.height.coerceAtLeast(1)
        database.mutateSession(sessionId) {
            it.put(
                "readingLocator",
                JSONObject().put("pageIndex", pageIndex).put("offsetY", offset).put("offsetX", 0),
            )
            it.put("locationLabel", "Page ${pageIndex + 1}")
        }
    }

    private fun clearPdfSelection() {
        selectedLocator = null
        selectedQuote = ""
        pdfPage?.clearSelection()
        database.mutateSession(sessionId) { it.put("selection", JSONObject.NULL) }
        status.text = pdfStatus()
    }

    private fun resize(delta: Double) {
        fontSize = (fontSize + delta).coerceIn(0.7, 2.5)
        epub?.submitPreferences(EpubPreferences(fontSize = fontSize, scroll = epubScroll))
        database.mutateSession(sessionId) {
            it.getJSONObject("preferences").put("fontSize", fontSize)
        }
    }

    private fun updateEpubStatus(locator: Locator? = epub?.currentLocator?.value) {
        val title = locator?.title ?: "Reading"
        val layout = if (epubScroll && !fixedLayout) "Scroll" else "Pages"
        status.text = "$title · $layout" + if (selectionKept) " · Selection kept" else ""
    }

    private suspend fun captureSelection(): Boolean {
        val selection = epub?.currentSelection() ?: return false
        val quote = selection.locator.text.highlight?.takeIf { it.isNotBlank() } ?: return false
        rememberSelection(selection.locator.toJSON(), quote)
        return true
    }

    private suspend fun resumeTurnSelection(navigator: EpubNavigatorFragment) {
        val pending = selectionToResume ?: return
        if (navigator.resumeReadleSelection(pending) && selectionToResume === pending) {
            selectionToResume = null
        }
    }

    private fun turnPage(forward: Boolean) {
        val navigator = epub
        if (navigator == null) {
            if (pdf != null) showPdf(pageIndex + if (forward) 1 else -1)
            return
        }
        if (turningPage) return
        turningPage = true
        lifecycleScope.launch {
            try {
                val captured = captureSelection()
                if (captured) {
                    selectionKept = true
                    selectionToResume = selectedLocator
                }
                panel.close()
                if (navigator.readleTurnPage(forward, selectionToResume != null)) {
                    // The debounced reading locator might never change on a quick round trip.
                    resumeTurnSelection(navigator)
                } else if (captured) {
                    selectionToResume = null
                }
                if (captured) applyEpubHighlights()
                updateEpubStatus()
            } finally {
                turningPage = false
            }
        }
    }

    private fun chooseEpubLayout() {
        if (epub == null) return
        if (fixedLayout) {
            panel.show("Reading layout") {
                addView(
                    panel.text(
                        "This EPUB has fixed pages. Use Previous or Next to move through the book."
                    )
                )
            }
            return
        }
        panel.show("Reading layout") {
            addView(
                panel.text(
                    if (epubScroll) "Scroll up or down. Swipe again at a chapter edge to continue."
                    else "Swipe left or right to turn a page."
                )
            )
            addView(
                panel
                    .action("Continuous scroll") { setEpubLayout(true) }
                    .apply { isEnabled = !epubScroll }
            )
            addView(panel.action("Pages") { setEpubLayout(false) }.apply { isEnabled = epubScroll })
        }
    }

    private fun setEpubLayout(scroll: Boolean) = lifecycleScope.launch {
        selectionToResume = null
        if (captureSelection()) {
            selectionKept = true
            epub?.clearSelection()
        }
        epubScroll = scroll
        epub?.submitPreferences(EpubPreferences(fontSize = fontSize, scroll = epubScroll))
        database.mutateSession(sessionId) {
            it.getJSONObject("preferences").put("epubScroll", epubScroll)
        }
        panel.close()
        updateEpubStatus()
        applyEpubHighlights()
    }

    private fun openOriginalPdf() {
        val uri =
            FileProvider.getUriForFile(this, "$packageName.sources", File(book.getString("path")))
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(
                    this,
                    "Install a PDF viewer to open the original file.",
                    Toast.LENGTH_LONG,
                )
                .show()
            return
        }
        startActivity(Intent.createChooser(intent, "Open original PDF"))
    }

    private fun selectionIsSaved(rows: JSONArray): Boolean =
        selectedLocator != null &&
            (0 until rows.length()).any {
                rows.getJSONObject(it).getString("locator") == selectedLocator.toString()
            }

    private fun annotationDeleted(row: JSONObject) {
        if (selectedLocator?.toString() == row.getString("locator")) {
            selectionToResume = null
            selectedLocator = null
            selectedQuote = ""
            selectionKept = false
            database.mutateSession(sessionId) { it.put("selection", JSONObject.NULL) }
            if (epub != null) updateEpubStatus()
        }
        lifecycleScope.launch { if (epub != null) applyEpubHighlights() else showPdf(pageIndex) }
    }

    private fun openAnnotationSource(row: JSONObject) {
        selectionToResume = null
        rememberSelection(JSONObject(row.getString("locator")), row.getString("quote"))
        if (epub != null) parseEpubLocator(selectedLocator!!)?.let { epub?.go(it) }
        else showPdf(selectedLocator!!.getInt("pageIndex"))
    }

    private fun selectionActions() = lifecycleScope.launch {
        selectionToResume = null
        if (captureSelection()) epub?.clearSelection()
        if (selectedLocator == null) {
            Toast.makeText(this@ReaderActivity, "Long press a passage first.", Toast.LENGTH_SHORT)
                .show()
            return@launch
        }
        applyEpubHighlights()
        panel.show("Selected passage") {
            addView(panel.text(selectedQuote.take(280)).apply { maxLines = 3 })
            panel.addActions(this, "Highlight" to { saveAnnotation("") }, "Add note" to ::addNote)
            panel.addActions(
                this,
                "Copy" to
                    {
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                            ClipData.newPlainText("Readle quote", selectedQuote)
                        )
                        panel.close()
                    },
                "Open passage" to
                    {
                        panel.close()
                        selectedLocator?.let { locator ->
                            if (epub != null) parseEpubLocator(locator)?.let { epub?.go(it) }
                            else showPdf(locator.getInt("pageIndex"))
                        }
                    },
            )
            panel.addActions(
                this,
                "Look up" to ::lookUpSelection,
                "Search Google" to ::confirmWebSearch,
            )
            addView(
                panel.action("Clear selection") {
                    if (epub == null) clearPdfSelection()
                    else {
                        epub?.clearSelection()
                        selectedLocator = null
                        selectedQuote = ""
                        selectionKept = false
                        database.mutateSession(sessionId) { it.put("selection", JSONObject.NULL) }
                        lifecycleScope.launch {
                            applyEpubHighlights()
                            updateEpubStatus()
                        }
                    }
                    panel.close()
                }
            )
        }
    }

    private fun lookUpSelection() {
        val intent =
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, selectedQuote)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        try {
            startActivity(Intent.createChooser(intent, "Look up"))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(
                    this,
                    "Install a dictionary with Android text actions.",
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }

    private fun confirmWebSearch() {
        val quote = selectedQuote
        panel.show("Send selection to Google?") {
            addView(panel.text(quote.take(500)))
            panel.addActions(
                this,
                "Cancel" to panel::close,
                "Search" to
                    {
                        panel.close()
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://www.google.com/search?q=" +
                                        android.net.Uri.encode(quote)
                                ),
                            )
                        )
                    },
            )
        }
    }

    private fun addNote() {
        val input =
            EditText(this).apply {
                hint = "Your note"
                minLines = 2
                maxLines = 5
            }
        panel.show("Note on selection") {
            addView(panel.text(selectedQuote.take(160)).apply { maxLines = 2 })
            addView(input)
            panel.addActions(
                this,
                "Cancel" to panel::close,
                "Save" to { saveAnnotation(input.text.toString()) },
            )
        }
    }

    private fun saveAnnotation(note: String) {
        val locator = selectedLocator ?: return
        selectionToResume = null
        database.annotate(sessionId, locator, selectedQuote, note)
        selectionKept = false
        if (epub != null) updateEpubStatus()
        panel.close()
        lifecycleScope.launch { if (epub != null) applyEpubHighlights() else showPdf(pageIndex) }
        Toast.makeText(this, "Saved on this device", Toast.LENGTH_SHORT).show()
    }

    private fun showAnnotations() {
        val rows = database.annotations(sessionId)
        panel.show("This session’s highlights & notes") {
            if (rows.length() == 0) {
                addView(panel.text("Long press a passage to highlight or add a note."))
            } else {
                addView(
                    panel.action("Export") {
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, rows.toString(2)),
                                "Export annotations",
                            )
                        )
                    }
                )
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val quote = row.getString("quote").take(100)
                    val note = row.getString("note").take(80)
                    val label = if (note.isBlank()) quote else "$quote\n$note"
                    addView(
                        panel.action(label) { annotationActions.show(listOf(row.getString("id"))) }
                    )
                }
            }
        }
    }

    private fun showContents() {
        val pub = publication
        if (pub != null) {
            val links = pub.tableOfContents.ifEmpty { pub.readingOrder }
            AlertDialog.Builder(this)
                .setTitle("Contents")
                .setItems(links.map { it.title ?: it.href.toString() }.toTypedArray()) { _, which ->
                    selectionToResume = null
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
        if (book.getString("format") == "epub") {
            val pub = publication
            if (pub == null) {
                Toast.makeText(this, "The book is still opening.", Toast.LENGTH_SHORT).show()
            } else findInEpub(pub)
            return
        }
        if (pdfText == null) {
            Toast.makeText(this, "This PDF has no searchable text.", Toast.LENGTH_SHORT).show()
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

    private fun findInEpub(pub: Publication) {
        val input =
            EditText(this).apply {
                hint = "Word or phrase"
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                isSingleLine = true
            }
        val message = panel.text("Search this book on your device.", 14f)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun search() {
            val query = input.text.toString().trim()
            if (query.isEmpty()) {
                input.error = "Enter a word or phrase."
                return
            }
            findJob?.cancel()
            val keyboard =
                getSystemService(INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            keyboard.hideSoftInputFromWindow(input.windowToken, 0)
            results.removeAllViews()
            message.text = "Searching…"
            findJob = lifecycleScope.launch {
                try {
                    val iterator = pub.search(query) ?: error("This book has no searchable text.")
                    var count = 0
                    while (count < 100) {
                        val matches =
                            iterator.next().getOrElse { error("This book could not be searched.") }
                                ?: break
                        for (locator in matches.locators.take(100 - count)) {
                            count++
                            val before = locator.text.before.orEmpty().takeLast(45).trimStart()
                            val after = locator.text.after.orEmpty().take(55).trimEnd()
                            val excerpt =
                                (before + locator.text.highlight.orEmpty() + after)
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                            val chapter = locator.title ?: "Passage"
                            results.addView(
                                panel.action("$chapter\n$excerpt") {
                                    selectionToResume = null
                                    panel.close()
                                    lifecycleScope.launch {
                                        if (captureSelection()) {
                                            selectionKept = true
                                            epub?.clearSelection()
                                            applyEpubHighlights()
                                        }
                                        epub?.applyDecorations(
                                            listOf(
                                                Decoration(
                                                    "find",
                                                    locator,
                                                    Decoration.Style.Highlight(0xFFB5D4DF.toInt()),
                                                )
                                            ),
                                            "find",
                                        )
                                        epub?.go(locator, animated = false)
                                    }
                                }
                            )
                        }
                    }
                    message.text =
                        when (count) {
                            0 -> "No matches found."
                            1 -> "1 match"
                            100 -> "Showing the first 100 matches. Refine your search for more."
                            else -> "$count matches"
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message.text = e.message ?: "This book could not be searched."
                }
            }
        }
        panel.show("Find in book") {
            addView(input)
            addView(panel.action("Find text", ::search))
            addView(message)
            addView(results)
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) false
            else {
                search()
                true
            }
        }
    }

    override fun onStop() {
        if (pdf != null) persistPdfPosition()
        super.onStop()
    }

    override fun onDestroy() {
        if (::content.isInitialized) content.removeCallbacks(resizePdf)
        pdfRenderGeneration++
        pdfPage?.release()
        pdf?.close()
        pdfText?.close()
        publication?.close()
        super.onDestroy()
    }
}
