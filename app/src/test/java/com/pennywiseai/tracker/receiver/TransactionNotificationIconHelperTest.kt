package com.pennywiseai.tracker.receiver

import android.content.Context
import com.pennywiseai.tracker.ui.icons.BrandIcons
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransactionNotificationIconHelperTest {

    @Test
    fun `brand icon resolves for known merchants`() {
        val swiggyRes = BrandIcons.getIconResource("Swiggy")
        assertNotNull(swiggyRes)

        val amazonRes = BrandIcons.getIconResource("Amazon")
        assertNotNull(amazonRes)

        val uberRes = BrandIcons.getIconResource("Uber")
        assertNotNull(uberRes)
    }

    @Test
    fun `category emoji resolves for known categories`() {
        assertEquals("🍽️", CategoryMapping.effectiveEmojiFor("Food & Dining"))
        assertEquals("🛒", CategoryMapping.effectiveEmojiFor("Groceries"))
        assertEquals("🚗", CategoryMapping.effectiveEmojiFor("Transportation"))
        assertEquals("🏷️", CategoryMapping.effectiveEmojiFor("Others"))
    }

    @Test
    fun `circular icon helper constants and fallback are valid`() {
        assertEquals(192, TransactionNotificationIconHelper.NOTIFICATION_ICON_SIZE_PX)
        assertEquals("🏷️", CategoryMapping.effectiveEmojiFor("Others"))
    }
}
