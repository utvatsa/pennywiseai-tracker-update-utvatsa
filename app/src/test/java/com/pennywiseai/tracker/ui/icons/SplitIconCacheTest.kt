package com.pennywiseai.tracker.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SplitIconCacheTest {

    @Before
    fun setUp() {
        SplitIconCache.invalidateAll()
        CategoryMapping.userStyles.clear()
    }

    @After
    fun tearDown() {
        SplitIconCache.invalidateAll()
        CategoryMapping.userStyles.clear()
    }

    @Test
    fun `empty categories returns null`() {
        val config = SplitIconCache.resolveConfig(emptyList())
        assertNull(config)
    }

    @Test
    fun `two categories creates primary and single secondary left with custom emojis`() {
        CategoryMapping.userStyles["CatA"] = CategoryMapping.UserStyle("#7c6af7", "💻")
        CategoryMapping.userStyles["CatB"] = CategoryMapping.UserStyle("#4CAF50", "💵")

        val config = checkNotNull(SplitIconCache.resolveConfig(listOf("CatA", "CatB")))
        assertEquals("CatA", config.primary.category)
        assertEquals("💻", config.primary.emoji)
        assertNull(config.primary.vectorIcon)

        val secondaryLeft = checkNotNull(config.secondaryLeft)
        assertEquals("CatB", secondaryLeft.category)
        assertEquals("💵", secondaryLeft.emoji)
        assertNull(secondaryLeft.vectorIcon)

        assertNull(config.secondaryRight)
        assertNotNull(config.gradientBrush)
    }

    @Test
    fun `builtin category resolves its default emoji`() {
        // "Food & Dining" resolves its default built-in emoji "🍽️"
        val config = checkNotNull(SplitIconCache.resolveConfig(listOf("Food & Dining", "Others")))
        assertEquals("Food & Dining", config.primary.category)
        assertEquals("🍽️", config.primary.emoji)
    }

    @Test
    fun `custom category without emoji falls back to default vector icon`() {
        val config = checkNotNull(SplitIconCache.resolveConfig(listOf("UnknownCustomCat", "Others")))
        assertEquals("UnknownCustomCat", config.primary.category)
        assertNull(config.primary.emoji)
        assertNotNull(config.primary.vectorIcon)
    }

    @Test
    fun `three categories populates primary, secondary left, and secondary right`() {
        CategoryMapping.userStyles["CatA"] = CategoryMapping.UserStyle("#7c6af7", "💻")
        CategoryMapping.userStyles["CatB"] = CategoryMapping.UserStyle("#4CAF50", "💵")
        CategoryMapping.userStyles["CatC"] = CategoryMapping.UserStyle("#5AC85A", "🛒")

        val config = checkNotNull(
            SplitIconCache.resolveConfig(listOf("CatA", "CatB", "CatC"))
        )
        assertEquals("CatA", config.primary.category)
        assertEquals("💻", config.primary.emoji)

        assertEquals("CatB", config.secondaryLeft?.category)
        assertEquals("💵", config.secondaryLeft?.emoji)

        assertEquals("CatC", config.secondaryRight?.category)
        assertEquals("🛒", config.secondaryRight?.emoji)
    }

    @Test
    fun `four categories excludes fourth category from badge icon`() {
        val config = checkNotNull(
            SplitIconCache.resolveConfig(listOf("Cat1", "Cat2", "Cat3", "Cat4"))
        )
        assertEquals("Cat1", config.primary.category)
        assertEquals("Cat2", config.secondaryLeft?.category)
        assertEquals("Cat3", config.secondaryRight?.category)
        assertEquals("Cat1||Cat2||Cat3", config.comboKey)
    }

    @Test
    fun `multiple transactions with same category combination reuse the exact same cached config`() {
        // Transaction 1 (id=101) and Transaction 2 (id=202) both have CatA + CatB
        val configTxn1 = SplitIconCache.resolveConfig(listOf("CatA", "CatB"), transactionId = 101L)
        val configTxn2 = SplitIconCache.resolveConfig(listOf("CatA", "CatB"), transactionId = 202L)

        assertNotNull(configTxn1)
        assertNotNull(configTxn2)
        // Same memory reference reused without re-creating or re-allocating
        assertSame(configTxn1, configTxn2)
    }

    @Test
    fun `reordering categories changes priority order correctly and creates distinct combo`() {
        CategoryMapping.userStyles["CatA"] = CategoryMapping.UserStyle("#7c6af7", "💻")
        CategoryMapping.userStyles["CatB"] = CategoryMapping.UserStyle("#4CAF50", "💵")
        CategoryMapping.userStyles["CatC"] = CategoryMapping.UserStyle("#5AC85A", "🛒")

        // Combination 1: CatA (1st) -> CatB (2nd) -> CatC (3rd)
        val config1 = checkNotNull(
            SplitIconCache.resolveConfig(listOf("CatA", "CatB", "CatC"))
        )
        assertEquals("💻", config1.primary.emoji)
        assertEquals("💵", config1.secondaryLeft?.emoji)
        assertEquals("🛒", config1.secondaryRight?.emoji)

        // Combination 2: CatB (1st) -> CatC (2nd) -> CatA (3rd)
        val config2 = checkNotNull(
            SplitIconCache.resolveConfig(listOf("CatB", "CatC", "CatA"))
        )
        assertEquals("💵", config2.primary.emoji)
        assertEquals("🛒", config2.secondaryLeft?.emoji)
        assertEquals("💻", config2.secondaryRight?.emoji)

        assertNotEquals(config1.comboKey, config2.comboKey)
    }

    @Test
    fun `invalidateCombo evicts specific category combination`() {
        val combo = listOf("CatA", "CatB")
        SplitIconCache.resolveConfig(combo)
        assertNotNull(SplitIconCache.get(SplitIconCache.getComboKey(combo)))

        SplitIconCache.invalidateCombo(combo)
        assertNull(SplitIconCache.get(SplitIconCache.getComboKey(combo)))
    }

    @Test
    fun `invalidateCategory evicts all combinations referencing the category`() {
        val combo1 = listOf("CatA", "CatB")
        val combo2 = listOf("CatC", "CatD")
        SplitIconCache.resolveConfig(combo1)
        SplitIconCache.resolveConfig(combo2)

        assertNotNull(SplitIconCache.get(SplitIconCache.getComboKey(combo1)))
        assertNotNull(SplitIconCache.get(SplitIconCache.getComboKey(combo2)))

        SplitIconCache.invalidateCategory("CatA")

        assertNull(SplitIconCache.get(SplitIconCache.getComboKey(combo1)))
        assertNotNull(SplitIconCache.get(SplitIconCache.getComboKey(combo2)))
    }

    @Test
    fun `invalidateAll clears entire cache`() {
        val combo1 = listOf("CatA", "CatB")
        val combo2 = listOf("CatC", "CatD")
        SplitIconCache.resolveConfig(combo1)
        SplitIconCache.resolveConfig(combo2)

        SplitIconCache.invalidateAll()

        assertNull(SplitIconCache.get(SplitIconCache.getComboKey(combo1)))
        assertNull(SplitIconCache.get(SplitIconCache.getComboKey(combo2)))
    }
}
