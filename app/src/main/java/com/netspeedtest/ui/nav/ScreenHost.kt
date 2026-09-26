package com.netspeedtest.ui.nav

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.netspeedtest.ui.components.Haptics
import com.netspeedtest.ui.components.Motion
import com.netspeedtest.ui.components.NeuButton
import com.netspeedtest.ui.components.NeuCard
import com.netspeedtest.ui.components.TouchBlocker
import com.netspeedtest.ui.theme.TextStyle
import com.netspeedtest.ui.theme.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Shows the screen for the navigator's current route with a short shared-axis
 * transition, and owns the lifecycle scope handed to the visible screen.
 */
class ScreenHost(
    private val ui: Ui,
    private val factory: (Route) -> Screen,
) : FrameLayout(ui.context) {
    private var current: Screen? = null
    private var scope: CoroutineScope? = null
    private var foreground = false
    private var insets = IntArray(4)

    val screen: Screen? get() = current

    fun show(route: Route, forward: Boolean?) {
        val next = factory(route)
        next.applyInsets(insets[0], insets[1], insets[2], insets[3])
        val previous = current
        stopScope()
        current = next
        addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (previous != null && forward != null && !ui.reduceMotion) {
            val shift = width * 0.08f * if (forward) 1 else -1
            if (!forward) bringChildToFront(previous) // the leaving screen slides over when going back
            if (forward) {
                next.translationX = shift
                next.alpha = 0f
                next.animate().translationX(0f).alpha(1f).setDuration(Motion.MEDIUM + 40)
                    .setInterpolator(Motion.EmphasizedDecelerate).withEndAction { removeView(previous) }.start()
            } else {
                previous.animate().translationX(-shift).alpha(0f).setDuration(Motion.MEDIUM)
                    .setInterpolator(Motion.Standard).withEndAction { removeView(previous) }.start()
            }
        } else if (previous != null) {
            removeView(previous)
        }
        startScopeIfNeeded()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) startScopeIfNeeded() else stopScope()
    }

    fun setInsets(left: Int, top: Int, right: Int, bottom: Int) {
        insets = intArrayOf(left, top, right, bottom)
        for (i in 0 until childCount) (getChildAt(i) as? Screen)?.applyInsets(left, top, right, bottom)
    }

    private fun startScopeIfNeeded() {
        val screen = current ?: return
        if (!foreground || scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        screen.onActive(newScope)
    }

    private fun stopScope() {
        scope?.cancel()
        scope = null
    }

    fun dispose() {
        stopScope()
    }
}

/**
 * Bottom sheet overlay with a spring entrance. Used for confirmations, so destructive
 * actions (clearing history) always take a deliberate second tap.
 */
class SheetHost(private val ui: Ui) : FrameLayout(ui.context) {
    private var sheet: View? = null
    private val scrim = TouchBlocker(ui).apply {
        setBackgroundColor(ui.palette.scrim)
        setOnClickListener { dismiss() }
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var bottomInset = 0

    init {
        visibility = GONE
    }

    val isShowing: Boolean get() = sheet != null

    fun setBottomInset(value: Int) {
        bottomInset = value
    }

    fun confirm(title: String, message: String, confirmLabel: String, destructive: Boolean, onConfirm: () -> Unit) {
        val card = NeuCard(ui, radiusDp = 30f).apply {
            setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(24) + bottomInset)
            isClickable = true
        }
        card.addView(ui.text(TextStyle.Headline, title).apply { isAccessibilityHeading = true })
        card.addView(ui.text(TextStyle.Body, message, ui.palette.textSecondary).apply {
            setLineSpacing(0f, 1.25f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(10)
        })
        val buttons = LinearLayout(ui.context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        val cancel = NeuButton(ui, "Cancel", primary = false).apply { onClick { dismiss() } }
        val confirm = NeuButton(ui, confirmLabel, primary = !destructive).apply {
            if (destructive) text.setTextColor(ui.palette.danger)
            onClick {
                Haptics.confirm(this)
                dismiss()
                onConfirm()
            }
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ui.dp(8) })
        buttons.addView(confirm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(8) })
        card.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(28)
        })
        present(card)
    }

    private fun present(card: View) {
        dismissNow()
        visibility = VISIBLE
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = ui.dp(8)
            rightMargin = ui.dp(8)
            bottomMargin = ui.dp(8)
        })
        sheet = card
        if (ui.reduceMotion) return
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(Motion.MEDIUM).start()
        card.translationY = ui.dp(320f)
        card.animate().translationY(0f).setDuration(Motion.SLOW + 80).setInterpolator(Motion.SpringSoft).start()
        card.announceForAccessibility((card as? ViewGroup)?.getChildAt(0)?.let { (it as? android.widget.TextView)?.text } ?: "")
    }

    fun dismiss() {
        val card = sheet ?: return
        sheet = null
        if (ui.reduceMotion) {
            dismissNow()
            return
        }
        scrim.animate().alpha(0f).setDuration(Motion.FAST).start()
        card.animate().translationY(card.height.toFloat() + ui.dp(24f)).setDuration(Motion.MEDIUM)
            .setInterpolator(Motion.Standard).withEndAction { if (sheet == null) dismissNow() }.start()
    }

    private fun dismissNow() {
        removeAllViews()
        sheet = null
        visibility = GONE
    }
}
