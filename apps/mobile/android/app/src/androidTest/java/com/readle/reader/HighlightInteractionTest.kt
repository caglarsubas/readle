package com.readle.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real reader, native touch dispatch, dialogs, and existing SQLite schema. */
@RunWith(AndroidJUnit4::class)
class HighlightInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: LibraryDatabase
    private lateinit var context: Context
    private lateinit var bookId: String
    private lateinit var sessionId: String
    private lateinit var annotationId: String
    private lateinit var sourceFile: File
    private lateinit var importId: String
    private var scenario: ActivityScenario<ReaderActivity>? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = LibraryDatabase.get(context)
    }

    private fun launchFixture(format: String, locator: JSONObject) {
        sourceFile = File.createTempFile("highlight-ui-", ".$format", context.cacheDir)
        val bytes = context.assets.open("fixtures/readle-sample.$format").use { it.readBytes() }
        if (format == "epub") {
            // Give this ZIP a valid, unique archive comment without altering publication content.
            val comment = UUID.randomUUID().toString().toByteArray()
            sourceFile.outputStream().use {
                it.write(bytes, 0, bytes.size - 2)
                it.write(comment.size)
                it.write(0)
                it.write(comment)
            }
        } else {
            sourceFile.writeBytes(bytes)
            sourceFile.appendText("\n% Isolated UI fixture ${UUID.randomUUID()}\n")
        }
        val importer = BookImporter(context, database)
        importId =
            importer.register(android.net.Uri.fromFile(sourceFile), "Highlight UI fixture.$format")
        importer.process(importId)
        bookId =
            database
                .query("SELECT bookId FROM imports WHERE id = ?", arrayOf(importId))
                .getJSONObject(0)
                .getString("bookId")
        sessionId = database.createSession(bookId).getString("id")
        annotationId =
            database.annotate(
                bookId,
                locator,
                "A stable anchor identifies the passage",
                "Original UI note",
            )
        scenario =
            ActivityScenario.launch(
                Intent(context, ReaderActivity::class.java).putExtra("sessionId", sessionId)
            )
        if (format == "pdf") waitForPdfPage()
    }

    private fun launchPdf() =
        launchFixture(
            "pdf",
            JSONObject()
                .put("pageIndex", 0)
                .put("quads", JSONArray().put(JSONArray(listOf(36, 116, 200, 132)))),
        )

    private fun waitForPdfPage() = waitUntil {
        var ready = false
        scenario!!.onActivity {
            ready =
                findView(it.window.decorView, PdfPageView::class.java)?.let { page ->
                    page.width > 0
                } == true
        }
        ready
    }

    @After
    fun cleanup() {
        scenario?.close()
        if (::bookId.isInitialized) {
            val stored = File(database.book(bookId).getString("path"))
            database.writableDatabase.delete("annotations", "bookId = ?", arrayOf(bookId))
            database.writableDatabase.delete("sessions", "bookId = ?", arrayOf(bookId))
            database.writableDatabase.delete("books", "id = ?", arrayOf(bookId))
            stored.delete()
        }
        if (::importId.isInitialized)
            database.writableDatabase.delete("imports", "id = ?", arrayOf(importId))
        if (::sourceFile.isInitialized) sourceFile.delete()
    }

    @Test
    fun tapHighlightAfterZoomEditsAndDeletesTheSameObject() {
        launchPdf()
        val originalLocator = database.annotation(bookId, annotationId)!!.getString("locator")
        doubleTap()
        waitUntil { zoom() > 1.5 }
        tapHighlight()
        click("Edit note")
        val editor = waitForNode { it.className?.toString() == "android.widget.EditText" }
        editor.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "Edited from object",
                )
            },
        )
        click("Save")
        waitUntil {
            database.annotation(bookId, annotationId)?.getString("note") == "Edited from object"
        }
        assertEquals(
            originalLocator,
            database.annotation(bookId, annotationId)!!.getString("locator"),
        )
        assertEquals(1, database.annotations(bookId).length())
        tapHighlight()
        click("Delete highlight")
        click("Delete")
        waitUntil { database.annotation(bookId, annotationId) == null }
        assertEquals(
            0,
            JSONObject(database.session(sessionId).getString("state"))
                .getJSONObject("readingLocator")
                .getInt("pageIndex"),
        )
    }

    @Test
    fun pinchCommitsZoomAndAccessibilityCanActivateSavedObject() {
        launchPdf()
        scenario!!.onActivity { activity ->
            val viewport = findView(activity.window.decorView, PdfViewport::class.java)!!
            val time = SystemClock.uptimeMillis()
            fun event(action: Int, elapsed: Long, positions: List<Pair<Float, Float>>) {
                val properties =
                    positions.indices
                        .map { index ->
                            MotionEvent.PointerProperties().apply {
                                id = index
                                toolType = MotionEvent.TOOL_TYPE_FINGER
                            }
                        }
                        .toTypedArray()
                val coordinates =
                    positions
                        .map { (x, y) ->
                            MotionEvent.PointerCoords().apply {
                                this.x = x
                                this.y = y
                                pressure = 1f
                                size = 1f
                            }
                        }
                        .toTypedArray()
                val input =
                    MotionEvent.obtain(
                        time,
                        time + elapsed,
                        action,
                        positions.size,
                        properties,
                        coordinates,
                        0,
                        0,
                        1f,
                        1f,
                        0,
                        0,
                        android.view.InputDevice.SOURCE_TOUCHSCREEN,
                        0,
                    )
                viewport.dispatchTouchEvent(input)
                input.recycle()
            }
            event(MotionEvent.ACTION_DOWN, 0, listOf(100f to 300f))
            event(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                30,
                listOf(100f to 300f, 400f to 300f),
            )
            event(MotionEvent.ACTION_MOVE, 70, listOf(90f to 300f, 440f to 300f))
            event(MotionEvent.ACTION_MOVE, 110, listOf(80f to 300f, 530f to 300f))
            event(MotionEvent.ACTION_MOVE, 150, listOf(70f to 300f, 620f to 300f))
            event(
                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                190,
                listOf(70f to 300f, 620f to 300f),
            )
            event(MotionEvent.ACTION_UP, 210, listOf(70f to 300f))
        }
        waitUntil { zoom() > 1.1 }
        scenario!!.onActivity { activity ->
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            val info = AccessibilityNodeInfo.obtain()
            page.onInitializeAccessibilityNodeInfo(info)
            val action =
                info.actionList.single {
                    it.label?.toString()?.startsWith("Highlight A stable") == true
                }
            assertTrue(page.performAccessibilityAction(action.id, null))
            info.recycle()
        }
        waitForNode { it.text?.toString() == "Copy quote" }
        assertEquals(1, database.annotations(bookId).length())
    }

    @Test
    fun epubHighlightCanBeTappedAfterReflowAndReopen() {
        launchFixture(
            "epub",
            JSONObject()
                .put("href", "EPUB/c0.xhtml")
                .put("type", "application/xhtml+xml")
                .put("locations", JSONObject().put("cssSelector", "html > body > p:nth-of-type(1)"))
                .put(
                    "text",
                    JSONObject().put("highlight", "A stable anchor identifies the passage"),
                ),
        )
        val originalLocator = database.annotation(bookId, annotationId)!!.getString("locator")
        tapEpubHighlight()
        click("Edit note")
        click("Cancel")
        val originalFontSize = epubFontSize()
        click("Text +")
        waitUntil { epubFontSize() > originalFontSize }
        waitUntil {
            JSONObject(database.session(sessionId).getString("state"))
                .getJSONObject("preferences")
                .getDouble("fontSize") > 1.0
        }
        tapEpubHighlight()
        click("Copy quote")
        scenario!!.close()
        scenario =
            ActivityScenario.launch(
                Intent(context, ReaderActivity::class.java).putExtra("sessionId", sessionId)
            )
        tapEpubHighlight()
        click("Delete highlight")
        click("Keep")
        assertEquals(
            originalLocator,
            database.annotation(bookId, annotationId)!!.getString("locator"),
        )
        assertEquals(1, database.annotations(bookId).length())
    }

    private fun epubFontSize(): Double {
        val completed = CountDownLatch(1)
        var size = 0.0
        scenario!!.onActivity { activity ->
            val web = findVisibleWebView(activity.window.decorView)
            if (web == null) completed.countDown()
            else
                web.evaluateJavascript(
                    "parseFloat(getComputedStyle(document.querySelector('p')).fontSize)"
                ) {
                    size = it.toDoubleOrNull() ?: 0.0
                    completed.countDown()
                }
        }
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        return size
    }

    private fun tapEpubHighlight() {
        var point: Pair<Float, Float>? = null
        var priorPoint: Pair<Float, Float>? = null
        var stableSince = SystemClock.uptimeMillis()
        waitUntil {
            val completed = CountDownLatch(1)
            scenario!!.onActivity { activity ->
                val web = findVisibleWebView(activity.window.decorView)
                if (web == null) completed.countDown()
                else
                    web.evaluateJavascript(
                        """
                        (function() {
                            const group = document.querySelector('[data-group="readle"]');
                            if (!group) return null;
                            const rects = Array.from(group.querySelectorAll('[data-style] > *'))
                                .map(e => e.getBoundingClientRect());
                            const r = rects.find(r => r.width > 0 && r.height > 0 && r.left >= 0 && r.top >= 0
                                && r.right <= innerWidth && r.bottom <= innerHeight);
                            return r ? [r.left + r.width / 2, r.top + r.height / 2] : null;
                        })()
                        """
                            .trimIndent()
                    ) { value ->
                        if (value != "null") {
                            val xy = JSONArray(value)
                            val origin = IntArray(2)
                            web.getLocationOnScreen(origin)
                            point =
                                origin[0] +
                                    xy.getDouble(0).toFloat() *
                                        web.resources.displayMetrics.density to
                                    origin[1] +
                                        xy.getDouble(1).toFloat() *
                                            web.resources.displayMetrics.density
                        }
                        completed.countDown()
                    }
            }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            if (point == null || point != priorPoint) {
                priorPoint = point
                stableSince = SystemClock.uptimeMillis()
                false
            } else SystemClock.uptimeMillis() - stableSince >= 150
        }
        val time = SystemClock.uptimeMillis()
        for ((action, elapsed) in
            listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 30L)) {
            val event =
                MotionEvent.obtain(time, time + elapsed, action, point!!.first, point!!.second, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        try {
            waitForNode { it.text?.toString() == "Edit note" }
        } catch (e: AssertionError) {
            File(context.getExternalFilesDir(null), "epub-tap-failure.png").outputStream().use {
                instrumentation.uiAutomation
                    .takeScreenshot()
                    ?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            android.util.Log.i("ReadleTest", "Failed EPUB tap at $point")
            throw e
        }
    }

    private fun zoom() =
        JSONObject(database.session(sessionId).getString("state"))
            .getJSONObject("preferences")
            .getDouble("pdfZoom")

    private fun doubleTap() {
        scenario!!.onActivity { activity ->
            val viewport = findView(activity.window.decorView, PdfViewport::class.java)!!
            val time = SystemClock.uptimeMillis()
            for ((action, elapsed) in
                listOf(
                    MotionEvent.ACTION_DOWN to 0L,
                    MotionEvent.ACTION_UP to 30L,
                    MotionEvent.ACTION_DOWN to 120L,
                    MotionEvent.ACTION_UP to 150L,
                )) {
                val event = MotionEvent.obtain(time, time + elapsed, action, 350f, 350f, 0)
                viewport.dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }

    private fun tapHighlight() {
        waitForPdfPage()
        instrumentation.waitForIdleSync()
        var point: Pair<Float, Float>? = null
        scenario!!.onActivity { activity ->
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            val origin = IntArray(2)
            page.getLocationOnScreen(origin)
            point = origin[0] + page.width * 100f / 432f to origin[1] + page.height * 124f / 648f
        }
        val time = SystemClock.uptimeMillis()
        for ((action, elapsed) in
            listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 30L)) {
            val event =
                MotionEvent.obtain(time, time + elapsed, action, point!!.first, point!!.second, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        waitForNode { it.text?.toString() == "Edit note" }
    }

    private fun click(label: String) {
        instrumentation.waitForIdleSync()
        waitUntil {
            var node =
                findNode(instrumentation.uiAutomation.rootInActiveWindow) {
                    it.text?.toString()?.equals(label, ignoreCase = true) == true
                }
            while (node != null && !node.isClickable && node.parent != null) node = node.parent
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        waitUntil {
            found = findNode(instrumentation.uiAutomation.rootInActiveWindow, predicate)
            found != null
        }
        return found!!
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) findNode(node.getChild(index), predicate)?.let {
            return it
        }
        return null
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!check()) {
            if (SystemClock.uptimeMillis() >= deadline) fail("Reader condition did not become true")
            SystemClock.sleep(50)
        }
    }

    private fun findVisibleWebView(view: View): WebView? {
        if (view is WebView && view.isShown && view.getGlobalVisibleRect(android.graphics.Rect()))
            return view
        if (view is ViewGroup)
            for (index in 0 until view.childCount) {
                findVisibleWebView(view.getChildAt(index))?.let {
                    return it
                }
            }
        return null
    }

    private fun <T : View> findView(view: View, type: Class<T>): T? {
        if (type.isInstance(view)) return type.cast(view)
        if (view is ViewGroup)
            for (index in 0 until view.childCount) findView(view.getChildAt(index), type)?.let {
                return it
            }
        return null
    }
}
