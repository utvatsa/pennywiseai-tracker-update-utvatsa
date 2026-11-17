package com.pennywiseai.tracker.data.database.converter

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun toTransactionType_parsesAllKnownTypes() {
        assertEquals(TransactionType.INCOME, converters.toTransactionType("INCOME"))
        assertEquals(TransactionType.EXPENSE, converters.toTransactionType("EXPENSE"))
        assertEquals(TransactionType.CREDIT, converters.toTransactionType("CREDIT"))
        assertEquals(TransactionType.TRANSFER, converters.toTransactionType("TRANSFER"))
        assertEquals(TransactionType.INVESTMENT, converters.toTransactionType("INVESTMENT"))
        assertEquals(TransactionType.CC_REPAYMENT, converters.toTransactionType("CC_REPAYMENT"))
    }

    @Test
    fun toTransactionType_handlesLegacyAliases() {
        assertEquals(TransactionType.CC_REPAYMENT, converters.toTransactionType("CC_BILL_PAYMENT"))
        assertEquals(TransactionType.CC_REPAYMENT, converters.toTransactionType("REPAYMENT"))
    }

    @Test
    fun toTransactionType_fallsBackToExpenseOnUnknown() {
        assertEquals(TransactionType.EXPENSE, converters.toTransactionType("UNKNOWN_FOOBAR"))
    }

    @Test
    fun fromTransactionType_serializesCorrectly() {
        assertEquals("CC_REPAYMENT", converters.fromTransactionType(TransactionType.CC_REPAYMENT))
        assertEquals("CREDIT", converters.fromTransactionType(TransactionType.CREDIT))
    }
}
