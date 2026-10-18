package com.kavach.guard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Tiny view helpers shared by the safety screens. */
object Ui {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun lp(c: Context, top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(c, top) }
    fun page(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 20), dp(c, 36), dp(c, 20), dp(c, 24)) }
    fun scroll(c: Context, root: LinearLayout, bg: Int) = ScrollView(c).apply { setBackgroundColor(bg); addView(root) }
    fun tv(c: Context, t: String, size: Float, color: Int = Color.BLACK, bold: Boolean = false, top: Int = 0) = TextView(c).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); layoutParams = lp(c, top)
    }
    fun btn(c: Context, t: String, bg: String, fg: Int = Color.WHITE, onClick: () -> Unit) = Button(c).apply {
        text = t; isAllCaps = false; textSize = 16f; setTextColor(fg)
        background = GradientDrawable().apply { cornerRadius = dp(c, 12).toFloat(); setColor(Color.parseColor(bg)) }
        setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14)); setOnClickListener { onClick() }
    }
}
