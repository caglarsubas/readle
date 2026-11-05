package org.readium.r2.navigator

import android.os.SystemClock
import android.view.ActionMode
import android.view.MenuInflater
import android.view.View
import android.view.ViewConfiguration

/** Observes the public ActionMode drag lifecycle while retaining Chromium's native handles. */
internal class ReadleSelectionActionMode(
    private val delegate: ActionMode,
    private val view: R2BasicWebView,
) : ActionMode() {
    private var monitoring = false
    private var candidate = 0
    private var candidateSince = 0L
    private var lastTurn = 0L
    private val poll =
        object : Runnable {
            override fun run() {
                if (!monitoring || !view.isAttachedToWindow || !view.isSelecting) {
                    stop()
                    return
                }
                view.runJavaScript(EDGE.replace("__SCROLL__", view.scrollMode.toString())) { result
                    ->
                    if (!monitoring) return@runJavaScript
                    val direction = result.toIntOrNull() ?: 0
                    val now = SystemClock.uptimeMillis()
                    if (direction == 0 || direction != candidate) {
                        candidate = direction
                        candidateSince = now
                    } else if (view.scrollMode && now - candidateSince >= 250) {
                        if (view.canScrollVertically(direction)) {
                            val step = (view.height / 35).coerceAtLeast(12)
                            view.scrollBy(0, direction * step)
                        }
                    } else if (
                        !view.scrollMode && now - candidateSince >= 400 && now - lastTurn >= 1000
                    ) {
                        val pager = view as? R2WebView
                        if (pager != null && pager.numPages > 1) {
                            val next = (pager.mCurItem + direction).coerceIn(0, pager.numPages - 1)
                            if (next != pager.mCurItem) {
                                lastTurn = now
                                candidateSince = now
                                pager.setCurrentItem(next, false)
                            }
                        }
                    }
                    if (monitoring) view.postDelayed(this, 60)
                }
            }
        }

    override fun hide(duration: Long) {
        delegate.hide(duration)
        if (duration >= ViewConfiguration.getDefaultActionModeHideDuration()) {
            if (!monitoring) {
                monitoring = true
                candidate = 0
                view.runJavaScript("window.__readleSelectionEdge = null;")
                view.post(poll)
            }
        } else stop()
    }

    private fun stop() {
        monitoring = false
        candidate = 0
        view.removeCallbacks(poll)
    }

    override fun finish() {
        stop()
        delegate.finish()
    }

    override fun invalidate() = delegate.invalidate()

    override fun invalidateContentRect() = delegate.invalidateContentRect()

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) =
        delegate.onWindowFocusChanged(hasWindowFocus)

    override fun getType() = delegate.type

    override fun setTitle(title: CharSequence?) {
        delegate.title = title
    }

    override fun setTitle(resId: Int) = delegate.setTitle(resId)

    override fun setSubtitle(subtitle: CharSequence?) {
        delegate.subtitle = subtitle
    }

    override fun setSubtitle(resId: Int) = delegate.setSubtitle(resId)

    override fun setCustomView(view: View?) {
        delegate.customView = view
    }

    override fun getMenu() = delegate.menu

    override fun getTitle(): CharSequence? = delegate.title

    override fun getSubtitle(): CharSequence? = delegate.subtitle

    override fun getCustomView(): View? = delegate.customView

    override fun getMenuInflater(): MenuInflater = delegate.menuInflater

    override fun setTag(tag: Any?) {
        delegate.tag = tag
    }

    override fun getTag(): Any? = delegate.tag

    override fun setTitleOptionalHint(titleOptional: Boolean) {
        delegate.titleOptionalHint = titleOptional
    }

    override fun getTitleOptionalHint() = delegate.titleOptionalHint

    override fun isTitleOptional() = delegate.isTitleOptional

    companion object {
        private val EDGE =
            """
            (function() {
                const s = getSelection();
                if (!s || s.isCollapsed || !s.rangeCount) return 0;
                const r = s.getRangeAt(0);
                const scrolling = __SCROLL__;
                const prior = window.__readleSelectionEdge;
                const old = prior && prior.scrolling === scrolling ? prior : null;
                const st = {a:r.startContainer, ao:r.startOffset, z:r.endContainer, zo:r.endOffset, side:old ? old.side : null, scrolling};
                if (old) {
                    if (old.a !== st.a || old.ao !== st.ao) st.side = 'start';
                    else if (old.z !== st.z || old.zo !== st.zo) st.side = 'end';
                }
                window.__readleSelectionEdge = st;
                if (!st.side) return 0;
                const p = r.cloneRange(); p.collapse(st.side === 'start');
                const b = p.getBoundingClientRect();
                if (b.bottom <= 0 || b.top >= innerHeight) return 0;
                if (scrolling) {
                    const margin = Math.max(24, b.height * 1.5);
                    if (st.side === 'start' && b.top <= margin) return -1;
                    if (st.side === 'end' && b.bottom >= innerHeight - margin) return 1;
                    return 0;
                }
                const rtl = getComputedStyle(document.body).direction === 'rtl';
                const direction = (st.side === 'start' ? -1 : 1) * (rtl ? -1 : 1);
                const x = direction < 0 ? 1 : innerWidth - 1;
                const at = document.caretRangeFromPoint(x, b.top + b.height/2);
                if (!at) return 0;
                const node = st.side === 'start' ? st.a : st.z;
                const offset = st.side === 'start' ? st.ao : st.zo;
                const matched = at.startContainer === node && Math.abs(at.startOffset - offset) <= 1;
                return matched ? direction : 0;
            })()
            """
                .trimIndent()
    }
}
