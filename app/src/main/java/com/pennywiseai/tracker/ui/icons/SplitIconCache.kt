package com.pennywiseai.tracker.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SplitCategoryVisual(
    val category: String,
    val emoji: String? = null,
    val vectorIcon: ImageVector? = null,
    val color: Color
)

data class SplitIconConfig(
    val comboKey: String,
    val primary: SplitCategoryVisual,
    val secondaryLeft: SplitCategoryVisual?,
    val secondaryRight: SplitCategoryVisual?,
    val gradientBrush: Brush
)

/**
 * In-memory LRU cache for composite split category icons.
 *
 * Icons are keyed strictly by their ordered category combination (top 3 categories),
 * allowing any transaction sharing the same combination (e.g. CatA + CatB, CatA + CatB + CatC)
 * to instantly reuse the cached logo without redundant allocations or computations.
 */
object SplitIconCache {
    private const val MAX_SIZE = 512
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, SplitIconConfig>(MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SplitIconConfig>?): Boolean {
            return size > MAX_SIZE
        }
    }

    /**
     * Observable version state that increments on cache invalidation,
     * triggering automatic recomposition for any active Compose split icons.
     */
    var version by mutableIntStateOf(0)
        private set

    fun get(key: String): SplitIconConfig? = synchronized(lock) {
        cache[key]
    }

    fun put(key: String, config: SplitIconConfig) = synchronized(lock) {
        cache[key] = config
    }

    /**
     * Builds a normalized combination key representing the ordered top categories.
     * Takes up to the first 3 categories (any 4th+ categories are excluded from the badge).
     */
    fun getComboKey(categories: List<String>): String {
        return categories.take(3).joinToString("||")
    }

    fun buildKey(transactionId: Long? = null, categories: List<String>): String {
        return getComboKey(categories)
    }

    fun buildKey(categories: List<String>): String {
        return getComboKey(categories)
    }

    /**
     * Resolves the visual representation for a single category using PennyWise's
     * existing CategoryMapping system (custom emoji if assigned, otherwise built-in icon & color).
     */
    fun resolveVisual(category: String): SplitCategoryVisual {
        val emoji = CategoryMapping.effectiveEmojiFor(category)
        val categoryInfo = CategoryMapping.categories[category]
        val vectorIcon = if (emoji == null) {
            categoryInfo?.icon ?: Icons.Default.Category
        } else {
            null
        }
        val color = CategoryMapping.colorFor(category)
        return SplitCategoryVisual(
            category = category,
            emoji = emoji,
            vectorIcon = vectorIcon,
            color = color
        )
    }

    /**
     * Resolves and caches the [SplitIconConfig] for the given ordered [categories].
     * Any transaction with the same ordered top categories will reuse the exact same cached config.
     *
     * The 1st category is Primary (top-center large glyph, dominates background).
     * The 2nd category is Secondary Left.
     * The 3rd category is Secondary Right.
     * 4th+ categories are excluded from the badge icon.
     */
    fun resolveConfig(categories: List<String>, transactionId: Long? = null): SplitIconConfig? {
        if (categories.isEmpty()) return null
        val comboKey = getComboKey(categories)
        get(comboKey)?.let { return it }

        val visuals = categories.take(3).map { cat -> resolveVisual(cat) }

        val primary = visuals.firstOrNull() ?: return null
        val secondaryLeft = visuals.getOrNull(1)
        val secondaryRight = visuals.getOrNull(2)

        val bottomColor = secondaryLeft?.color ?: secondaryRight?.color ?: primary.color.copy(alpha = 0.6f)
        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                primary.color,
                primary.color.copy(alpha = 0.85f),
                bottomColor.copy(alpha = 0.9f)
            )
        )

        val config = SplitIconConfig(
            comboKey = comboKey,
            primary = primary,
            secondaryLeft = secondaryLeft,
            secondaryRight = secondaryRight,
            gradientBrush = gradientBrush
        )
        put(comboKey, config)
        return config
    }

    /**
     * Overload taking transactionId first for backward compatibility with existing call sites.
     */
    fun resolveConfig(transactionId: Long?, categories: List<String>): SplitIconConfig? {
        return resolveConfig(categories, transactionId)
    }

    /**
     * Invalidate cached icon entries for a specific category combination.
     */
    fun invalidateCombo(comboKey: String) {
        synchronized(lock) {
            cache.remove(comboKey)
        }
        triggerRecomposition()
    }

    fun invalidateCombo(categories: List<String>) {
        invalidateCombo(getComboKey(categories))
    }

    /**
     * Invalidate cached icon entries for a specific transaction ID.
     */
    fun invalidateTransaction(transactionId: Long) {
        triggerRecomposition()
    }

    /**
     * Invalidate cached entries referencing a specific category name across all combinations.
     */
    fun invalidateCategory(categoryName: String) {
        synchronized(lock) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val config = entry.value
                if (config.primary.category == categoryName ||
                    config.secondaryLeft?.category == categoryName ||
                    config.secondaryRight?.category == categoryName
                ) {
                    iterator.remove()
                }
            }
        }
        triggerRecomposition()
    }

    /**
     * Invalidate all cached split icons and force recomposition.
     */
    fun invalidateAll() {
        synchronized(lock) {
            cache.clear()
        }
        triggerRecomposition()
    }

    fun triggerRecomposition() {
        version++
    }
}
