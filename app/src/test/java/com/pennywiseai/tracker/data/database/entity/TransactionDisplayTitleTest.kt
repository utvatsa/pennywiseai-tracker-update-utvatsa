package com.pennywiseai.tracker.data.database.entity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionDisplayTitleTest {

    private fun sampleTx(
        title: String? = null,
        merchant: String = "UPI-9876543210@paytm",
    ) = TransactionEntity(
        id = 1L,
        amount = BigDecimal("100.00"),
        merchantName = merchant,
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.now(),
        transactionHash = "hash-1",
        title = title
    )

    @Test
    fun `tier 1 - custom title wins over alias, contact, and raw merchant`() {
        val tx = sampleTx(title = "Team Lunch at Diner")
        val result = tx.displayTitle(
            merchantDisplay = { "Global Diner Alias" }
        )
        assertEquals("Team Lunch at Diner", result)
    }

    @Test
    fun `tier 1 - blank or whitespace title falls back to tier 2`() {
        val txEmpty = sampleTx(title = "")
        val txBlank = sampleTx(title = "   ")
        assertEquals("Global Diner Alias", txEmpty.displayTitle { "Global Diner Alias" })
        assertEquals("Global Diner Alias", txBlank.displayTitle { "Global Diner Alias" })
    }

    @Test
    fun `tier 2 - merchant alias wins when title is null`() {
        val tx = sampleTx(title = null)
        val result = tx.displayTitle(
            merchantDisplay = { "Global Diner Alias" }
        )
        assertEquals("Global Diner Alias", result)
    }

    @Test
    fun `tier 3 and 4 - falls back to raw merchant name when no title and display function returns raw`() {
        val tx = sampleTx(title = null, merchant = "RAW_MERCHANT_NAME")
        val result = tx.displayTitle { it }
        assertEquals("RAW_MERCHANT_NAME", result)
    }

    @Test
    fun `tier 3 and 4 - falls back to raw merchant name when default merchantDisplay lambda is used`() {
        val tx = sampleTx(title = null, merchant = "RAW_MERCHANT_NAME")
        val result = tx.displayTitle()
        assertEquals("RAW_MERCHANT_NAME", result)
    }
}
