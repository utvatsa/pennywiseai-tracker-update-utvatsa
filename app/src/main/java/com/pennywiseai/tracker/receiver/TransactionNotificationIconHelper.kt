package com.pennywiseai.tracker.receiver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.pennywiseai.tracker.ui.icons.BrandIcons
import com.pennywiseai.tracker.ui.icons.CategoryMapping

/**
 * Generates circular Bitmaps for transaction OS notifications (NotificationCompat.setLargeIcon).
 *
 * Hierarchy:
 * 1. Brand logo drawable (if merchant matches a known brand, e.g. Swiggy, Uber, Netflix)
 * 2. Category emoji (drawn centered on a circle tinted with the category's theme color)
 * 3. Fallback emoji / placeholder (drawn on a neutral dark circle for unidentified transactions)
 */
object TransactionNotificationIconHelper {

    const val NOTIFICATION_ICON_SIZE_PX = 192
    private const val DEFAULT_FALLBACK_EMOJI = "🏷️"
    private const val DEFAULT_FALLBACK_COLOR_HEX = "#2E3250"

    fun createCircularIconBitmap(
        context: Context,
        merchantName: String,
        category: String?,
        sizePx: Int = NOTIFICATION_ICON_SIZE_PX
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = sizePx / 2f

        // 1. Try brand-specific logo
        val brandResId = BrandIcons.getIconResource(merchantName)
        if (brandResId != null) {
            val brandHex = BrandIcons.getBrandColor(merchantName)
            val bgColor = brandHex?.let { runCatching { it.toColorInt() }.getOrNull() }
                ?: android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX)

            paint.color = bgColor
            canvas.drawCircle(radius, radius, radius, paint)

            val drawable = runCatching { ContextCompat.getDrawable(context, brandResId) }.getOrNull()
            if (drawable != null) {
                // Inset by 22% for clean padding inside the circle
                val padding = (sizePx * 0.22f).toInt()
                drawable.setBounds(padding, padding, sizePx - padding, sizePx - padding)
                drawable.draw(canvas)
                return bitmap
            }
        }

        // 2. Try category emoji
        val effectiveCategory = category?.takeIf { it.isNotBlank() && !it.equals("Uncategorized", ignoreCase = true) }
            ?: CategoryMapping.getCategory(merchantName)

        val emoji = CategoryMapping.effectiveEmojiFor(effectiveCategory) ?: DEFAULT_FALLBACK_EMOJI
        val categoryColor = CategoryMapping.colorFor(effectiveCategory)
        val bgColor = runCatching { categoryColor.toArgb() }
            .getOrDefault(android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX))

        paint.color = bgColor
        canvas.drawCircle(radius, radius, radius, paint)

        // Draw emoji centered
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * 0.45f
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val yOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
        canvas.drawText(emoji, radius, radius + yOffset, textPaint)

        return bitmap
    }
}
