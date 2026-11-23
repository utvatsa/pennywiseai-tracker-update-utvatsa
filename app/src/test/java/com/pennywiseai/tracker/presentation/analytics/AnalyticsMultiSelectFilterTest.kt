package com.pennywiseai.tracker.presentation.analytics

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsMultiSelectFilterTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context = mockk<Context>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val currencyConversionService = mockk<CurrencyConversionService>(relaxed = true)
    private val accountBalanceRepository = mockk<AccountBalanceRepository>(relaxed = true)
    private val profileRepository = mockk<ProfileRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val entitlementGate = mockk<EntitlementGate>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: AnalyticsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        every { userPreferencesRepository.selectedProfileId } returns flowOf(null)
        every { profileRepository.observeAllProfiles() } returns flowOf(emptyList())
        every { userPreferencesRepository.budgetCycleStartDay } returns flowOf(1)
        every { userPreferencesRepository.countCreditCardAsExpense } returns flowOf(false)
        every { userPreferencesRepository.baseCurrency } returns flowOf("INR")
        every { userPreferencesRepository.unifiedCurrencyMode } returns flowOf(false)
        every { userPreferencesRepository.displayCurrency } returns flowOf("INR")
        every { userPreferencesRepository.getAnalyticsChartType() } returns flowOf(null)
        every { userPreferencesRepository.getAnalyticsGroupBy() } returns flowOf(null)
        every { entitlementGate.isProEntitled } returns MutableStateFlow(false)
        every { accountBalanceRepository.getAllLatestBalances() } returns flowOf(emptyList())
        every { tagRepository.observeTransactionTagNames() } returns flowOf(emptyMap())

        viewModel = AnalyticsViewModel(
            context = context,
            transactionRepository = transactionRepository,
            userPreferencesRepository = userPreferencesRepository,
            currencyConversionService = currencyConversionService,
            accountBalanceRepository = accountBalanceRepository,
            profileRepository = profileRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            entitlementGate = entitlementGate,
            savedStateHandle = savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial category and account filter states are null`() {
        assertNull(viewModel.categoryFilter.value)
        assertNull(viewModel.categoriesFilter.value)
        assertNull(viewModel.accountFilter.value)
        assertNull(viewModel.accountsFilter.value)
    }

    @Test
    fun `toggleCategory excludes single category from all available`() = runTest {
        val available = listOf("Food", "Shopping", "Transport")
        viewModel.toggleCategory("Food", available)

        assertEquals(listOf("Shopping", "Transport"), viewModel.categoriesFilter.value)
        assertNull(viewModel.categoryFilter.value)
    }

    @Test
    fun `toggleCategory excludes multiple categories`() = runTest {
        val available = listOf("Food", "Shopping", "Transport", "Bills")
        viewModel.toggleCategory("Food", available)
        viewModel.toggleCategory("Transport", available)

        assertEquals(listOf("Shopping", "Bills"), viewModel.categoriesFilter.value)
    }

    @Test
    fun `toggleCategory re-checking items normalizes to null when all selected`() = runTest {
        val available = listOf("Food", "Shopping", "Transport")
        viewModel.toggleCategory("Food", available)
        assertEquals(listOf("Shopping", "Transport"), viewModel.categoriesFilter.value)

        viewModel.toggleCategory("Food", available)
        assertNull("All items re-checked resets filter to null (ALL)", viewModel.categoriesFilter.value)
    }

    @Test
    fun `toggleCategory keeps emptyList when all items unticked (None state)`() = runTest {
        val available = listOf("Food", "Shopping")
        viewModel.toggleCategory("Food", available)
        assertEquals(listOf("Shopping"), viewModel.categoriesFilter.value)

        viewModel.toggleCategory("Shopping", available)
        assertEquals(emptyList<String>(), viewModel.categoriesFilter.value)
    }

    @Test
    fun `hierarchical toggleCategory unselecting child also unselects parent`() = runTest {
        val testCats = listOf(
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 1, name = "Food & Dining", color = "#FF0000", parentId = null),
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 2, name = "Groceries", color = "#00FF00", parentId = 1),
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 3, name = "Restaurants", color = "#0000FF", parentId = 1),
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 4, name = "Shopping", color = "#FFFF00", parentId = null)
        )
        every { categoryRepository.getAllCategories() } returns flowOf(testCats)
        val vm = AnalyticsViewModel(
            context = context,
            transactionRepository = transactionRepository,
            userPreferencesRepository = userPreferencesRepository,
            currencyConversionService = currencyConversionService,
            accountBalanceRepository = accountBalanceRepository,
            profileRepository = profileRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            entitlementGate = entitlementGate,
            savedStateHandle = savedStateHandle
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val available = listOf("Food & Dining", "Groceries", "Restaurants", "Shopping")

        // Unselect "Restaurants" -> "Restaurants" AND "Food & Dining" parent are removed
        vm.toggleCategory("Restaurants", available)
        assertEquals(listOf("Groceries", "Shopping"), vm.categoriesFilter.value)

        // Reselect "Restaurants" -> all children selected -> parent re-selected -> all selected (null)
        vm.toggleCategory("Restaurants", available)
        assertNull(vm.categoriesFilter.value)
    }

    @Test
    fun `hierarchical toggleCategory clicking parent toggles parent and all children`() = runTest {
        val testCats = listOf(
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 1, name = "Food & Dining", color = "#FF0000", parentId = null),
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 2, name = "Groceries", color = "#00FF00", parentId = 1),
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 3, name = "Restaurants", color = "#0000FF", parentId = 1),
            com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 4, name = "Shopping", color = "#FFFF00", parentId = null)
        )
        every { categoryRepository.getAllCategories() } returns flowOf(testCats)
        val vm = AnalyticsViewModel(
            context = context,
            transactionRepository = transactionRepository,
            userPreferencesRepository = userPreferencesRepository,
            currencyConversionService = currencyConversionService,
            accountBalanceRepository = accountBalanceRepository,
            profileRepository = profileRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            entitlementGate = entitlementGate,
            savedStateHandle = savedStateHandle
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val available = listOf("Food & Dining", "Groceries", "Restaurants", "Shopping")

        // When all selected: clicking parent unselects parent and ALL its children
        vm.toggleCategory("Food & Dining", available)
        assertEquals(listOf("Shopping"), vm.categoriesFilter.value)

        // When partially/none selected: clicking parent selects parent and ALL its children
        vm.toggleCategory("Food & Dining", available)
        assertNull(vm.categoriesFilter.value)
    }

    @Test
    fun `toggleAllCategories toggles between All and None`() = runTest {
        val available = listOf("Food", "Shopping", "Transport")
        // Initial state is All (null) -> toggleAllCategories should unselect all (emptyList)
        viewModel.toggleAllCategories(available)
        assertEquals(emptyList<String>(), viewModel.categoriesFilter.value)

        // When None (emptyList) -> toggleAllCategories should select all (null)
        viewModel.toggleAllCategories(available)
        assertNull(viewModel.categoriesFilter.value)

        // When partial/custom selected -> toggleAllCategories should select all (null)
        viewModel.toggleCategory("Food", available)
        assertEquals(listOf("Shopping", "Transport"), viewModel.categoriesFilter.value)
        viewModel.toggleAllCategories(available)
        assertNull(viewModel.categoriesFilter.value)
    }

    @Test
    fun `setCategoryFilter clears multi-category filter and sets single category`() = runTest {
        val available = listOf("Food", "Shopping", "Bills")
        viewModel.toggleCategory("Food", available)
        assertEquals(listOf("Shopping", "Bills"), viewModel.categoriesFilter.value)

        viewModel.setCategoryFilter("Food")
        assertEquals("Food", viewModel.categoryFilter.value)
        assertNull(viewModel.categoriesFilter.value)
    }

    @Test
    fun `clearCategoryFilter clears both single and multi-category filters`() = runTest {
        val available = listOf("Food", "Shopping")
        viewModel.toggleCategory("Food", available)
        viewModel.clearCategoryFilter()

        assertNull(viewModel.categoryFilter.value)
        assertNull(viewModel.categoriesFilter.value)
    }

    @Test
    fun `toggleAccount excludes single account from all available`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678", "SBI_9012")
        viewModel.toggleAccount("HDFC_1234", available)

        assertEquals(listOf("ICICI_5678", "SBI_9012"), viewModel.accountsFilter.value)
        assertNull(viewModel.accountFilter.value)
    }

    @Test
    fun `toggleAccount excludes multiple accounts`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678", "SBI_9012", "AXIS_3456")
        viewModel.toggleAccount("HDFC_1234", available)
        viewModel.toggleAccount("AXIS_3456", available)

        assertEquals(listOf("ICICI_5678", "SBI_9012"), viewModel.accountsFilter.value)
    }

    @Test
    fun `toggleAccount re-checking items normalizes to null when all selected`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678")
        viewModel.toggleAccount("HDFC_1234", available)
        assertEquals(listOf("ICICI_5678"), viewModel.accountsFilter.value)

        viewModel.toggleAccount("HDFC_1234", available)
        assertNull("All accounts re-checked resets accountsFilter to null (ALL)", viewModel.accountsFilter.value)
    }

    @Test
    fun `toggleAccount keeps emptyList when all accounts unticked (None state)`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678")
        viewModel.toggleAccount("HDFC_1234", available)
        viewModel.toggleAccount("ICICI_5678", available)

        assertEquals(emptyList<String>(), viewModel.accountsFilter.value)
    }

    @Test
    fun `toggleAllAccounts toggles between All and None`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678", "SBI_9012")
        // Initial state is All (null) -> toggleAllAccounts should unselect all (emptyList)
        viewModel.toggleAllAccounts(available)
        assertEquals(emptyList<String>(), viewModel.accountsFilter.value)

        // When None (emptyList) -> toggleAllAccounts should select all (null)
        viewModel.toggleAllAccounts(available)
        assertNull(viewModel.accountsFilter.value)

        // When partial/custom selected -> toggleAllAccounts should select all (null)
        viewModel.toggleAccount("HDFC_1234", available)
        assertEquals(listOf("ICICI_5678", "SBI_9012"), viewModel.accountsFilter.value)
        viewModel.toggleAllAccounts(available)
        assertNull(viewModel.accountsFilter.value)
    }

    @Test
    fun `organizeHierarchicalCategories groups children directly beneath parent`() {
        val available = listOf("Food & Dining", "Coffee", "Groceries", "Shopping", "Clothing", "Bills")
        val parentOf = mapOf(
            "Coffee" to "Food & Dining",
            "Groceries" to "Food & Dining",
            "Clothing" to "Shopping"
        )

        val organized = organizeHierarchicalCategories(available, parentOf)

        // Top levels sorted: Bills, Food & Dining, Shopping
        // Children nested under their respective parents
        val expected = listOf(
            "Bills",
            "Food & Dining",
            "Coffee",
            "Groceries",
            "Shopping",
            "Clothing"
        )
        assertEquals(expected, organized)
    }

    @Test
    fun `organizeHierarchicalCategories handles orphan children without parent in available`() {
        val available = listOf("Coffee", "Bills")
        val parentOf = mapOf("Coffee" to "Food & Dining")

        val organized = organizeHierarchicalCategories(available, parentOf)
        // Bills is top-level; Coffee has no parent in available, so placed as orphan
        assertEquals(listOf("Bills", "Coffee"), organized)
    }

    @Test
    fun `setAccountFilter clears multi-account filter and sets single account`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678")
        viewModel.toggleAccount("HDFC_1234", available)
        assertEquals(listOf("ICICI_5678"), viewModel.accountsFilter.value)

        viewModel.setAccountFilter("HDFC_1234")
        assertEquals("HDFC_1234", viewModel.accountFilter.value)
        assertNull(viewModel.accountsFilter.value)
    }

    @Test
    fun `clearAccountFilter clears both single and multi-account filters`() = runTest {
        val available = listOf("HDFC_1234", "ICICI_5678")
        viewModel.toggleAccount("HDFC_1234", available)
        viewModel.clearAccountFilter()

        assertNull(viewModel.accountFilter.value)
        assertNull(viewModel.accountsFilter.value)
    }
}
