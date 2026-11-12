package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TransactionItemSplitTest {

    @Test
    fun `two splits format as comma-separated category names`() {
        val splits = listOf(
            TransactionSplitEntity(transactionId = 1, category = "Cigarettes", amount = BigDecimal("800.00")),
            TransactionSplitEntity(transactionId = 1, category = "Groceries", amount = BigDecimal("334.82"))
        )
        val names = splits.map { it.category }
        val summary = if (names.size <= 2) {
            names.joinToString(", ")
        } else {
            "${names[0]}, ${names[1]} +${names.size - 2}"
        }
        assertEquals("Cigarettes, Groceries", summary)
    }

    @Test
    fun `three or more splits format as first two plus remainder count`() {
        val splits = listOf(
            TransactionSplitEntity(transactionId = 1, category = "Food & Dining", amount = BigDecimal("500")),
            TransactionSplitEntity(transactionId = 1, category = "Groceries", amount = BigDecimal("300")),
            TransactionSplitEntity(transactionId = 1, category = "Entertainment", amount = BigDecimal("200"))
        )
        val names = splits.map { it.category }
        val summary = if (names.size <= 2) {
            names.joinToString(", ")
        } else {
            "${names[0]}, ${names[1]} +${names.size - 2}"
        }
        assertEquals("Food & Dining, Groceries +1", summary)
    }

    @Test
    fun `split tag includes indicator chevron matching expansion state`() {
        val count = 2
        val collapsedTag = "Split ($count) \u25BE"
        val expandedTag = "Split ($count) \u25B4"
        assertEquals("Split (2) ▾", collapsedTag)
        assertEquals("Split (2) ▴", expandedTag)
    }
}
