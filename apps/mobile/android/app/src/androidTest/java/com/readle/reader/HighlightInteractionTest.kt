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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

/** Exercises the real reader, native touch dispatch, reader panels, and existing SQLite schema. */
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
    private var keepVisualWorkspace = false

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
        if (keepVisualWorkspace) return
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
        assertTrue(
            waitForNode { it.text?.toString() == "Keep" }
                .performAction(AccessibilityNodeInfo.ACTION_CLICK)
        )
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
            evaluateEpub(
                "window.readium && readium.getDecorations('readle').items.some(i => i.decoration.id === " +
                    JSONObject.quote(saved.getString("id")) +
                    ")"
            ) == "true"
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
        visualCheckpoint("selected-finger-scroll")
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
    fun nativeStartHandleAutoScrollsUpInContinuousLayout() {
        launchEpubForGestures()
        waitUntil {
            evaluateEpub("scrollTo(0, document.scrollingElement.scrollHeight)")
            !webCanScrollVertically(1)
        }
        longPressEpubWord("location")
        checkUpwardContinuousHandle("scroll-start-handle-stopped")
    }

    @Test
    fun returnedContinuousSelectionCanExtendUpWithoutAnotherLongPress() {
        launchEpubForGestures()
        waitUntil {
            evaluateEpub("scrollTo(0, document.scrollingElement.scrollHeight)")
            !webCanScrollVertically(1)
        }
        longPressEpubWord("location")
        val original = evaluateEpub("window.__readleAnchors.capture().readleRange")
        val bounds = webScreenBounds()
        val x = bounds.left + bounds.width() * .82f
        val upper = bounds.top + bounds.height() * .24f
        val lower = bounds.top + bounds.height() * .75f
        swipe(x, lower, x, upper)
        waitUntil { evaluateEpub("location.pathname").contains("c1.xhtml") }
        waitUntil { !webCanScrollVertically(-1) }
        swipe(x, upper, x, lower)
        waitUntil { evaluateEpub("location.pathname").contains("c0.xhtml") }
        waitUntil { !webCanScrollVertically(1) }
        waitUntil { evaluateEpub("window.__readleAnchors.capture()?.readleRange") == original }
        visualCheckpoint("scroll-selection-returned")
        checkUpwardContinuousHandle("scroll-restored-handle-stopped")
    }

    private fun checkUpwardContinuousHandle(checkpoint: String) {
        awaitEpubFrame()
        val before = webScrollXy().second
        val end = JSONObject(evaluateEpub("window.__readleAnchors.capture().readleRange"))
        val bounds = webScreenBounds()
        val handle = selectionHandle(start = true)
        dragTouch(handle.first, handle.second, handle.first, bounds.top + 4f, 1800)
        waitUntil { webScrollXy().second < before - 100 }
        val range = JSONObject(evaluateEpub("window.__readleAnchors.capture().readleRange"))
        assertEquals(end.getJSONArray("end").toString(), range.getJSONArray("end").toString())
        assertEquals(end.getInt("endOffset"), range.getInt("endOffset"))
        val stoppedAt = webScrollXy().second
        val quote = evaluateEpub("getSelection().toString()")
        SystemClock.sleep(1200)
        assertEquals(stoppedAt, webScrollXy().second)
        assertEquals(quote, evaluateEpub("getSelection().toString()"))
        visualCheckpoint(checkpoint)
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
        visualCheckpoint("end-before")
        dragTouch(handle.first, handle.second, bounds.right - 4f, handle.second, 1800)
        captureGesture("end-after")
        waitUntil { webScrollXy().first > before + bounds.width() * .8 }
        assertTrue(evaluateEpub("getSelection().toString().length").toInt() > 50)
        val stoppedAt = webScrollXy().first
        val stoppedQuote = evaluateEpub("getSelection().toString()")
        SystemClock.sleep(1400)
        assertEquals("Page kept turning after finger release", stoppedAt, webScrollXy().first)
        assertEquals(stoppedQuote, evaluateEpub("getSelection().toString()"))
        visualCheckpoint("end-stopped")
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
        visualCheckpoint("start-before")
        dragTouch(handle.first, handle.second, bounds.left + 4f, handle.second, 1800)
        captureGesture("start-after")
        waitUntil { webScrollXy().first < before - bounds.width() * .8 }
        val quote = JSONArray("[" + evaluateEpub("getSelection().toString()") + "]").getString(0)
        assertTrue(quote.length > 50)
        assertTrue(quote.endsWith("appearance"))
        val stoppedAt = webScrollXy().first
        val stoppedQuote = evaluateEpub("getSelection().toString()")
        SystemClock.sleep(1400)
        assertEquals("Previous page kept turning after release", stoppedAt, webScrollXy().first)
        assertEquals(stoppedQuote, evaluateEpub("getSelection().toString()"))
        visualCheckpoint("start-stopped")
    }

    @Test
    fun scrollAndPagesHaveDistinctTouchAxesAndScrollContinuesAtChapterEdge() {
        launchEpubForGestures()
        val bounds = webScreenBounds()
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        swipe(x, y + bounds.height() * .25f, x, y - bounds.height() * .25f)
        waitUntil { webScrollXy().second > 100 }
        assertEquals(0, webScrollXy().first)
        assertTrue(evaluateEpub("location.pathname").contains("c0.xhtml"))
        // Move to the bottom, allowing the native viewport to catch up to the DOM.
        // Chromium can still be finishing the first fling when scrollTo returns.
        waitUntil {
            evaluateEpub("scrollTo(0, document.scrollingElement.scrollHeight)")
            var atEnd = false
            scenario!!.onActivity { activity ->
                val web = findVisibleWebView(activity.window.decorView)!!
                atEnd = !web.canScrollVertically(1)
            }
            atEnd
        }
        swipe(x, y + bounds.height() * .25f, x, y - bounds.height() * .25f)
        waitUntil { evaluateEpub("location.pathname").contains("c1.xhtml") }
        waitUntil { webScrollXy().second < 10 }
        swipe(x, y - bounds.height() * .25f, x, y + bounds.height() * .25f)
        waitUntil { evaluateEpub("location.pathname").contains("c0.xhtml") }
        waitUntil { webScrollXy().second > 100 }
        visualCheckpoint("scroll-chapter-return")

        click("Layout")
        click("Pages")
        waitUntil {
            evaluateEpub("document.scrollingElement.scrollWidth > innerWidth * 1.5") == "true"
        }
        click("Contents")
        click("1. A place for an idea")
        waitUntil { webScrollXy().first == 0 }
        swipe(bounds.right - bounds.width() * .15f, y, bounds.left + bounds.width() * .15f, y)
        waitUntil { webScrollXy().first > 100 }
        assertEquals(0, webScrollXy().second)
        visualCheckpoint("horizontal-pages")
        click("Layout")
        click("Continuous scroll")
        waitUntil {
            evaluateEpub("document.scrollingElement.scrollHeight > innerHeight * 1.5") == "true"
        }
        assertEquals(0, webScrollXy().first)
    }

    @Test
    fun continuousChapterSwipesPreserveSelectedSourceInBothDirections() {
        launchEpubForGestures()
        waitUntil {
            evaluateEpub("scrollTo(0, document.scrollingElement.scrollHeight)")
            !webCanScrollVertically(1)
        }
        longPressEpubWord("location")
        val original = evaluateEpub("window.__readleAnchors.capture().readleRange")
        val b = webScreenBounds()
        val x = b.left + b.width() * .82f
        val upper = b.top + b.height() * .24f
        val lower = b.top + b.height() * .75f
        swipe(x, lower, x, upper)
        waitUntil { evaluateEpub("location.pathname").contains("c1.xhtml") }
        waitUntil { !webCanScrollVertically(-1) }
        assertEquals("location", selectionState()!!.getString("quote"))
        assertEquals(original, selectionState()!!.getJSONObject("locator")
            .getJSONObject("locations").getJSONObject("readleRange").toString())
        visualCheckpoint("selected-scroll-next-chapter")
        longPressEpubWord("weighted")
        swipe(x, upper, x, lower)
        waitUntil { evaluateEpub("location.pathname").contains("c0.xhtml") }
        waitUntil { !webCanScrollVertically(1) }
        assertEquals("weighted", selectionState()!!.getString("quote"))
        waitUntil {
            evaluateEpub("readium.getDecorations('readle').items.every(i => !i.decoration.id.startsWith('selection'))") == "true"
        }
        visualCheckpoint("selected-scroll-previous-chapter")
    }

    private fun webCanScrollVertically(direction: Int): Boolean {
        var canScroll = false
        scenario!!.onActivity { activity ->
            canScroll = findVisibleWebView(activity.window.decorView)!!.canScrollVertically(direction)
        }
        return canScroll
    }

    @Test
    fun paginatedSelectionIsAdjustableAfterLeavingAndReturningToItsChapter() {
        launchEpubForGestures()
        click("Layout")
        click("Pages")
        click("Contents")
        click("2. A simple learning rule")
        waitUntil { evaluateEpub("location.pathname").contains("c1.xhtml") }
        click("Previous")
        waitUntil { evaluateEpub("location.pathname").contains("c0.xhtml") }
        awaitEpubFrame()
        longPressEpubWord("location")
        val original = JSONObject(evaluateEpub("window.__readleAnchors.capture().readleRange"))
        click("Next")
        waitUntil { evaluateEpub("location.pathname").contains("c1.xhtml") }
        click("Previous")
        waitUntil { evaluateEpub("location.pathname").contains("c0.xhtml") }
        waitUntil { evaluateEpub("getSelection().toString()") == JSONObject.quote("location") }
        assertEquals(original.toString(), evaluateEpub("window.__readleAnchors.capture().readleRange"))
        visualCheckpoint("chapter-selection-returned")
        awaitEpubFrame()
        val handle = selectionHandle(start = true)
        val b = webScreenBounds()
        dragTouch(handle.first, handle.second, b.left + 5f, handle.second, 1800)
        val adjusted = JSONObject(evaluateEpub("window.__readleAnchors.capture().readleRange"))
        assertNotEquals(original.toString(), adjusted.toString())
        assertEquals(original.getJSONArray("end").toString(), adjusted.getJSONArray("end").toString())
        assertEquals(original.getInt("endOffset"), adjusted.getInt("endOffset"))
        val stoppedAt = webScrollXy()
        SystemClock.sleep(1200)
        assertEquals(stoppedAt, webScrollXy())
        assertEquals(adjusted.toString(), evaluateEpub("window.__readleAnchors.capture().readleRange"))
        visualCheckpoint("chapter-selection-adjusted")
        click("Selection")
        click("Highlight")
        waitUntil { database.annotations(sessionId).length() == 2 }
    }

    @Test
    fun nativeSelectionRemainsAdjustableAfterButtonPageTurns() =
        checkAdjustableSelectionAfterPageTurns(buttons = true)

    @Test
    fun nativeSelectionRemainsAdjustableAfterSwipePageTurns() =
        checkAdjustableSelectionAfterPageTurns(buttons = false)

    @Test
    fun paginatedSelectionRecoversIfWebViewDropsTheRangeWhileAway() =
        checkAdjustableSelectionAfterPageTurns(buttons = true, loseNativeSelection = true)

    private fun checkAdjustableSelectionAfterPageTurns(
        buttons: Boolean,
        loseNativeSelection: Boolean = false,
    ) {
        launchEpubForGestures()
        click("Layout")
        click("Pages")
        waitUntil {
            evaluateEpub("document.scrollingElement.scrollWidth > innerWidth * 1.5") == "true"
        }
        if (buttons) {
            val fontBefore = epubFontSize()
            repeat(4) { click("Text +") }
            waitUntil { epubFontSize() > fontBefore * 1.5 }
        } else {
            click("Next")
            waitUntil { webScrollXy().first > 100 }
        }
        longPressEpubWord()
        fun range() = JSONObject(evaluateEpub("window.__readleAnchors.capture().readleRange"))
        val original = range()
        val page = webScrollXy().first
        val bounds = webScreenBounds()
        fun turn(forward: Boolean) {
            if (buttons) click(if (forward) "Next" else "Previous")
            else {
                val left = bounds.left + bounds.width() * .15f
                val right = bounds.right - bounds.width() * .15f
                swipe(
                    if (forward) right else left,
                    bounds.exactCenterY(),
                    if (forward) left else right,
                    bounds.exactCenterY(),
                )
            }
        }
        turn(forward = buttons)
        waitUntil { if (buttons) webScrollXy().first > page + 100 else webScrollXy().first == 0 }
        assertEquals(
            "Live selection was cleared by page navigation",
            JSONObject.quote("appearance"),
            evaluateEpub("getSelection().toString().trim()"),
        )
        assertEquals(original.toString(), range().toString())
        if (loseNativeSelection) {
            // Reproduce a WebView losing its range while the source column is offscreen.
            evaluateEpub("getSelection().removeAllRanges()")
            waitUntil { evaluateEpub("getSelection().isCollapsed") == "true" }
        }
        turn(forward = !buttons)
        waitUntil { webScrollXy().first == page }
        waitUntil { evaluateEpub("window.__readleAnchors.capture()?.readleRange") == original.toString() }
        assertEquals(original.toString(), range().toString())
        val name = if (loseNativeSelection) "recovered" else if (buttons) "buttons" else "swipes"
        visualCheckpoint("$name-range-returned")
        awaitEpubFrame()

        // Drive the rendered grip with touch. A retained quote or a DOM-only range
        // cannot satisfy the handle drag and fixed-opposite-endpoint checks.
        val handle = selectionHandle(start = !buttons)
        dragTouch(
            handle.first,
            handle.second,
            if (buttons) bounds.right - 4f else bounds.left + 4f,
            handle.second,
            1800,
        )
        waitUntil {
            if (buttons) webScrollXy().first > page + bounds.width() * .8
            else webScrollXy().first < page - bounds.width() * .8
        }
        val extended = range()
        val fixed = if (buttons) "start" else "end"
        assertEquals(original.getJSONArray(fixed).toString(), extended.getJSONArray(fixed).toString())
        assertEquals(original.getInt("${fixed}Offset"), extended.getInt("${fixed}Offset"))
        val quote = evaluateEpub("getSelection().toString()")
        assertTrue(JSONArray("[$quote]").getString(0).length > 50)
        val stoppedAt = webScrollXy().first
        SystemClock.sleep(1400)
        assertEquals(stoppedAt, webScrollXy().first)
        assertEquals(quote, evaluateEpub("getSelection().toString()"))
        visualCheckpoint("$name-range-adjusted")
        click("Selection")
        click("Highlight")
        waitUntil { database.annotations(sessionId).length() == 2 }
    }

    @Test
    fun previousNextAndHorizontalSwipesKeepTheSelectedPassage() {
        launchEpubForGestures()
        click("Layout")
        click("Pages")
        waitUntil {
            evaluateEpub("document.scrollingElement.scrollWidth > innerWidth * 1.5") == "true"
        }
        longPressEpubWord()
        click("Next")
        waitUntil { webScrollXy().first > 100 }
        waitUntil { selectionState()?.optString("quote") == "appearance" }
        val held = selectionState()!!.getJSONObject("locator").toString()
        click("Previous")
        waitUntil { webScrollXy().first == 0 }
        assertEquals(held, selectionState()!!.getJSONObject("locator").toString())
        visualCheckpoint("selection-after-button-turns")
        click("Selection")
        click("Add note")
        scenario!!.onActivity { reader ->
            assertNotNull(findView(reader.window.decorView, android.widget.EditText::class.java))
        }
        val editor = waitForNode { it.className?.toString() == "android.widget.EditText" }
        editor.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "Kept through page turns",
                )
            },
        )
        click("Save")
        waitUntil {
            (0 until database.annotations(sessionId).length()).any {
                database.annotations(sessionId).getJSONObject(it).getString("note") ==
                    "Kept through page turns"
            }
        }

        longPressEpubWord("changes")
        val bounds = webScreenBounds()
        swipe(
            bounds.right - bounds.width() * .15f,
            bounds.exactCenterY(),
            bounds.left + bounds.width() * .15f,
            bounds.exactCenterY(),
        )
        waitUntil { webScrollXy().first > 100 }
        waitUntil { selectionState()?.optString("quote") == "changes" }
        click("Selection")
        click("Open passage")
        waitUntil { webScrollXy().first == 0 }
        visualCheckpoint("selection-after-swipe-return")
    }

    @Test
    fun epubFindShowsNoMatchesThenOpensTheMatchingChapterAndQuote() {
        launchEpubForGestures()
        click("Find")
        fun setQuery(query: String) {
            waitForNode { it.className?.toString() == "android.widget.EditText" }
                .performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            query,
                        )
                    },
                )
            click("Find text")
        }
        setQuery("zzzxq-not-a-word")
        waitForNode { it.text?.toString() == "No matches found." }
        visualCheckpoint("find-no-matches")
        setQuery("hypothetical linear response")
        val result = waitForNode {
            it.className?.toString() == "android.widget.Button" &&
                it.text?.toString()?.contains("hypothetical linear response.") == true &&
                it.isVisibleToUser
        }
        assertTrue(result.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitUntil { evaluateEpub("location.pathname").contains("c1.xhtml") }
        waitUntil {
            evaluateEpub(
                """readium.getDecorations('find').items.some(item => item.range.toString() === 'hypothetical linear response')"""
            ) == "true"
        }
        val quoteY =
            evaluateEpub(
                    """readium.getDecorations('find').items[0].range.getBoundingClientRect().top"""
                )
                .toDouble()
        assertTrue(quoteY >= 0)
        assertTrue(quoteY < evaluateEpub("innerHeight").toDouble())
        visualCheckpoint("find-chapter-two")
    }

    @Test
    fun readerNotesEditAndBackRestoreSamePassage() {
        launchEpubForGestures()
        longPressEpubWord()
        click("Selection")
        click("Add note")
        fun setNote(text: String) {
            val editor = waitForNode { it.className?.toString() == "android.widget.EditText" }
            assertTrue(
                editor.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text,
                        )
                    },
                )
            )
        }
        setNote("Fold visual UAT note")
        click("Save")
        waitUntil { database.annotations(sessionId).length() == 2 }
        click("Notes")
        waitForNode { it.text?.toString() == "appearance\nFold visual UAT note" }
        visualCheckpoint("notes-saved")
        // Deliver the system Back action, independent of emulator toolbar key forwarding.
        assertTrue(
            instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            )
        )
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        val backClosed = findNode(instrumentation.uiAutomation.rootInActiveWindow) {
            it.text?.toString() == "This session’s highlights & notes" && it.isVisibleToUser
        } == null
        visualCheckpoint(if (backClosed) "notes-back-closed" else "notes-back-still-open")
        // Retain evidence for editing/restoration even if this independent Back subcheck fails.
        if (!backClosed) click("Close")
        click("Notes")
        click("appearance\nFold visual UAT note")
        click("Edit note")
        setNote("Fold visual UAT edited")
        click("Save")
        click("Notes")
        click("appearance\nFold visual UAT edited")
        click("Open source")
        val saved = (0 until database.annotations(sessionId).length())
            .map { database.annotations(sessionId).getJSONObject(it) }
            .single { it.getString("note") == "Fold visual UAT edited" }
        assertEquals("appearance", saved.getString("quote"))
        click("Layout")
        click("Pages")
        waitUntil {
            !JSONObject(database.session(sessionId).getString("state"))
                .getJSONObject("preferences").getBoolean("epubScroll")
        }
        visualCheckpoint("note-edited-pages")
        if (InstrumentationRegistry.getArguments().getString("readleKeepVisualFixture") == "true") {
            File(context.getExternalFilesDir(null), "visual-workspace.json").writeText(
                JSONObject().put("bookId", bookId).put("sessionId", sessionId)
                    .put("annotation", saved).put("session", database.session(sessionId)).toString(2)
            )
            keepVisualWorkspace = true
        }
        assertTrue("System Back did not dismiss the Notes panel", backClosed)
    }

    private fun selectionState(): JSONObject? =
        JSONObject(database.session(sessionId).getString("state")).optJSONObject("selection")

    private fun awaitEpubFrame() {
        val ready = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val web = findVisibleWebView(activity.window.decorView)!!
            web.postVisualStateCallback(
                SystemClock.uptimeMillis(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        web.postOnAnimation { ready.countDown() }
                    }
                },
            )
        }
        assertTrue("WebView did not render the returned selection", ready.await(3, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 3000)
    }

    private fun swipe(x: Float, y: Float, endX: Float, endY: Float) {
        val down = SystemClock.uptimeMillis()
        sendTouch(MotionEvent.ACTION_DOWN, down, x, y)
        for (step in 1..12) {
            val f = step / 12f
            sendTouch(MotionEvent.ACTION_MOVE, down, x + (endX - x) * f, y + (endY - y) * f)
            SystemClock.sleep(15)
        }
        sendTouch(MotionEvent.ACTION_UP, down, endX, endY)
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

    /** Opt-in observation time for a visible Android Studio run; normal checks do not pause. */
    private fun visualCheckpoint(name: String) {
        val pause =
            InstrumentationRegistry.getArguments()
                .getString("readleVisualPauseMs")
                ?.toLongOrNull()
                ?.coerceIn(0L, 30000L) ?: 0L
        if (pause == 0L) return
        captureGesture(name)
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("stream", "Readle visual checkpoint: $name\n") },
        )
        SystemClock.sleep(pause)
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

    private fun longPressEpubWord(word: String = "appearance") {
        val point =
            screenPoint(
                """
                (function() {
                    for (const p of document.querySelectorAll('p')) {
                        const n = p.firstChild; const at = n.textContent.indexOf(${JSONObject.quote(word)});
                        if (at < 0) continue;
                        const r = document.createRange(); r.setStart(n, at); r.setEnd(n, at + ${word.length});
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
        waitUntil { evaluateEpub("getSelection().toString().trim()") == JSONObject.quote(word) }
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
        // A note save can finish while the keyboard is still changing the reader's
        // insets. Sample the real hit location after the PDF viewport has settled.
        waitUntil {
            var keyboardHidden = false
            scenario!!.onActivity { activity ->
                keyboardHidden = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            keyboardHidden
        }
        instrumentation.uiAutomation.waitForIdle(300, 3000)
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
        fun candidates(node: View): Sequence<WebView> = sequence {
            if (node is WebView && node.isShown) yield(node)
            if (node is ViewGroup)
                for (index in 0 until node.childCount) yieldAll(candidates(node.getChildAt(index)))
        }
        val bounds = android.graphics.Rect()
        return candidates(view)
            .mapNotNull {
                if (it.getGlobalVisibleRect(bounds)) it to bounds.width() * bounds.height()
                else null
            }
            .maxByOrNull { it.second }
            ?.first
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
