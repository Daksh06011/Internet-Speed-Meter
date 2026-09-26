package com.netspeedtest.ui.nav

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.netspeedtest.AppGraph
import com.netspeedtest.ui.components.Glyph
import com.netspeedtest.ui.components.IconButton
import com.netspeedtest.ui.components.unclip
import com.netspeedtest.ui.theme.TextStyle
import com.netspeedtest.ui.theme.Ui
import kotlinx.coroutines.CoroutineScope

/** Shared services every screen can reach. */
class ScreenEnv(
    val ui: Ui,
    val graph: AppGraph,
    val sheets: SheetHost,
) {
    val navigator: Navigator get() = graph.navigator
}

/**
 * Base class for a full-screen destination: a vertically scrolling column that respects
 * system bar insets. Live data is collected only inside [onActive]'s scope, which is
 * cancelled as soon as the screen is hidden or the app goes to the background.
 */
abstract class Screen(protected val env: ScreenEnv) : FrameLayout(env.ui.context) {
    protected val ui: Ui get() = env.ui
    protected val scroll = ScrollView(context)
    protected val column = LinearLayout(context)
    private val sidePadding = ui.dp(20)

    init {
        setBackgroundColor(ui.palette.background)
        scroll.isFillViewport = true
        scroll.overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        scroll.isVerticalScrollBarEnabled = false
        scroll.unclip()
        column.orientation = LinearLayout.VERTICAL
        column.unclip()
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        applyInsets(0, 0, 0, 0)
    }

    /** Called with a fresh scope whenever the screen becomes visible and the app is in the foreground. */
    abstract fun onActive(scope: CoroutineScope)

    /** Handles back before the navigator does. Return true to consume it. */
    open fun onBack(): Boolean = false

    fun applyInsets(left: Int, top: Int, right: Int, bottom: Int) {
        column.setPadding(sidePadding + left, top + ui.dp(12), sidePadding + right, bottom + ui.dp(32))
    }

    /** Standard header for secondary screens: back button + title (+ optional trailing view). */
    protected fun header(title: String, trailing: View? = null): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            unclip()
        }
        val back = IconButton(ui, Glyph.Back, "Back").apply {
            onClick { env.navigator.pop() }
        }
        row.addView(back, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        val titleView = ui.text(TextStyle.Headline, title).apply {
            isAccessibilityHeading = true
            setPadding(ui.dp(16), 0, 0, 0)
        }
        row.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        trailing?.let { row.addView(it) }
        column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = ui.dp(24)
        })
        return row
    }

    /** Small uppercase section label placed above a group of cards. */
    protected fun sectionLabel(text: String, topMargin: Int = 28): View =
        ui.text(TextStyle.Label, text, ui.palette.textTertiary).also {
            it.isAccessibilityHeading = true
            column.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                this.topMargin = ui.dp(topMargin)
                bottomMargin = ui.dp(14)
                leftMargin = ui.dp(4)
            })
        }

    protected fun addBlock(view: View, topMargin: Int = 0): View {
        column.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = ui.dp(topMargin)
        })
        return view
    }
}
