package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.icons.SplitCategoryVisual
import com.pennywiseai.tracker.ui.icons.SplitIconCache

/**
 * Renders a composite logo badge for a transaction with multiple split categories.
 *
 * Visual hierarchy:
 * 1. 1st Category (Primary): Large glyph top-center, dominates the background color.
 * 2. 2nd Category: Small circular badge chip at bottom-left.
 * 3. 3rd Category: Small circular badge chip at bottom-right.
 * 4. 4th+ Categories: Excluded from icon to prevent visual clutter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitCategoryIcon(
    categories: List<String>,
    transactionId: Long? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showBackground: Boolean = true
) {
    // Read SplitIconCache.version so cache invalidations trigger recomposition
    val cacheVersion = SplitIconCache.version
    val config = remember(categories, cacheVersion) {
        SplitIconCache.resolveConfig(categories)
    } ?: return

    val primary = config.primary
    val secondaryLeft = config.secondaryLeft
    val secondaryRight = config.secondaryRight
    val gradientBrush = config.gradientBrush

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBackground) {
                    Modifier
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(gradientBrush)
                        .padding((size * 0.05f).coerceAtLeast(1.5.dp))
                } else {
                    Modifier
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(primary.color.copy(alpha = 0.2f))
                        .padding((size * 0.04f).coerceAtLeast(1.dp))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Primary Big Glyph (centered in upper portion)
        val primaryBoxSize = if (secondaryLeft != null) size * 0.48f else size * 0.54f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (secondaryLeft != null) size * 0.03f else 0.dp),
            contentAlignment = if (secondaryLeft != null) Alignment.TopCenter else Alignment.Center
        ) {
            VisualGlyph(
                visual = primary,
                box = primaryBoxSize,
                tint = Color.White
            )
        }

        // 2. Secondary Chips (circular badges at bottom corners, scaled 25% larger for readability)
        val chipDiameter = (size * 0.45f).coerceAtLeast(13.dp)
        val chipGlyphSize = chipDiameter * 0.72f

        // Bottom-Left Chip (Category 2)
        if (secondaryLeft != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = size * 0.02f, bottom = size * 0.02f)
                    .size(chipDiameter)
                    .clip(CircleShape)
                    .background(secondaryLeft.color.copy(alpha = 0.95f))
                    .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                VisualGlyph(
                    visual = secondaryLeft,
                    box = chipGlyphSize,
                    tint = Color.White
                )
            }
        }

        // Bottom-Right Chip (Category 3)
        if (secondaryRight != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = size * 0.02f, bottom = size * 0.02f)
                    .size(chipDiameter)
                    .clip(CircleShape)
                    .background(secondaryRight.color.copy(alpha = 0.95f))
                    .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                VisualGlyph(
                    visual = secondaryRight,
                    box = chipGlyphSize,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun VisualGlyph(
    visual: SplitCategoryVisual,
    box: Dp,
    tint: Color = Color.White
) {
    if (!visual.emoji.isNullOrBlank()) {
        EmojiGlyph(
            emoji = visual.emoji,
            box = box
        )
    } else if (visual.vectorIcon != null) {
        Icon(
            imageVector = visual.vectorIcon,
            contentDescription = visual.category,
            tint = tint,
            modifier = Modifier.size(box)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Category,
            contentDescription = visual.category,
            tint = tint,
            modifier = Modifier.size(box)
        )
    }
}
