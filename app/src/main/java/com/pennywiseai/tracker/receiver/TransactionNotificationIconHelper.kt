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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.icons.BrandIcons
import com.pennywiseai.tracker.ui.icons.CategoryMapping

/**
 * Generates circular Bitmaps for transaction OS notifications (NotificationCompat.setLargeIcon).
 *
 * 5-Stage Hierarchy:
 * 1. Brand First: Known brand logo drawable (e.g. Swiggy, Uber, Netflix, Zepto)
 * 2. Multi-Emoji Split Second: Composite multi-emoji badge when 2+ split categories exist
 * 3. Single Emoji for Category Third: Category emoji drawn centered on a circle tinted with theme color
 * 4. Default for Transaction Type Fourth: e.g. Income ("💵" on emerald green circle)
 * 5. Default Fallback Fifth: Fallback placeholder ("🏷️") on neutral dark circle
 */
object TransactionNotificationIconHelper {

    const val NOTIFICATION_ICON_SIZE_PX = 192
    private const val DEFAULT_FALLBACK_EMOJI = "🏷️"
    private const val DEFAULT_FALLBACK_COLOR_HEX = "#2E3250"

    fun createCircularIconBitmap(
        context: Context,
        merchantName: String,
        category: String?,
        sizePx: Int = NOTIFICATION_ICON_SIZE_PX,
        splitCategories: List<String> = emptyList(),
        transactionType: TransactionType? = null
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = sizePx / 2f

        // Stage 1: Brand First
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

        // Stage 2: Multi-Emoji Split Second
        if (splitCategories.size >= 2) {
            val primaryCat = splitCategories[0]
            val secondaryCat = splitCategories[1]
            val tertiaryCat = splitCategories.getOrNull(2)

            val primaryColor = runCatching { CategoryMapping.colorFor(primaryCat).toArgb() }
                .getOrDefault(android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX))
            paint.color = primaryColor
            canvas.drawCircle(radius, radius, radius, paint)

            val primaryEmoji = CategoryMapping.effectiveEmojiFor(primaryCat) ?: DEFAULT_FALLBACK_EMOJI
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sizePx * 0.38f
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val yOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
            canvas.drawText(primaryEmoji, radius, radius * 0.82f + yOffset, textPaint)

            // Secondary chip at bottom-left
            val chipRadius = sizePx * 0.22f
            val chipX = radius * 0.58f
            val chipY = sizePx - radius * 0.52f
            val secColor = runCatching { CategoryMapping.colorFor(secondaryCat).toArgb() }
                .getOrDefault(android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX))
            paint.color = secColor
            canvas.drawCircle(chipX, chipY, chipRadius, paint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.025f
                color = android.graphics.Color.WHITE
            }
            canvas.drawCircle(chipX, chipY, chipRadius, borderPaint)

            val secEmoji = CategoryMapping.effectiveEmojiFor(secondaryCat) ?: DEFAULT_FALLBACK_EMOJI
            val chipTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = chipRadius * 1.05f
                textAlign = Paint.Align.CENTER
            }
            val chipFontMetrics = chipTextPaint.fontMetrics
            val chipYOffset = (chipFontMetrics.descent - chipFontMetrics.ascent) / 2f - chipFontMetrics.descent
            canvas.drawText(secEmoji, chipX, chipY + chipYOffset, chipTextPaint)

            // Tertiary chip at bottom-right if 3+ categories
            if (tertiaryCat != null) {
                val tertChipX = sizePx - radius * 0.58f
                val tertChipY = sizePx - radius * 0.52f
                val tertColor = runCatching { CategoryMapping.colorFor(tertiaryCat).toArgb() }
                    .getOrDefault(android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX))
                paint.color = tertColor
                canvas.drawCircle(tertChipX, tertChipY, chipRadius, paint)
                canvas.drawCircle(tertChipX, tertChipY, chipRadius, borderPaint)

                val tertEmoji = CategoryMapping.effectiveEmojiFor(tertiaryCat) ?: DEFAULT_FALLBACK_EMOJI
                canvas.drawText(tertEmoji, tertChipX, tertChipY + chipYOffset, chipTextPaint)
            }

            return bitmap
        }

        // Stage 3: Single Emoji for Category Third
        val effectiveCategory = category?.takeIf { it.isNotBlank() && !it.equals("Uncategorized", ignoreCase = true) }
            ?: CategoryMapping.getCategory(merchantName)

        val emoji = CategoryMapping.effectiveEmojiFor(effectiveCategory)
        if (emoji != null) {
            val categoryColor = CategoryMapping.colorFor(effectiveCategory)
            val bgColor = runCatching { categoryColor.toArgb() }
                .getOrDefault(android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX))

            paint.color = bgColor
            canvas.drawCircle(radius, radius, radius, paint)

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sizePx * 0.45f
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val yOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
            canvas.drawText(emoji, radius, radius + yOffset, textPaint)

            return bitmap
        }

        // Stage 4: Default for Transaction Type Fourth
        if (transactionType == TransactionType.INCOME) {
            val bgColor = android.graphics.Color.parseColor("#4CAF50")
            paint.color = bgColor
            canvas.drawCircle(radius, radius, radius, paint)

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sizePx * 0.45f
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val yOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
            canvas.drawText("💵", radius, radius + yOffset, textPaint)

            return bitmap
        }

        // Stage 5: Default Fallback Fifth
        val bgColor = android.graphics.Color.parseColor(DEFAULT_FALLBACK_COLOR_HEX)
        paint.color = bgColor
        canvas.drawCircle(radius, radius, radius, paint)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * 0.45f
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val yOffset = (fontMetrics.descent - fontMetrics.ascent) / 2f - fontMetrics.descent
        canvas.drawText(DEFAULT_FALLBACK_EMOJI, radius, radius + yOffset, textPaint)

        return bitmap
    }
}
