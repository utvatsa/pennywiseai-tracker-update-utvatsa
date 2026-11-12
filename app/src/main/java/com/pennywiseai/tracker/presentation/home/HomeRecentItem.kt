package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import java.math.BigDecimal
import java.time.LocalDateTime

sealed class HomeRecentItem {
    abstract val sortTime: LocalDateTime

    data class SingleTransaction(
        val transaction: TransactionEntity,
        val convertedAmount: BigDecimal? = null,
        val splits: List<TransactionSplitEntity> = emptyList(),
        val splitCategories: List<String> = splits.map { it.category }
    ) : HomeRecentItem() {
        override val sortTime: LocalDateTime get() = transaction.dateTime
    }

    data class GroupItem(
        val group: TransactionGroupEntity,
        val transactions: List<TransactionEntity>,
        val convertedAmounts: Map<Long, BigDecimal> = emptyMap()
    ) : HomeRecentItem() {
        override val sortTime: LocalDateTime
            get() = transactions.map { it.dateTime }.maxOrNull() ?: group.updatedAt
    }
}
