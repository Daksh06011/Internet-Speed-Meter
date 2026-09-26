package com.netspeedtest.ui.components

import android.view.ViewGroup
import android.widget.LinearLayout
import com.netspeedtest.ui.theme.TextStyle
import com.netspeedtest.ui.theme.Ui

/** A card holding [InfoRow]s separated by hairline dividers. */
fun Ui.rowsCard(rows: List<InfoRow>): NeuCard = NeuCard(this, radiusDp = 26f).apply {
    setPadding(dp(20), dp(4), dp(20), dp(4))
    rows.forEachIndexed { i, row ->
        if (i > 0) addView(divider())
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

/** A quiet explanatory note (platform limitations, estimates). */
fun Ui.noteCard(title: String, body: String): NeuCard = NeuCard(this, radiusDp = 22f, style = NeuDrawable.Style.Inset, elevation = 0.7f).apply {
    setPadding(dp(20), dp(18), dp(20), dp(18))
    addView(text(TextStyle.Label, title, palette.textTertiary))
    addView(text(TextStyle.Caption, body, palette.textSecondary).apply {
        setLineSpacing(0f, 1.3f)
        setPadding(0, dp(8), 0, 0)
    })
    isFocusable = true
}
