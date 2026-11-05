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
                sessionId,
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
    fun pdfHighlightEditsAndDeletesTheSameSessionObject() {
        launchPdf()
        val originalLocator = database.annotation(sessionId, annotationId)!!.getString("locator")
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
            database.annotation(sessionId, annotationId)?.getString("note") == "Edited from object"
        }
        assertEquals(
            originalLocator,
            database.annotation(sessionId, annotationId)!!.getString("locator"),
        )
        assertEquals(1, database.annotations(sessionId).length())
        tapHighlight()
        click("Delete highlight")
        click("Delete")
        waitUntil { database.annotation(sessionId, annotationId) == null }
        assertEquals(
            0,
            JSONObject(database.session(sessionId).getString("state"))
                .getJSONObject("readingLocator")
                .getInt("pageIndex"),
        )
    }

    @Test
    fun pdfSelectionClearsOnOutsideTapAndFitsOneSurfaceAfterResize() {
        launchPdf()
        var pageIdentity: PdfPageView? = null
        scenario!!.onActivity { activity ->
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            pageIdentity = page
            assertEquals((page.parent as View).width, page.width)
            val time = SystemClock.uptimeMillis()
            val input =
                MotionEvent.obtain(
                    time,
                    time,
                    MotionEvent.ACTION_DOWN,
                    page.width * 100f / 432f,
                    page.height * 124f / 648f,
                    0,
                )
            page.dispatchTouchEvent(input)
            input.recycle()
        }
        SystemClock.sleep(650)
        scenario!!.onActivity { activity ->
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            val time = SystemClock.uptimeMillis()
            val input =
                MotionEvent.obtain(
                    time - 650,
                    time,
                    MotionEvent.ACTION_UP,
                    page.width * 100f / 432f,
                    page.height * 124f / 648f,
                    0,
                )
            page.dispatchTouchEvent(input)
            input.recycle()
        }
        waitUntil {
            !JSONObject(database.session(sessionId).getString("state")).isNull("selection")
        }
        scenario!!.onActivity { activity ->
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            val time = SystemClock.uptimeMillis()
            for ((action, elapsed) in
                listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 30L)) {
                val input =
                    MotionEvent.obtain(time, time + elapsed, action, page.width * .9f, 20f, 0)
                page.dispatchTouchEvent(input)
                input.recycle()
            }
        }
        waitUntil { JSONObject(database.session(sessionId).getString("state")).isNull("selection") }
        // Exercise the same content-width callback used by a fold/unfold without depending on an
        // AVD.
        scenario!!.onActivity { activity ->
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            val content = page.parent.parent as View
            content.layoutParams.width = (content.width * .7f).toInt()
            content.requestLayout()
        }
        waitUntil {
            var fits = false
            scenario!!.onActivity { activity ->
                val page = findView(activity.window.decorView, PdfPageView::class.java)!!
                fits = page.width == (page.parent.parent as View).width
            }
            fits
        }
        scenario!!.onActivity { activity ->
            assertSame(pageIdentity, findView(activity.window.decorView, PdfPageView::class.java))
            assertEquals(1, countPages(activity.window.decorView))
            val info = AccessibilityNodeInfo.obtain()
            val page = findView(activity.window.decorView, PdfPageView::class.java)!!
            page.onInitializeAccessibilityNodeInfo(info)
            val action =
                info.actionList.single {
                    it.label?.toString()?.startsWith("Highlight A stable") == true
                }
            assertTrue(page.performAccessibilityAction(action.id, null))
            info.recycle()
        }
        waitForNode { it.text?.toString() == "Copy quote" }
    }

    private fun countPages(view: View): Int {
        if (view is PdfPageView) return 1
        if (view !is ViewGroup) return 0
        return (0 until view.childCount).sumOf { countPages(view.getChildAt(it)) }
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
        val originalLocator = database.annotation(sessionId, annotationId)!!.getString("locator")
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
            database.annotation(sessionId, annotationId)!!.getString("locator"),
        )
        assertEquals(1, database.annotations(sessionId).length())
    }

    @Test
    fun multiScreenEpubRangePersistsAndNewSessionStartsBlank() {
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
        tapEpubHighlight()
        click("Close")
        database.deleteAnnotation(sessionId, annotationId)
        val selected =
            evaluateEpub(
                """
                (function() {
                    const ps = document.querySelectorAll('p');
                    const r = document.createRange();
                    r.setStart(ps[0].firstChild, 2); r.setEnd(ps[5].firstChild, 38);
                    const s = getSelection(); s.removeAllRanges(); s.addRange(r);
                    return r.getBoundingClientRect().height > innerHeight;
                })()
                """
                    .trimIndent()
            )
        assertEquals("true", selected)
        click("Selection")
        click("Highlight")
        waitUntil { database.annotations(sessionId).length() == 1 }
        val saved = database.annotations(sessionId).getJSONObject(0)
        assertTrue(saved.getString("quote").length > 800)
        val original = sessionId
        scenario!!.close()
        sessionId = database.createSession(bookId).getString("id")
        scenario =
            ActivityScenario.launch(
                Intent(context, ReaderActivity::class.java).putExtra("sessionId", sessionId)
            )
        waitUntil { evaluateEpub("!!document.querySelector('p')") == "true" }
        assertEquals(0, database.annotations(sessionId).length())
        assertEquals(
            "0",
            evaluateEpub("document.querySelectorAll('[data-group=readle] [data-style]').length"),
        )
        scenario!!.close()
        sessionId = original
        scenario =
            ActivityScenario.launch(
                Intent(context, ReaderActivity::class.java).putExtra("sessionId", sessionId)
            )
        waitUntil {
            evaluateEpub("document.querySelectorAll('[data-group=readle] [data-style]').length") ==
                "1"
        }
        assertEquals(
            saved.getString("locator"),
            database.annotations(sessionId).getJSONObject(0).getString("locator"),
        )
        assertTrue(
            JSONObject(database.session(sessionId).getString("state"))
                .getJSONObject("preferences")
                .getBoolean("epubScroll")
        )
    }

    @Test
    fun nativeSelectionSurvivesFingerScrollInContinuousLayout() {
        launchEpubForGestures()
        longPressEpubWord()
        val selected = evaluateEpub("getSelection().toString()")
        val before = webScrollXy().second
        val bounds = webScreenBounds()
        dragTouch(
            bounds.exactCenterX(),
            bounds.top + bounds.height() * .8f,
            bounds.exactCenterX(),
            bounds.top + bounds.height() * .3f,
            600,
        )
        waitUntil { webScrollXy().second > before + 100 }
        assertEquals(selected, evaluateEpub("getSelection().toString()"))
        click("Selection")
        click("Highlight")
        waitUntil { database.annotations(sessionId).length() == 2 }
        assertTrue(
            (0 until 2).any {
                database.annotations(sessionId).getJSONObject(it).getString("quote") == "appearance"
            }
        )
    }

    @Test
    fun nativeEndHandleAutoScrollsDownInContinuousLayout() {
        launchEpubForGestures()
        longPressEpubWord()
        val before = webScrollXy().second
        val bounds = webScreenBounds()
        val handle = selectionHandle(false)
        dragTouch(handle.first, handle.second, handle.first, bounds.bottom - 4f, 1800)
        waitUntil { webScrollXy().second > before + 100 }
        val quote = JSONArray("[" + evaluateEpub("getSelection().toString()") + "]").getString(0)
        assertTrue(quote.startsWith("appearance"))
        assertTrue(quote.length > 100)
        val stoppedAt = webScrollXy().second
        SystemClock.sleep(1200)
        assertEquals("Scroll continued after handle release", stoppedAt, webScrollXy().second)
    }

    @Test
    fun nativeEndHandleTurnsNextPageAndSavesItsExtendedRange() {
        launchEpubForGestures()
        click("Layout")
        click("Pages")
        waitUntil {
            evaluateEpub("document.scrollingElement.scrollWidth > innerWidth * 1.5") == "true"
        }
        val fontBefore = epubFontSize()
        repeat(4) { click("Text +") }
        waitUntil { epubFontSize() > fontBefore * 1.5 }
        longPressEpubWord()
        val before = webScrollXy().first
        val bounds = webScreenBounds()
        val handle = selectionHandle(false)
        captureGesture("end-before")
        dragTouch(handle.first, handle.second, bounds.right - 4f, handle.second, 1800)
        captureGesture("end-after")
        waitUntil { webScrollXy().first > before + bounds.width() * .8 }
        assertTrue(evaluateEpub("getSelection().toString().length").toInt() > 50)
        val stoppedAt = webScrollXy().first
        val stoppedQuote = evaluateEpub("getSelection().toString()")
        SystemClock.sleep(1400)
        assertEquals("Page kept turning after finger release", stoppedAt, webScrollXy().first)
        assertEquals(stoppedQuote, evaluateEpub("getSelection().toString()"))
        click("Selection")
        click("Highlight")
        waitUntil { database.annotations(sessionId).length() == 2 }
        val row =
            (0 until 2)
                .map { database.annotations(sessionId).getJSONObject(it) }
                .single { it.getString("quote").length > 50 }
        assertTrue(row.getString("quote").startsWith("appearance"))
        val locator = row.getString("locator")
        scenario!!.recreate()
        waitUntil { evaluateEpub("!!document.querySelector('p')") == "true" }
        assertEquals(
            locator,
            database.annotation(sessionId, row.getString("id"))!!.getString("locator"),
        )
        val expectedText = row.getString("quote").replace(Regex("\\s+"), "")
        var rendered = "null"
        waitUntil {
            rendered =
                evaluateEpub(
                    """
                (function() {
                    if (!window.readium) return null;
                    const item = readium.getDecorations('readle').items.find(i => i.decoration.id === ${JSONObject.quote(row.getString("id"))});
                    return item ? item.range.toString().replace(/\s/g, '') : null;
                })()
            """
                        .trimIndent()
                )
            rendered != "null"
        }
        assertEquals(expectedText, JSONArray("[" + rendered + "]").getString(0))
    }

    @Test
    fun nativeStartHandleTurnsPreviousPageAndKeepsTheEndAnchor() {
        launchEpubForGestures()
        click("Layout")
        click("Pages")
        waitUntil {
            evaluateEpub("document.scrollingElement.scrollWidth > innerWidth * 1.5") == "true"
        }
        click("Next")
        waitUntil { webScrollXy().first > 100 }
        longPressEpubWord()
        val before = webScrollXy().first
        val bounds = webScreenBounds()
        val handle = selectionHandle(true)
        captureGesture("start-before")
        dragTouch(handle.first, handle.second, bounds.left + 4f, handle.second, 1800)
        captureGesture("start-after")
        waitUntil { webScrollXy().first < before - bounds.width() * .8 }
        val quote = JSONArray("[" + evaluateEpub("getSelection().toString()") + "]").getString(0)
        assertTrue(quote.length > 50)
        assertTrue(quote.endsWith("appearance"))
    }

    private fun captureGesture(name: String) {
        File(context.getExternalFilesDir(null), "gesture-$name.png").outputStream().use {
            instrumentation.uiAutomation
                .takeScreenshot()
                ?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        android.util.Log.i(
            "ReadleGesture",
            "$name: " +
                evaluateEpub(
                    "JSON.stringify({selection:getSelection().toString(),x:scrollX,y:scrollY})"
                ),
        )
    }

    private fun launchEpubForGestures() {
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
        waitUntil { evaluateEpub("!!document.querySelector('p')") == "true" }
    }

    private fun webScreenBounds(): android.graphics.Rect {
        val bounds = android.graphics.Rect()
        scenario!!.onActivity { activity ->
            findVisibleWebView(activity.window.decorView)!!.getGlobalVisibleRect(bounds)
        }
        return bounds
    }

    private fun webScrollXy(): Pair<Int, Int> {
        var position = 0 to 0
        scenario!!.onActivity { activity ->
            findVisibleWebView(activity.window.decorView)!!.let {
                position = it.scrollX to it.scrollY
            }
        }
        return position
    }

    private fun screenPoint(css: String): Pair<Float, Float> {
        val xy = JSONArray(evaluateEpub(css))
        val bounds = webScreenBounds()
        val density = context.resources.displayMetrics.density
        return bounds.left + xy.getDouble(0).toFloat() * density to
            bounds.top + xy.getDouble(1).toFloat() * density
    }

    private fun longPressEpubWord() {
        val point =
            screenPoint(
                """
                (function() {
                    for (const p of document.querySelectorAll('p')) {
                        const n = p.firstChild; const at = n.textContent.indexOf('appearance');
                        if (at < 0) continue;
                        const r = document.createRange(); r.setStart(n, at); r.setEnd(n, at + 10);
                        const b = r.getBoundingClientRect();
                        if (b.left >= 0 && b.right <= innerWidth && b.top >= 0 && b.bottom <= innerHeight)
                            return [b.left + b.width/2, b.top + b.height/2];
                    }
                    return null;
                })()
                """
                    .trimIndent()
            )
        val down = SystemClock.uptimeMillis()
        sendTouch(MotionEvent.ACTION_DOWN, down, point.first, point.second)
        SystemClock.sleep(750)
        sendTouch(MotionEvent.ACTION_UP, down, point.first, point.second)
        waitUntil { evaluateEpub("getSelection().toString().trim()") == "\"appearance\"" }
    }

    private fun selectionHandle(start: Boolean) =
        screenPoint(
            """
        (function() {
            const r = getSelection().getRangeAt(0).cloneRange(); r.collapse($start);
            const b = r.getBoundingClientRect();
            return [b.left, b.bottom + 10];
        })()
    """
                .trimIndent()
        )

    private fun sendTouch(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.sendPointerSync(event)
        event.recycle()
    }

    private fun dragTouch(x: Float, y: Float, endX: Float, endY: Float, duration: Int) {
        val down = SystemClock.uptimeMillis()
        sendTouch(MotionEvent.ACTION_DOWN, down, x, y)
        for (step in 1..60) {
            val fraction = (step / 20f).coerceAtMost(1f)
            sendTouch(
                MotionEvent.ACTION_MOVE,
                down,
                x + (endX - x) * fraction,
                y + (endY - y) * fraction,
            )
            SystemClock.sleep((duration / 60).toLong())
        }
        sendTouch(MotionEvent.ACTION_UP, down, endX, endY)
    }

    private fun evaluateEpub(script: String): String {
        var value = "null"
        val completed = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val web = findVisibleWebView(activity.window.decorView)
            if (web == null) completed.countDown()
            else
                web.evaluateJavascript(script) {
                    value = it
                    completed.countDown()
                }
        }
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        return value
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
        val bounds = android.graphics.Rect()
        waitUntil {
            val node =
                findNode(instrumentation.uiAutomation.rootInActiveWindow) {
                    it.text?.toString()?.equals(label, ignoreCase = true) == true &&
                        it.isVisibleToUser
                }
            node?.getBoundsInScreen(bounds)
            node != null && !bounds.isEmpty
        }
        val time = SystemClock.uptimeMillis()
        val down =
            MotionEvent.obtain(
                time,
                time,
                MotionEvent.ACTION_DOWN,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                0,
            )
        instrumentation.sendPointerSync(down)
        down.recycle()
        SystemClock.sleep(50)
        val up =
            MotionEvent.obtain(
                time,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                0,
            )
        instrumentation.sendPointerSync(up)
        up.recycle()
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
