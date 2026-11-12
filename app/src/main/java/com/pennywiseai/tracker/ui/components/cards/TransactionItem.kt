package com.pennywiseai.tracker.ui.components.cards

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.EmojiGlyph
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.sharedElementIcon
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    showDate: Boolean = true,
    showTypeLabel: Boolean = true,
    listItemPosition: ListItemPosition = ListItemPosition.Single,
    profileAccountKeys: Map<Long, Set<String>> = emptyMap(),
    splitCategories: List<String> = emptyList(),
    splits: List<TransactionSplitEntity> = emptyList(),
    onClick: () -> Unit = {},
    /** Optional long-press handler — used for bulk-edit selection entry. */
    onLongClick: (() -> Unit)? = null,
    /** Overrides the row's container colour (e.g. for selected state). */
    containerColor: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME -> colorScheme.income
        TransactionType.EXPENSE -> containerColor
            ?.let { colorScheme.contentColorFor(it) }
            ?.takeIf { it.isSpecified }
            ?: colorScheme.onSurface
        TransactionType.CREDIT -> colorScheme.credit
        TransactionType.TRANSFER -> colorScheme.transfer
        TransactionType.INVESTMENT -> colorScheme.investment
    }

    val effectiveSplits = remember(splits, splitCategories, transaction.id) {
        if (splits.isNotEmpty()) splits
        else splitCategories.map {
            TransactionSplitEntity(
                transactionId = transaction.id,
                category = it,
                amount = BigDecimal.ZERO
            )
        }
    }
    val effectiveSplitCategories = remember(effectiveSplits) {
        effectiveSplits.map { it.category }
    }
    var isExpanded by rememberSaveable(transaction.id) { mutableStateOf(false) }

    val dateTimeFormatter = if (showDate) AppDates.dayMonthTimeFormatter() else AppDates.pattern("h:mm a")
    val dateTimeText = remember(transaction.dateTime, dateTimeFormatter) {
        transaction.dateTime.format(dateTimeFormatter)
    }

    val isEffectivelyBusiness = remember(transaction, profileAccountKeys) {
        val effectiveProfileId = transaction.profileId ?: run {
            if (transaction.bankName != null && transaction.accountNumber != null) {
                val key = "${transaction.bankName}_${transaction.accountNumber}"
                profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
            } else null
        }
        effectiveProfileId == ProfileEntity.BUSINESS_ID
    }

    // User-written description, if present, is surfaced as the LEAD segment of
    // the subtitle (kept short) — not as the title. Promoting it to title made
    // casual notes ("movie night with sarah") read as inconsistent next to
    // brand-name merchants ("Uber", "Netflix") and routinely got truncated. The
    // merchant stays the visual heading; the description is a small contextual
    // tag below. (#383)
    val description = transaction.description?.takeIf { it.isNotBlank() }

    val resources = LocalContext.current.resources
    val categoryText = categoryLabel(transaction.category)
    val splitCategoryLabels = effectiveSplits.map { categoryLabel(it.category) }
    val splitCategoriesSummary = remember(splitCategoryLabels) {
        if (splitCategoryLabels.size <= 2) {
            splitCategoryLabels.joinToString(", ")
        } else {
            "${splitCategoryLabels.getOrNull(0) ?: ""}, ${splitCategoryLabels.getOrNull(1) ?: ""} +${splitCategoryLabels.size - 2}"
        }
    }
    val subtitle = remember(transaction, categoryText, dateTimeText, isEffectivelyBusiness, AmountPrivacy.hidden, effectiveSplits.size, splitCategoriesSummary, isExpanded) {
        buildList {
            if (description != null) add(CurrencyFormatter.maskAmountsIn(description))
            add(dateTimeText)
            if (effectiveSplits.size >= 2) {
                add(splitCategoriesSummary)
                add(resources.getString(R.string.txn_item_tag_split, effectiveSplits.size) + if (isExpanded) " \u25B4" else " \u25BE")
            } else if (transaction.category.isNotBlank() &&
                !transaction.category.equals("Uncategorized", ignoreCase = true)
            ) {
                add(categoryText)
            }

            if (showTypeLabel) {
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> add(resources.getString(R.string.txn_item_tag_credit))
                    TransactionType.TRANSFER -> {
                        if (transferTitleOverride(transaction, resources) == null) {
                            add(resources.getString(R.string.txn_item_tag_transfer))
                        }
                    }
                    TransactionType.INVESTMENT -> add(resources.getString(R.string.txn_item_tag_investment))
                    else -> {}
                }
            }
            if (transaction.isRecurring) add(resources.getString(R.string.txn_item_tag_recurring))
            if (isEffectivelyBusiness) add(resources.getString(R.string.txn_item_tag_business))
            // Mark rows the user excluded from analytics so it's visible in the
            // list which ones are skipped by spending stats (#451).
            if (transaction.excludedFromAnalytics) add(resources.getString(R.string.txn_item_tag_excluded))
            transaction.balanceAfter?.let { balance ->
                add(resources.getString(R.string.txn_item_balance_after, CurrencyFormatter.formatCurrency(balance, transaction.currency)))
            }
        }.joinToString(" \u00B7 ")
    }

    val amountPrefix = remember(transaction.transactionType) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> "+"
            TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INVESTMENT -> "-"
            TransactionType.TRANSFER -> ""
        }
    }

    val formattedAmount = if (convertedAmount != null && displayCurrency != null) {
        CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
    } else {
        transaction.formatAmount()
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val merchantDisplay = LocalMerchantDisplay.current

    // For a paired self-transfer row, the event ("Transfer → 9999" /
    // "Transfer from 1234") is more informative than the merchant name (often
    // the user's own contact name), and stops the two legs from looking like
    // duplicate rows in the list. Falls back to merchant otherwise.
    val transferTitle = transferTitleOverride(transaction, resources)

    ListItemCardV2(
        title = transferTitle ?: merchantDisplay(transaction.merchantName) ?: transaction.merchantName,
        subtitle = subtitle,
        amount = CurrencyFormatter.signed(amountPrefix, formattedAmount),
        amountColor = amountColor,
        shape = listItemPosition.toShape(),
        contentPadding = Dimensions.Padding.cardCompact,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (effectiveSplits.size >= 2) {
                isExpanded = !isExpanded
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        containerColor = containerColor,
        modifier = modifier,
        leadingContent = {
            val iconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    sharedElementIcon(
                        key = "brand_icon_${transaction.id}",
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else {
                Modifier
            }
            BrandIcon(
                merchantName = transaction.merchantName,
                modifier = iconModifier,
                size = Dimensions.Icon.list,
                showBackground = true,
                category = transaction.category,
                splitCategories = effectiveSplitCategories,
                transactionId = transaction.id,
                transactionType = transaction.transactionType
            )
        },
        trailingContent = {
            if (convertedAmount != null && displayCurrency != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = CurrencyFormatter.signed(amountPrefix, CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)),
                        style = PennyWiseText.amountRow,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "(${transaction.formatAmount()})",
                        style = PennyWiseText.amountSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = CurrencyFormatter.signed(amountPrefix, formattedAmount),
                    style = PennyWiseText.amountRow,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        expandedContent = if (effectiveSplits.size >= 2 && isExpanded) {
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    effectiveSplits.forEach { split ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                modifier = Modifier.weight(1f)
                            ) {
                                val emoji = CategoryMapping.effectiveEmojiFor(split.category)
                                if (emoji != null && emoji.isNotBlank()) {
                                    EmojiGlyph(emoji, Dimensions.Icon.small * 0.8f)
                                } else {
                                    CategoryIcon(category = split.category, size = Dimensions.Icon.small)
                                }
                                Text(
                                    text = categoryLabel(split.category),
                                    style = PennyWiseText.metadata,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (split.amount > BigDecimal.ZERO) {
                                Text(
                                    text = CurrencyFormatter.signed(amountPrefix, CurrencyFormatter.formatCurrency(split.amount, transaction.currency)),
                                    style = PennyWiseText.amountSmall,
                                    color = amountColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onClick()
                            }
                            .padding(vertical = Spacing.xs, horizontal = Spacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.txn_item_view_details),
                            style = PennyWiseText.metadata,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        } else null
    )
}

/**
 * For TRANSFER rows that have `fromAccount` and `toAccount` populated,
 * synthesise a title that describes the event (which leg + the other
 * account's last-4) rather than the merchant. Returns null for any other
 * row, in which case the default merchant-as-title rendering wins.
 */
private fun transferTitleOverride(transaction: TransactionEntity, resources: Resources): String? {
    if (transaction.transactionType != TransactionType.TRANSFER) return null
    val mine = transaction.accountNumber
    val from = transaction.fromAccount
    val to = transaction.toAccount
    return when {
        from != null && to != null && mine == from -> resources.getString(R.string.txn_item_transfer_to, to.takeLast(4))
        from != null && to != null && mine == to -> resources.getString(R.string.txn_item_transfer_from, from.takeLast(4))
        to != null && mine != to -> resources.getString(R.string.txn_item_transfer_to, to.takeLast(4))
        from != null && mine != from -> resources.getString(R.string.txn_item_transfer_from, from.takeLast(4))
        else -> null
    }
}
