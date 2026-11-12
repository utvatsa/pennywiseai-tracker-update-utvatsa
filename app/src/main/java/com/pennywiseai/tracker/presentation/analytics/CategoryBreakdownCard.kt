package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.ExpandableList
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun CategoryBreakdownCard(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {},
    onSubCategoryClick: (SubCategoryData) -> Unit = {}
) {
    val maxAmount = categories.map { it.amount }.maxOrNull() ?: BigDecimal.ZERO
    var expandedCategories by rememberSaveable { mutableStateOf(setOf<String>()) }

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.analytics_spending_by_category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            ExpandableList(
                items = categories,
                visibleItemCount = 5
            ) { category ->
                val isExpanded = category.name in expandedCategories
                CategoryBar(
                    category = category,
                    maxAmount = maxAmount,
                    currency = currency,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedCategories = if (isExpanded) {
                            expandedCategories - category.name
                        } else {
                            expandedCategories + category.name
                        }
                    },
                    onClick = { onCategoryClick(category) },
                    onSubCategoryClick = onSubCategoryClick
                )
            }
        }
    }
}

@Composable
private fun CategoryBar(
    category: CategoryData,
    maxAmount: BigDecimal,
    currency: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onSubCategoryClick: (SubCategoryData) -> Unit
) {
    val targetFraction = if (maxAmount > BigDecimal.ZERO) {
        (category.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Animated progress bar fill
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animatedFraction by animateFloatAsState(
        targetValue = if (visible) targetFraction else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "category_bar_${category.name}"
    )

    // Category color: user's assigned color, else built-in palette, else gray (#586)
    val categoryColor = CategoryMapping.colorFor(category.name, category.color)
    val hasSubcategories = category.subCategories.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Main row: clickable to navigate to Transactions for the parent category
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onClick() }
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categoryLabel(category.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.analytics_percent, category.percentage.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(category.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Animated progress bar with rounded corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(categoryColor)
                )
            }
        }

        // Distinct pill CTA for subcategories (with distinct background & Material blue CTA styling)
        if (hasSubcategories) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isExpanded) {
                                stringResource(R.string.analytics_hide_subcategories)
                            } else {
                                pluralStringResource(
                                    R.plurals.analytics_subcategories_count,
                                    category.subCategories.size,
                                    category.subCategories.size
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "${category.transactionCount} txns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable subsection
            AnimatedVisibility(
                visible = isExpanded,
                enter = pwExpand(),
                exit = pwCollapse()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    category.subCategories.forEach { sub ->
                        SubCategoryRow(
                            subCategory = sub,
                            parentCategory = category,
                            currency = currency,
                            onClick = { onSubCategoryClick(sub) }
                        )
                    }

                    // Direct spend under parent if present
                    if (category.directAmount > BigDecimal.ZERO) {
                        val directPctOfParent = if (category.amount > BigDecimal.ZERO) {
                            (category.directAmount.divide(category.amount, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat().coerceAtMost(100f)
                        } else 0f
                        val directPctOfTotal = category.percentage * (directPctOfParent / 100f)
                        DirectSpendRow(
                            parentName = category.name,
                            directAmount = category.directAmount,
                            parentColor = categoryColor,
                            percentageOfParent = directPctOfParent,
                            percentageOfTotal = directPctOfTotal,
                            currency = currency,
                            onClick = onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubCategoryRow(
    subCategory: SubCategoryData,
    parentCategory: CategoryData,
    currency: String,
    onClick: () -> Unit
) {
    val subColor = CategoryMapping.colorFor(subCategory.name, subCategory.color)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(subColor)
                )
                Text(
                    text = categoryLabel(subCategory.name),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = CurrencyFormatter.formatCurrency(subCategory.amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // Percentage row: both % of parent and % of total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.analytics_subcategory_percent_both,
                    subCategory.percentageOfParent.toInt(),
                    categoryLabel(parentCategory.name),
                    subCategory.percentageOfTotal.toInt()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${subCategory.transactionCount} txns",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Sub-bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((subCategory.percentageOfParent / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(subColor)
            )
        }
    }
}

@Composable
private fun DirectSpendRow(
    parentName: String,
    directAmount: BigDecimal,
    parentColor: Color,
    percentageOfParent: Float,
    percentageOfTotal: Float,
    currency: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(parentColor.copy(alpha = 0.6f))
                )
                Text(
                    text = stringResource(R.string.analytics_direct_spend),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = CurrencyFormatter.formatCurrency(directAmount, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.analytics_subcategory_percent_both,
                    percentageOfParent.toInt(),
                    categoryLabel(parentName),
                    percentageOfTotal.toInt()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percentageOfParent / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(parentColor.copy(alpha = 0.6f))
            )
        }
    }
}
