package com.adrianhq.chat

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * A round chat button in the workspace colour with an unread badge. Tapping it opens the chat.
 * Give it 64dp × 64dp (the button is 56dp; the rest leaves room for the badge).
 * In Jetpack Compose, host it with `AndroidView(factory = ::AdrianLauncherButton)`.
 */
class AdrianLauncherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val button = ImageView(context)
    private val badge = TextView(context)

    private val listener = object : AdrianListener {
        override fun onUnreadCountChanged(count: Int) = renderBadge(count)
        override fun onPrimaryColorChanged(color: Int) = circle.setColor(color)
    }

    init {
        clipChildren = false
        clipToPadding = false
        button.apply {
            background = circle
            setImageResource(R.drawable.adrian_ic_chat)
            scaleType = ImageView.ScaleType.CENTER
            elevation = dp(6f)
            contentDescription = "Open chat"
            isClickable = true
            isFocusable = true
            setOnClickListener { Adrian.present(context) }
        }
        addView(button, LayoutParams(dp(56f).toInt(), dp(56f).toInt(), Gravity.BOTTOM or Gravity.START))

        badge.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(11f)
                setColor(Color.parseColor("#EF4444"))
                setStroke(dp(2f).toInt(), Color.WHITE)
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(22f).toInt()
            setPadding(dp(5f).toInt(), 0, dp(5f).toInt(), 0)
            elevation = dp(7f)
            visibility = GONE
        }
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, dp(22f).toInt(), Gravity.TOP or Gravity.END))
        circle.setColor(Adrian.primaryColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Adrian.addListener(listener)
        circle.setColor(Adrian.primaryColor)
        renderBadge(Adrian.unreadCount)
    }

    override fun onDetachedFromWindow() {
        Adrian.removeListener(listener)
        super.onDetachedFromWindow()
    }

    private fun renderBadge(count: Int) {
        badge.text = if (count > 99) "99+" else count.toString()
        badge.visibility = if (count > 0) VISIBLE else GONE
        button.contentDescription = if (count > 0) "Open chat, $count unread" else "Open chat"
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
