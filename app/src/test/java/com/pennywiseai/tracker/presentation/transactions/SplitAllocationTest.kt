package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.ui.components.SplitItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SplitAllocationTest {
    private val txn = TransactionEntity(
        id = 1,
        amount = BigDecimal("1000.00"),
        merchantName = "Supermarket",
        category = "Groceries",
        currency = "INR",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "hash1"
    )

    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getTransactionById(1) } returns txn
        coEvery { hasSplits(1) } returns false
        every { getSplitsForTransaction(1) } returns flowOf(emptyList())
    }

    private val categoryRepo = mockk<CategoryRepository>(relaxed = true) {
        every { getVisibleExpenseCategories() } returns flowOf(emptyList())
    }

    private val prefs = mockk<com.pennywiseai.tracker.data.preferences.UserPreferencesRepository>(relaxed = true) {
        every { unifiedCurrencyMode } returns flowOf(false)
    }

    private fun createViewModel(txRepo: TransactionRepository = transactions) = TransactionDetailViewModel(
        txRepo,
        mockk(relaxed = true),
        mockk(relaxed = true),
        categoryRepo,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        prefs,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true)
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `enableSplitMode initializes category 1 with full amount and category 2 with zero`() {
        val vm = createViewModel()
        vm.loadTransaction(1)
        vm.enableSplitMode()

        val splits = vm.splits.value
        assertEquals(2, splits.size)
        assertEquals("Groceries", splits[0].category)
        assertEquals(BigDecimal("1000.00"), splits[0].amount)
        assertEquals("Others", splits[1].category)
        assertEquals(BigDecimal.ZERO, splits[1].amount)
        assertTrue(vm.showSplitEditor.value)
    }

    @Test
    fun `enableSplitMode with Others category falls back to non-Others category`() {
        val othersTxn = txn.copy(category = "Others")
        val txRepo = mockk<TransactionRepository>(relaxed = true) {
            coEvery { getTransactionById(2) } returns othersTxn
            coEvery { hasSplits(2) } returns false
            every { getSplitsForTransaction(2) } returns flowOf(emptyList())
        }
        val vm = createViewModel(txRepo)
        vm.loadTransaction(2)
        vm.enableSplitMode()

        val splits = vm.splits.value
        assertEquals(2, splits.size)
        assertEquals("Others", splits[0].category)
        assertEquals(BigDecimal("1000.00"), splits[0].amount)
        assertEquals("General", splits[1].category)
        assertEquals(BigDecimal.ZERO, splits[1].amount)
    }

    @Test
    fun `validateSplits requires all splits to have positive amounts`() {
        val vm = createViewModel()
        vm.loadTransaction(1)
        vm.enableSplitMode()

        // Initially split 2 has 0 amount, so validation fails
        assertFalse(vm.validateSplits())

        // When user edits split 1 and fills remaining on split 2
        vm.updateSplits(
            listOf(
                SplitItem(id = 0, category = "Groceries", amount = BigDecimal("700.00")),
                SplitItem(id = 0, category = "Others", amount = BigDecimal("300.00"))
            )
        )

        assertTrue(vm.validateSplits())
    }

    @Test
    fun `validateSplits enforces total match within tolerance`() {
        val vm = createViewModel()
        vm.loadTransaction(1)
        vm.enableSplitMode()

        // Sum is 900 vs 1000 -> invalid
        vm.updateSplits(
            listOf(
                SplitItem(id = 0, category = "Groceries", amount = BigDecimal("700.00")),
                SplitItem(id = 0, category = "Others", amount = BigDecimal("200.00"))
            )
        )
        assertFalse(vm.validateSplits())

        // Sum is 1000 -> valid
        vm.updateSplits(
            listOf(
                SplitItem(id = 0, category = "Groceries", amount = BigDecimal("700.00")),
                SplitItem(id = 0, category = "Others", amount = BigDecimal("300.00"))
            )
        )
        assertTrue(vm.validateSplits())
    }
}
