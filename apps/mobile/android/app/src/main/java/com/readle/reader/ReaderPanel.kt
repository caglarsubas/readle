package com.readle.reader

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

/** A panel inside the reading surface, with its own scroll and a stable book viewport. */
internal class ReaderPanel(
    private val activity: AppCompatActivity,
    private val host: FrameLayout,
    private val onClose: () -> Unit,
) {
    private val scroll =
        object : ScrollView(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val available = View.MeasureSpec.getSize(heightMeasureSpec)
                    val max = (available * if (available < dp(440)) 0.9f else 0.66f).toInt()
                    super.onMeasure(
                        widthMeasureSpec,
                        View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST),
                    )
                }
            }
            .apply {
                visibility = View.GONE
                setBackgroundColor(0xFFF5F4F0.toInt())
                elevation = dp(12).toFloat()
                isFillViewport = false
                isClickable = true
            }

    val visible: Boolean
        get() = scroll.visibility == View.VISIBLE

    init {
        host.addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    fun show(title: String, body: LinearLayout.() -> Unit) {
        hideKeyboard()
        if (visible) onClose()
        scroll.removeAllViews()
        val panel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(6), dp(16), dp(10))
                isFocusableInTouchMode = true
            }
        val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(
            TextView(activity).apply {
                text = title
                textSize = 19f
                setTextColor(0xFF28352B.toInt())
                ViewCompat.setAccessibilityHeading(this, true)
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        heading.addView(action("Close", ::close))
        panel.addView(heading)
        panel.body()
        scroll.addView(panel)
        ViewCompat.setAccessibilityPaneTitle(scroll, title)
        scroll.visibility = View.VISIBLE
        scroll.bringToFront()
        scroll.scrollTo(0, 0)
        panel.requestFocus()
    }

    fun close() {
        if (!visible) return
        hideKeyboard()
        scroll.visibility = View.GONE
        scroll.removeAllViews()
        onClose()
    }

    fun text(value: CharSequence, size: Float = 16f) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(0xFF28352B.toInt())
            setPadding(0, dp(5), 0, dp(5))
        }

    fun action(label: CharSequence, onClick: () -> Unit) =
        Button(activity).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            elevation = 0f
            minHeight = dp(48)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE1E7DB.toInt())
            setTextColor(0xFF355742.toInt())
            setOnClickListener { onClick() }
        }

    fun addActions(parent: LinearLayout, vararg actions: Pair<String, () -> Unit>) {
        val row = LinearLayout(activity)
        actions.forEach { (label, click) ->
            row.addView(action(label, click), LinearLayout.LayoutParams(0, -2, 1f))
        }
        parent.addView(row)
    }

    private fun hideKeyboard() {
        if (!visible) return
        val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(scroll.windowToken, 0)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
