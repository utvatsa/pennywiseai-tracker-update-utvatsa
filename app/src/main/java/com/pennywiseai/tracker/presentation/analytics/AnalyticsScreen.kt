package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.chipLabel
import com.pennywiseai.tracker.presentation.common.filterIcon
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.shortLabel
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.ListItemCardV2
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

private enum class CategoryViewType { CHART, LIST }

/** The Transactions slice an Analytics row drills into: its group plus the view's filters. */
data class TransactionsDrillDown(
    val category: String? = null,
    val merchant: String? = null,
    val account: String? = null,
    val tag: String? = null,
    /** A [TransactionTypeFilter] name; null (all types) for Spending, which Transactions can't show. */
    val type: String? = null,
    val period: String? = null,
    val currency: String? = null,
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onNavigateToTransactions: (TransactionsDrillDown) -> Unit = {},
    onNavigateToHome: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsStateWithLifecycle()
    val defaultTypeFilter by viewModel.defaultTypeFilter.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val availableCurrencies by viewModel.availableCurrencies.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val budgetCycleStartDay by viewModel.budgetCycleStartDay.collectAsStateWithLifecycle()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsStateWithLifecycle()
    val chartType by viewModel.selectedChartType.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val categoriesFilter by viewModel.categoriesFilter.collectAsStateWithLifecycle()
    val categoryParentOf by viewModel.categoryParentOf.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val accountFilter by viewModel.accountFilter.collectAsStateWithLifecycle()
    val accountsFilter by viewModel.accountsFilter.collectAsStateWithLifecycle()
    val accountOptions by viewModel.accountOptions.collectAsStateWithLifecycle()
    val isProEntitled by viewModel.isProEntitled.collectAsStateWithLifecycle()
    val groupBy by viewModel.groupBy.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var categoryViewType by rememberSaveable { mutableStateOf(CategoryViewType.CHART) }
    var tagViewType by rememberSaveable { mutableStateOf(CategoryViewType.CHART) }
    var showChartTypeSelector by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showGroupByMenu by remember { mutableStateOf(false) }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // Cache expensive operations
    val timePeriods = remember { TimePeriod.values().toList() }
    val customRangeLabel = remember(customDateRange, AppDates.dateFormatStyle) {
        DateRangeUtils.formatDateRange(customDateRange)
    }
    val periodLabel = selectedPeriod.label
    val periodChipLabel = remember(selectedPeriod, budgetCycleStartDay, customRangeLabel, periodLabel) {
        selectedPeriod.chipLabel(budgetCycleStartDay, customRangeLabel, periodLabel)
    }
    // Carry the custom range through drill-down navigation so the Transactions
    // screen (and its CSV export) shows exactly the slice being viewed here.
    val navStartEpochDay = if (selectedPeriod == TimePeriod.CUSTOM) customDateRange?.first?.toEpochDay() else null
    val navEndEpochDay = if (selectedPeriod == TimePeriod.CUSTOM) customDateRange?.second?.toEpochDay() else null
    // Drill into Transactions for one group, keeping this view's period, currency,
    // type and category filter so the list matches the figure that was tapped.
    val drillDown: (TransactionsDrillDown) -> Unit = { group ->
        onNavigateToTransactions(
            group.copy(
                category = group.category ?: categoryFilter,
                type = transactionTypeFilter.takeIf { it.inTransactionsList }?.name,
                period = selectedPeriod.name,
                currency = selectedCurrency,
                startDateEpochDay = navStartEpochDay,
                endDateEpochDay = navEndEpochDay
            )
        )
    }
    val primaryVisibleCurrency = availableCurrencies.firstOrNull() ?: selectedCurrency
    val hasCurrencyFilter = !isUnifiedMode &&
        availableCurrencies.size > 1 &&
        selectedCurrency.isNotBlank() &&
        !selectedCurrency.equals(primaryVisibleCurrency, ignoreCase = true)
    val isCategoryFiltered = categoryFilter != null || (categoriesFilter != null && uiState.availableCategories.isNotEmpty() && categoriesFilter?.size != uiState.availableCategories.size)
    val isAccountFiltered = accountFilter != null || (accountsFilter != null && accountOptions.isNotEmpty() && accountsFilter?.size != accountOptions.size)
    val hasActiveAnalyticsFilter = selectedPeriod != TimePeriod.THIS_MONTH ||
        customDateRange != null ||
        transactionTypeFilter != defaultTypeFilter ||
        isCategoryFiltered ||
        isAccountFiltered ||
        hasCurrencyFilter

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.analytics_title),
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        // Data swaps here fade on effects specs: no full-width slides, no overshoot.
        val motion = MaterialTheme.motionScheme

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState }
        ) {
            item {
                AnalyticsFilterBar(
                    selectedPeriod = selectedPeriod,
                    customRangeLabel = customRangeLabel,
                    periodChipLabel = periodChipLabel,
                    timePeriods = timePeriods,
                    transactionTypeFilter = transactionTypeFilter,
                    defaultTypeFilter = defaultTypeFilter,
                    selectedCurrency = selectedCurrency,
                    availableCurrencies = availableCurrencies,
                    isUnifiedMode = isUnifiedMode,
                    categoryFilter = categoryFilter,
                    categoriesFilter = categoriesFilter,
                    availableCategories = uiState.availableCategories,
                    categoryParentOf = categoryParentOf,
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    accountOptions = accountOptions,
                    accountFilter = accountFilter,
                    accountsFilter = accountsFilter,
                    groupBy = groupBy,
                    isProEntitled = isProEntitled,
                    showGroupByMenu = showGroupByMenu,
                    showPeriodMenu = showPeriodMenu,
                    showTypeMenu = showTypeMenu,
                    showCurrencyMenu = showCurrencyMenu,
                    showCategoryMenu = showCategoryMenu,
                    showProfileMenu = showProfileMenu,
                    showAccountMenu = showAccountMenu,
                    hasActiveFilter = hasActiveAnalyticsFilter,
                    onGroupByClick = { showGroupByMenu = true },
                    onGroupByDismiss = { showGroupByMenu = false },
                    onGroupBySelected = { option ->
                        showGroupByMenu = false
                        // A Pro grouping without Pro opens the upgrade sheet; the view stays put.
                        if (option.allowedFor(isProEntitled) == option) {
                            viewModel.setGroupBy(option)
                        } else {
                            showUpgradeSheet = true
                        }
                    },
                    onPeriodClick = { showPeriodMenu = true },
                    onPeriodDismiss = { showPeriodMenu = false },
                    onPeriodSelected = { period ->
                        if (period == TimePeriod.CUSTOM) {
                            showDateRangePicker = true
                        } else {
                            viewModel.selectPeriod(period)
                        }
                        showPeriodMenu = false
                    },
                    onTypeClick = { showTypeMenu = true },
                    onTypeDismiss = { showTypeMenu = false },
                    onTypeSelected = { typeFilter ->
                        viewModel.setTransactionTypeFilter(typeFilter)
                        showTypeMenu = false
                    },
                    onCurrencyClick = { showCurrencyMenu = true },
                    onCurrencyDismiss = { showCurrencyMenu = false },
                    onCurrencySelected = { currency ->
                        viewModel.selectCurrency(currency)
                        showCurrencyMenu = false
                    },
                    onCategoryClick = { showCategoryMenu = true },
                    onCategoryDismiss = { showCategoryMenu = false },
                    onCategorySelected = { category ->
                        if (category == null) {
                            viewModel.clearCategoryFilter()
                        } else {
                            viewModel.setCategoryFilter(category)
                        }
                        showCategoryMenu = false
                    },
                    onToggleAllCategories = {
                        viewModel.toggleAllCategories(uiState.availableCategories)
                    },
                    onCategoryToggled = { category ->
                        viewModel.toggleCategory(category, uiState.availableCategories)
                    },
                    onProfileClick = { showProfileMenu = true },
                    onProfileDismiss = { showProfileMenu = false },
                    onProfileSelected = { profileId ->
                        viewModel.selectProfile(profileId)
                        showProfileMenu = false
                    },
                    onAccountClick = { showAccountMenu = true },
                    onAccountDismiss = { showAccountMenu = false },
                    onAccountSelected = { accountKey ->
                        viewModel.setAccountFilter(accountKey)
                        showAccountMenu = false
                    },
                    onToggleAllAccounts = {
                        viewModel.toggleAllAccounts(accountOptions.map { it.key })
                    },
                    onAccountToggled = { accountKey ->
                        viewModel.toggleAccount(accountKey, accountOptions.map { it.key })
                    },
                    onResetFilters = {
                        viewModel.selectPeriod(TimePeriod.THIS_MONTH)
                        viewModel.resetTransactionTypeFilter()
                        viewModel.clearCategoryFilter()
                        viewModel.setAccountFilter(null)
                        if (!isUnifiedMode && primaryVisibleCurrency.isNotBlank()) {
                            viewModel.selectCurrency(primaryVisibleCurrency)
                        }
                        showPeriodMenu = false
                        showTypeMenu = false
                        showCurrencyMenu = false
                        showCategoryMenu = false
                        showAccountMenu = false
                    }
                )
            }

            // Analytics Summary Card
            if (uiState.totalSpending > BigDecimal.ZERO || uiState.transactionCount > 0) {
                item {
                    AnalyticsSummaryCard(
                        totalAmount = uiState.totalSpending,
                        transactionCount = uiState.transactionCount,
                        averageAmount = uiState.averageAmount,
                        topCategory = uiState.topCategory,
                        topCategoryPercentage = uiState.topCategoryPercentage,
                        currency = uiState.currency,
                        isLoading = uiState.isLoading,
                        forecast = uiState.forecast
                    )
                }
            }

            // Chart Section with Type Selector
            if (uiState.spendingTrend.size >= 2) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeaderV2(
                            title = stringResource(R.string.analytics_trends),
                            action = {
                                Button(
                                    onClick = { showChartTypeSelector = !showChartTypeSelector },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = Spacing.smd, vertical = Spacing.xs),
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = when (chartType) {
                                            ChartType.LINE -> Icons.AutoMirrored.Filled.ShowChart
                                            ChartType.BAR -> Icons.Default.BarChart
                                            ChartType.HEATMAP -> Icons.Default.GridView
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = when (chartType) {
                                            ChartType.LINE -> stringResource(R.string.analytics_chart_line)
                                            ChartType.BAR -> stringResource(R.string.analytics_chart_bar)
                                            ChartType.HEATMAP -> stringResource(R.string.analytics_chart_heatmap)
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        )

                        // Expandable chart type selector card
                        AnimatedVisibility(
                            visible = showChartTypeSelector,
                            enter = pwExpand(),
                            exit = pwCollapse()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.sm),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                ChartType.entries.forEach { type ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable {
                                                viewModel.setChartType(type)
                                                showChartTypeSelector = false
                                            }
                                            .padding(horizontal = Spacing.md, vertical = Dimensions.Padding.listRowVertical),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when (type) {
                                                    ChartType.LINE -> Icons.AutoMirrored.Filled.ShowChart
                                                    ChartType.BAR -> Icons.Default.BarChart
                                                    ChartType.HEATMAP -> Icons.Default.GridView
                                                },
                                                contentDescription = null,
                                                tint = if (chartType == type)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = when (type) {
                                                    ChartType.LINE -> stringResource(R.string.analytics_chart_line_full)
                                                    ChartType.BAR -> stringResource(R.string.analytics_chart_bar_full)
                                                    ChartType.HEATMAP -> stringResource(R.string.analytics_chart_heatmap)
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (chartType == type)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (chartType == type) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(Dimensions.Icon.medium)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Chart display with crossfade transition
                        Crossfade(
                            targetState = chartType,
                            animationSpec = motion.defaultEffectsSpec(),
                            label = "chart_transition"
                        ) { type ->
                            // The trend's own currency, set with the data in one state: the
                            // picker's selectedCurrency flips first, which labelled the old
                            // figures with the new symbol for a frame.
                            val trendCurrency = uiState.currency.ifEmpty { selectedCurrency }
                            when (type) {
                                ChartType.LINE -> BalanceChart(
                                    primaryCurrency = trendCurrency,
                                    balanceHistory = uiState.spendingTrend,
                                    height = 220
                                )
                                ChartType.BAR -> SpendingBarChart(
                                    primaryCurrency = trendCurrency,
                                    data = uiState.spendingTrend,
                                    height = 220
                                )
                                ChartType.HEATMAP -> SpendingHeatmap(
                                    data = uiState.spendingTrend
                                )
                            }
                        }
                    }
                }
            }

            // Category Breakdown Section with Pie/List toggle
            if (groupBy == AnalyticsGroupBy.CATEGORY && uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeaderV2(
                            title = stringResource(R.string.analytics_top_categories),
                            action = {
                                IconButton(onClick = {
                                    categoryViewType = if (categoryViewType == CategoryViewType.CHART) {
                                        CategoryViewType.LIST
                                    } else {
                                        CategoryViewType.CHART
                                    }
                                }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(
                                        imageVector = if (categoryViewType == CategoryViewType.CHART)
                                            Icons.AutoMirrored.Filled.List
                                        else Icons.Default.PieChart,
                                        contentDescription = stringResource(R.string.analytics_toggle_view),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        // Animated content swap
                        AnimatedContent(
                            targetState = categoryViewType,
                            transitionSpec = {
                                fadeIn(motion.defaultEffectsSpec()) togetherWith
                                    fadeOut(motion.fastEffectsSpec()) using
                                    SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                            },
                            label = "category_view_transition"
                        ) { viewType ->
                            when (viewType) {
                                CategoryViewType.CHART -> CategoryPieChart(
                                    categories = uiState.categoryBreakdown,
                                    currency = selectedCurrency,
                                    onCategoryClick = { category ->
                                        drillDown(TransactionsDrillDown(category = category.name))
                                    },
                                    onSubCategoryClick = { subCategory ->
                                        drillDown(TransactionsDrillDown(category = subCategory.name))
                                    }
                                )
                                CategoryViewType.LIST -> CategoryBreakdownCard(
                                    categories = uiState.categoryBreakdown,
                                    currency = selectedCurrency,
                                    onCategoryClick = { category ->
                                        drillDown(TransactionsDrillDown(category = category.name))
                                    },
                                    onSubCategoryClick = { subCategory ->
                                        drillDown(TransactionsDrillDown(category = subCategory.name))
                                    }
                                )
                            }
                        }

                        // Explain why the bars can sum above the headline total: refunds are
                        // netted in full off the total, but a category bar can't go negative (#704).
                        if (uiState.refundNettedFromTotal > BigDecimal.ZERO) {
                            Text(
                                text = stringResource(
                                    R.string.analytics_refund_note,
                                    CurrencyFormatter.formatCurrency(uiState.refundNettedFromTotal, selectedCurrency)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.sm)
                            )
                        }
                    }
                }
            }

            // Tag Breakdown Section with Pie/List toggle
            if (groupBy == AnalyticsGroupBy.TAG && uiState.tagBreakdown.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeaderV2(
                            title = stringResource(R.string.analytics_top_tags),
                            action = {
                                IconButton(onClick = {
                                    tagViewType = if (tagViewType == CategoryViewType.CHART) {
                                        CategoryViewType.LIST
                                    } else {
                                        CategoryViewType.CHART
                                    }
                                }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(
                                        imageVector = if (tagViewType == CategoryViewType.CHART)
                                            Icons.AutoMirrored.Filled.List
                                        else Icons.Default.PieChart,
                                        contentDescription = stringResource(R.string.analytics_toggle_view),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        AnimatedContent(
                            targetState = tagViewType,
                            transitionSpec = {
                                fadeIn(motion.defaultEffectsSpec()) togetherWith
                                    fadeOut(motion.fastEffectsSpec()) using
                                    SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                            },
                            label = "tag_view_transition"
                        ) { viewType ->
                            when (viewType) {
                                CategoryViewType.CHART -> TagPieChart(
                                    tags = uiState.tagBreakdown,
                                    currency = selectedCurrency,
                                    onTagClick = { tag ->
                                        if (!tag.isUntagged) drillDown(TransactionsDrillDown(tag = tag.name))
                                    }
                                )
                                CategoryViewType.LIST -> TagBreakdownCard(
                                    tags = uiState.tagBreakdown,
                                    currency = selectedCurrency,
                                    onTagClick = { tag ->
                                        drillDown(TransactionsDrillDown(tag = tag.name))
                                    }
                                )
                            }
                        }

                        if (uiState.tagsOverlap) {
                            Text(
                                text = stringResource(R.string.analytics_tag_overlap_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.sm)
                            )
                        }
                    }
                }
            }

            // Day-of-week breakdown: there's no Transactions filter for a weekday, so rows don't drill in.
            if (groupBy == AnalyticsGroupBy.DAY_OF_WEEK && uiState.dayOfWeekBreakdown.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeaderV2(title = stringResource(R.string.analytics_by_day_of_week))
                        val barColor = MaterialTheme.colorScheme.primary
                        TagBreakdownCard(
                            tags = uiState.dayOfWeekBreakdown,
                            currency = selectedCurrency,
                            title = null,
                            visibleItemCount = uiState.dayOfWeekBreakdown.size,
                            barColor = { barColor },
                            onTagClick = null
                        )
                    }
                }
            }

            // Top Merchants Section
            if (groupBy == AnalyticsGroupBy.MERCHANT && uiState.topMerchants.isNotEmpty()) {
                item {
                    SectionHeaderV2(
                        title = stringResource(R.string.analytics_top_merchants)
                    )
                }

                // All Merchants with expandable list
                item {
                    ExpandableList(
                        items = uiState.topMerchants,
                        visibleItemCount = 3,
                        modifier = Modifier.fillMaxWidth()
                    ) { merchant ->
                        MerchantListItem(
                            merchant = merchant,
                            currency = selectedCurrency,
                            onClick = {
                                drillDown(TransactionsDrillDown(merchant = merchant.name))
                            }
                        )
                    }
                }
            }

            // By Account Section
            if (groupBy == AnalyticsGroupBy.ACCOUNT && uiState.accountBreakdown.isNotEmpty()) {
                item {
                    SectionHeaderV2(
                        title = stringResource(R.string.analytics_by_account)
                    )
                }

                item {
                    ExpandableList(
                        items = uiState.accountBreakdown,
                        visibleItemCount = 3,
                        modifier = Modifier.fillMaxWidth()
                    ) { account ->
                        AccountBreakdownListItem(
                            account = account,
                            currency = selectedCurrency,
                            onClick = { drillDown(TransactionsDrillDown(account = account.key)) }
                        )
                    }
                }
            }

            // Empty state
            if (uiState.topMerchants.isEmpty() && uiState.categoryBreakdown.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyAnalyticsState(onScanSmsClick = onNavigateToHome)
                }
            }
        }
    }

    if (showDateRangePicker) {
        CustomDateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { startDate, endDate ->
                viewModel.setCustomDateRange(startDate, endDate)
                showDateRangePicker = false
            },
            initialStartDate = customDateRange?.first,
            initialEndDate = customDateRange?.second
        )
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false }
        )
    }
}

@Composable
private fun AnalyticsFilterBar(
    selectedPeriod: TimePeriod,
    customRangeLabel: String?,
    periodChipLabel: String,
    timePeriods: List<TimePeriod>,
    transactionTypeFilter: TransactionTypeFilter,
    defaultTypeFilter: TransactionTypeFilter,
    selectedCurrency: String,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    categoryFilter: String?,
    categoriesFilter: List<String>? = null,
    availableCategories: List<String>,
    categoryParentOf: Map<String, String> = emptyMap(),
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    accountOptions: List<com.pennywiseai.tracker.presentation.common.AccountOption>,
    accountFilter: String?,
    accountsFilter: List<String>? = null,
    groupBy: AnalyticsGroupBy,
    isProEntitled: Boolean,
    showGroupByMenu: Boolean,
    showPeriodMenu: Boolean,
    showTypeMenu: Boolean,
    showCurrencyMenu: Boolean,
    showCategoryMenu: Boolean,
    showProfileMenu: Boolean,
    showAccountMenu: Boolean,
    hasActiveFilter: Boolean,
    onGroupByClick: () -> Unit,
    onGroupByDismiss: () -> Unit,
    onGroupBySelected: (AnalyticsGroupBy) -> Unit,
    onPeriodClick: () -> Unit,
    onPeriodDismiss: () -> Unit,
    onPeriodSelected: (TimePeriod) -> Unit,
    onTypeClick: () -> Unit,
    onTypeDismiss: () -> Unit,
    onTypeSelected: (TransactionTypeFilter) -> Unit,
    onCurrencyClick: () -> Unit,
    onCurrencyDismiss: () -> Unit,
    onCurrencySelected: (String) -> Unit,
    onCategoryClick: () -> Unit,
    onCategoryDismiss: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onToggleAllCategories: () -> Unit = {},
    onCategoryToggled: (String) -> Unit = {},
    onProfileClick: () -> Unit,
    onProfileDismiss: () -> Unit,
    onProfileSelected: (Long?) -> Unit,
    onAccountClick: () -> Unit,
    onAccountDismiss: () -> Unit,
    onAccountSelected: (String?) -> Unit,
    onToggleAllAccounts: () -> Unit = {},
    onAccountToggled: (String) -> Unit = {},
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (hasActiveFilter) {
            item {
                AssistChip(
                    onClick = onResetFilters,
                    label = { Text(stringResource(R.string.analytics_filter_clear)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                        leadingIconContentColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        // Group by (#422) — a view choice rather than a filter, so Clear leaves it alone.
        item {
            Box {
                ExpressiveFilterChip(
                    colors = analyticsFilterChipColors(),
                    border = analyticsFilterChipBorder(selected = groupBy != AnalyticsGroupBy.CATEGORY),
                    selected = groupBy != AnalyticsGroupBy.CATEGORY,
                    text = groupBy.label(),
                    icon = groupBy.icon(),
                    onClick = onGroupByClick
                )

                DropdownMenu(
                    expanded = showGroupByMenu,
                    onDismissRequest = onGroupByDismiss,
                    shape = MaterialTheme.shapes.large
                ) {
                    AnalyticsGroupBy.entries.forEachIndexed { index, option ->
                        SelectableDropdownMenuItem(
                            selected = groupBy == option,
                            text = { Text(option.label()) },
                            shapes = MenuDefaults.itemShape(index, AnalyticsGroupBy.entries.size),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            leadingIcon = { Icon(option.icon(), contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            // Subtle Pro hint for free users; picking it opens the upgrade sheet.
                            trailingContent = if (option.isPro && !isProEntitled) {
                                {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = stringResource(R.string.analytics_group_by_pro),
                                        tint = yellow_dark,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            } else null,
                            onClick = { onGroupBySelected(option) }
                        )
                    }
                }
            }
        }

        if (profiles.isNotEmpty()) {
            item {
                val selectedProfileLabel = profiles.find { it.id == selectedProfileId }?.displayName()
                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(selected = selectedProfileId != null),
                        selected = selectedProfileId != null,
                        text = selectedProfileLabel ?: stringResource(R.string.analytics_filter_all_profiles),
                        icon = profileFilterIcon(profiles, selectedProfileId),
                        onClick = onProfileClick
                    )

                    ProfileFilterDropdown(
                        expanded = showProfileMenu,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onProfileSelected = onProfileSelected,
                        onDismiss = onProfileDismiss
                    )
                }
            }
        }

        item {
            Box {
                ExpressiveFilterChip(
                    colors = analyticsFilterChipColors(),
                    border = analyticsFilterChipBorder(
                        selected = selectedPeriod != TimePeriod.THIS_MONTH || customRangeLabel != null
                    ),
                    selected = selectedPeriod != TimePeriod.THIS_MONTH || customRangeLabel != null,
                    text = periodChipLabel,
                    icon = Icons.Default.CalendarMonth,
                    onClick = onPeriodClick
                )

                DropdownMenu(
                    expanded = showPeriodMenu,
                    onDismissRequest = onPeriodDismiss,
                    shape = MaterialTheme.shapes.large
                ) {
                    timePeriods.forEachIndexed { index, period ->
                        SelectableDropdownMenuItem(
                            selected = selectedPeriod == period,
                            text = { Text(period.label) },
                            shapes = MenuDefaults.itemShape(index, timePeriods.size),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { onPeriodSelected(period) }
                        )
                    }
                }
            }
        }

        item {
            Box {
                ExpressiveFilterChip(
                    colors = analyticsFilterChipColors(),
                    border = analyticsFilterChipBorder(
                        selected = transactionTypeFilter != defaultTypeFilter
                    ),
                    selected = transactionTypeFilter != defaultTypeFilter,
                    text = transactionTypeFilter.shortLabel(),
                    icon = transactionTypeFilter.filterIcon(),
                    onClick = onTypeClick
                )

                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = onTypeDismiss,
                    shape = MaterialTheme.shapes.large
                ) {
                    TransactionTypeFilter.entries.forEachIndexed { index, typeFilter ->
                        SelectableDropdownMenuItem(
                            selected = transactionTypeFilter == typeFilter,
                            text = { Text(typeFilter.label) },
                            shapes = MenuDefaults.itemShape(index, TransactionTypeFilter.entries.size),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            leadingIcon = { Icon(typeFilter.filterIcon(), contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { onTypeSelected(typeFilter) }
                        )
                    }
                }
            }
        }

        if (availableCurrencies.size > 1 && !isUnifiedMode) {
            item {
                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(
                            selected = selectedCurrency != availableCurrencies.firstOrNull()
                        ),
                        selected = selectedCurrency != availableCurrencies.firstOrNull(),
                        text = selectedCurrency.ifBlank { stringResource(R.string.analytics_filter_currency) },
                        icon = Icons.Default.CurrencyExchange,
                        onClick = onCurrencyClick
                    )

                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = onCurrencyDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        availableCurrencies.forEachIndexed { index, currency ->
                            SelectableDropdownMenuItem(
                                selected = selectedCurrency == currency,
                                text = { Text(currency) },
                                shapes = MenuDefaults.itemShape(index, availableCurrencies.size),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = { onCurrencySelected(currency) }
                            )
                        }
                    }
                }
            }
        }

        if (availableCategories.isNotEmpty() || categoryFilter != null || categoriesFilter != null) {
            item {
                val selectedCategories = categoriesFilter?.toSet()
                    ?: categoryFilter?.let { setOf(it) }
                    ?: availableCategories.toSet()
                val isAllSelected = availableCategories.isNotEmpty() && selectedCategories.size == availableCategories.size && categoryFilter == null
                val hasCatFilter = !isAllSelected
                val catLabel = categoryFilterChipLabel(categoryFilter, categoriesFilter, availableCategories)

                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(selected = hasCatFilter),
                        selected = hasCatFilter,
                        text = catLabel,
                        icon = Icons.Default.Category,
                        onClick = onCategoryClick
                    )

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = onCategoryDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.analytics_filter_all_categories)) },
                            leadingIcon = {
                                Checkbox(
                                    checked = isAllSelected,
                                    onCheckedChange = null
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            },
                            onClick = onToggleAllCategories
                        )

                        HorizontalDivider()

                        val organizedCategories = remember(availableCategories, categoryParentOf) {
                            organizeHierarchicalCategories(availableCategories, categoryParentOf)
                        }

                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            organizedCategories.forEach { category ->
                                val isChild = category in categoryParentOf
                                DropdownMenuItem(
                                    modifier = if (isChild) Modifier.padding(start = Spacing.lg) else Modifier,
                                    text = {
                                        Text(
                                            categoryLabel(category),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Checkbox(
                                            checked = category in selectedCategories,
                                            onCheckedChange = null
                                        )
                                    },
                                    trailingIcon = {
                                        CategoryIcon(
                                            category = category,
                                            size = Dimensions.Icon.small
                                        )
                                    },
                                    onClick = { onCategoryToggled(category) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (accountOptions.isNotEmpty()) {
            item {
                val selectedAccounts = accountsFilter?.toSet()
                    ?: accountFilter?.let { setOf(it) }
                    ?: accountOptions.map { it.key }.toSet()
                val isAllSelected = accountOptions.isNotEmpty() && selectedAccounts.size == accountOptions.size && accountFilter == null
                val hasAccFilter = !isAllSelected
                val accLabel = accountFilterChipLabel(accountFilter, accountsFilter, accountOptions)

                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(selected = hasAccFilter),
                        selected = hasAccFilter,
                        text = accLabel,
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = onAccountClick
                    )

                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = onAccountDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.analytics_filter_all_accounts)) },
                            leadingIcon = {
                                Checkbox(
                                    checked = isAllSelected,
                                    onCheckedChange = null
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            },
                            onClick = onToggleAllAccounts
                        )

                        HorizontalDivider()

                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            accountOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Checkbox(
                                            checked = option.key in selectedAccounts,
                                            onCheckedChange = null
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    },
                                    onClick = { onAccountToggled(option.key) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsGroupBy.label(): String = stringResource(
    when (this) {
        AnalyticsGroupBy.CATEGORY -> R.string.analytics_group_by_category
        AnalyticsGroupBy.MERCHANT -> R.string.analytics_group_by_merchant
        AnalyticsGroupBy.ACCOUNT -> R.string.analytics_group_by_account
        AnalyticsGroupBy.TAG -> R.string.analytics_group_by_tag
        AnalyticsGroupBy.DAY_OF_WEEK -> R.string.analytics_group_by_day_of_week
    }
)

private fun AnalyticsGroupBy.icon() = when (this) {
    AnalyticsGroupBy.CATEGORY -> Icons.Default.Category
    AnalyticsGroupBy.MERCHANT -> Icons.Default.Store
    AnalyticsGroupBy.ACCOUNT -> Icons.Default.AccountBalanceWallet
    AnalyticsGroupBy.TAG -> Icons.Default.Sell
    AnalyticsGroupBy.DAY_OF_WEEK -> Icons.Default.DateRange
}

@Composable
private fun analyticsFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
)

@Composable
private fun analyticsFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    selected = selected,
    enabled = true,
    borderWidth = Dimensions.Elevation.none,
    selectedBorderWidth = Dimensions.Elevation.none
)

@Composable
private fun CategoryListItem(
    category: CategoryData,
    currency: String
) {
    val categoryInfo = CategoryMapping.categories[category.name]
        ?: CategoryMapping.categories["Others"]!!

    ListItemCardV2(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatar)
                    .clip(CircleShape)
                    .background(CategoryMapping.colorFor(category.name).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                CategoryIcon(
                    category = category.name,
                    size = Dimensions.Icon.medium,
                    tint = CategoryMapping.colorFor(category.name)
                )
            }
        },
        title = categoryLabel(category.name),
        subtitle = pluralStringResource(
            R.plurals.analytics_transaction_count,
            category.transactionCount,
            category.transactionCount
        ),
        amount = CurrencyFormatter.formatCurrency(category.amount, currency),
        trailingContent = {
            Text(
                text = stringResource(R.string.analytics_percent, category.percentage.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun MerchantListItem(
    merchant: MerchantData,
    currency: String,
    onClick: () -> Unit = {}
) {
    val subtitle = pluralStringResource(
        if (merchant.isSubscription) R.plurals.analytics_transaction_count_subscription
        else R.plurals.analytics_transaction_count,
        merchant.transactionCount,
        merchant.transactionCount
    )

    // Brand icon stays keyed on the raw merchant; only the label uses the alias (#583).
    val merchantDisplay = LocalMerchantDisplay.current

    ListItemCardV2(
        leadingContent = {
            BrandIcon(
                merchantName = merchant.name,
                size = Dimensions.Icon.avatarLarge,
                showBackground = true
            )
        },
        title = merchantDisplay(merchant.name) ?: merchant.name,
        subtitle = subtitle,
        amount = CurrencyFormatter.formatCurrency(merchant.amount, currency),
        onClick = onClick
    )
}

@Composable
private fun AccountBreakdownListItem(
    account: AccountBreakdownData,
    currency: String,
    onClick: () -> Unit
) {
    ListItemCardV2(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatar)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        },
        title = account.label,
        subtitle = pluralStringResource(
            R.plurals.analytics_transaction_count,
            account.transactionCount,
            account.transactionCount
        ),
        amount = CurrencyFormatter.formatCurrency(account.amount, currency),
        onClick = onClick,
        // Amount and share: a trailing slot replaces the card's own amount text.
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatCurrency(account.amount, currency),
                    style = PennyWiseText.amountRow,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.analytics_percent, account.percentage.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun EmptyAnalyticsState(
    onScanSmsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            headline = stringResource(R.string.analytics_empty_headline),
            description = stringResource(R.string.analytics_empty_description),
            actionLabel = stringResource(R.string.analytics_empty_action),
            onAction = onScanSmsClick
        )
    }
}

/**
 * Organizes available categories into hierarchical order: top-level categories first,
 * with each parent's children nested immediately underneath it.
 */
internal fun organizeHierarchicalCategories(
    availableCategories: List<String>,
    parentOf: Map<String, String>
): List<String> {
    if (availableCategories.isEmpty()) return emptyList()
    val childrenByParent = availableCategories
        .filter { it in parentOf }
        .groupBy { parentOf[it]!! }

    val topLevel = availableCategories
        .filter { it !in parentOf }
        .sorted()

    val result = mutableListOf<String>()
    val visited = mutableSetOf<String>()

    for (parent in topLevel) {
        result.add(parent)
        visited.add(parent)
        val children = childrenByParent[parent].orEmpty().sorted()
        for (child in children) {
            result.add(child)
            visited.add(child)
        }
    }

    val orphans = availableCategories.filter { it !in visited }.sorted()
    result.addAll(orphans)
    return result
}

@Composable
internal fun categoryFilterChipLabel(
    single: String?,
    selected: List<String>?,
    available: List<String>
): String {
    val selectedSet = selected?.toSet()
        ?: single?.let { setOf(it) }
        ?: available.toSet()

    return when {
        selectedSet.isEmpty() -> stringResource(R.string.analytics_filter_category_none)
        available.isNotEmpty() && selectedSet.size == available.size && single == null ->
            stringResource(R.string.analytics_filter_category_all)
        else -> stringResource(R.string.analytics_filter_category_custom)
    }
}

@Composable
internal fun accountFilterChipLabel(
    single: String?,
    selected: List<String>?,
    available: List<com.pennywiseai.tracker.presentation.common.AccountOption>
): String {
    val selectedSet = selected?.toSet()
        ?: single?.let { setOf(it) }
        ?: available.map { it.key }.toSet()

    return when {
        selectedSet.isEmpty() -> stringResource(R.string.analytics_filter_account_none)
        available.isNotEmpty() && selectedSet.size == available.size && single == null ->
            stringResource(R.string.analytics_filter_account_all)
        else -> stringResource(R.string.analytics_filter_account_custom)
    }
}
