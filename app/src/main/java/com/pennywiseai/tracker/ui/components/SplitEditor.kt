package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.SplitIconCache
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

data class SplitItem(
    val id: Long = 0,
    val category: String,
    val amount: BigDecimal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditor(
    totalAmount: BigDecimal,
    currency: String,
    splits: List<SplitItem>,
    availableCategories: List<String>,
    onSplitsChanged: (List<SplitItem>) -> Unit,
    onRemoveSplits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val splitsTotal = splits.fold(BigDecimal.ZERO) { acc, split -> acc + split.amount }
    val remaining = totalAmount - splitsTotal
    val hasZeroSplit = splits.any { it.amount <= BigDecimal.ZERO }
    val isBalanced = remaining.abs() <= BigDecimal("0.01") && !hasZeroSplit

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header with live composite icon preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (splits.size >= 2) {
                        SplitCategoryIcon(
                            categories = splits.map { it.category },
                            size = 36.dp,
                            showBackground = true
                        )
                    }
                    Text(
                        text = stringResource(R.string.split_editor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = onRemoveSplits,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.split_editor_remove_all))
                }
            }

            // Split rows
            splits.forEachIndexed { index, split ->
                SplitRow(
                    split = split,
                    priorityIndex = index,
                    availableCategories = availableCategories.filter { cat ->
                        cat == split.category || splits.none { it.category == cat }
                    },
                    canMoveUp = index > 0,
                    canMoveDown = index < splits.lastIndex,
                    onMoveUp = {
                        if (index > 0) {
                            val newSplits = splits.toMutableList()
                            val temp = newSplits[index]
                            newSplits[index] = newSplits[index - 1]
                            newSplits[index - 1] = temp
                            onSplitsChanged(newSplits)
                        }
                    },
                    onMoveDown = {
                        if (index < splits.lastIndex) {
                            val newSplits = splits.toMutableList()
                            val temp = newSplits[index]
                            newSplits[index] = newSplits[index + 1]
                            newSplits[index + 1] = temp
                            onSplitsChanged(newSplits)
                        }
                    },
                    onCategoryChanged = { newCategory ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(category = newCategory)
                        onSplitsChanged(newSplits)
                    },
                    onAmountChanged = { newAmount ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(amount = newAmount)
                        onSplitsChanged(newSplits)
                    },
                    onRemove = {
                        if (splits.size > 2) {
                            val newSplits = splits.toMutableList()
                            newSplits.removeAt(index)
                            onSplitsChanged(newSplits)
                        }
                    },
                    canRemove = splits.size > 2,
                    remaining = remaining,
                    currency = currency
                )
            }

            // Add split button
            OutlinedButton(
                onClick = {
                    val usedCategories = splits.map { it.category }.toSet()
                    val nextCategory = availableCategories.firstOrNull { it !in usedCategories } ?: "Others"
                    val newSplit = SplitItem(
                        id = 0,
                        category = nextCategory,
                        amount = BigDecimal.ZERO
                    )
                    onSplitsChanged(splits + newSplit)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = availableCategories.size > splits.size,
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(stringResource(R.string.split_editor_add))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            // Total validation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBalanced) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.split_editor_balanced),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(R.string.split_editor_not_balanced),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    }
                    Text(
                        text = stringResource(R.string.split_editor_total),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(splitsTotal, currency),
                        style = PennyWiseText.amountMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isBalanced) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (!isBalanced) {
                        Text(
                            text = when {
                                remaining.abs() <= BigDecimal("0.01") && hasZeroSplit -> {
                                    stringResource(R.string.split_editor_zero_amount_warning)
                                }
                                remaining > BigDecimal.ZERO -> {
                                    stringResource(R.string.split_editor_remaining, CurrencyFormatter.formatCurrency(remaining, currency))
                                }
                                else -> {
                                    stringResource(R.string.split_editor_over, CurrencyFormatter.formatCurrency(remaining.abs(), currency))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitRow(
    split: SplitItem,
    priorityIndex: Int,
    availableCategories: List<String>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCategoryChanged: (String) -> Unit,
    onAmountChanged: (BigDecimal) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    remaining: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var amountText by remember(split.amount) {
        mutableStateOf(if (split.amount == BigDecimal.ZERO) "" else split.amount.stripTrailingZeros().toPlainString())
    }

    // Category on its own full-width line, amount + remove beneath it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Priority header with reordering buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val priorityLabel = when (priorityIndex) {
                0 -> stringResource(R.string.split_priority_primary)
                1 -> stringResource(R.string.split_priority_secondary)
                2 -> stringResource(R.string.split_priority_tertiary)
                else -> "${priorityIndex + 1}th"
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (priorityIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = priorityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (priorityIndex == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(Dimensions.Component.iconButton),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.split_editor_move_up),
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(Dimensions.Component.iconButton),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.split_editor_move_down),
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        }

        // Category dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = categoryLabel(split.category),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableCategories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = categoryLabel(category),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onCategoryChanged(category)
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Amount field
            TextField(
                value = amountText,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        amountText = filtered
                        val parsedAmount = filtered.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        onAmountChanged(parsedAmount)
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = {
                    Text(
                        text = CurrencyFormatter.getCurrencySymbol(currency),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingIcon = if (amountText.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                amountText = ""
                                onAmountChanged(BigDecimal.ZERO)
                            },
                            modifier = Modifier.size(Dimensions.Component.iconButton),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.split_editor_clear_amount),
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        }
                    }
                } else null,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.split_editor_remove),
                    tint = if (canRemove) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        }

        // Fill remaining button on zero-amount rows when total filled < transaction total
        val canFillRemaining = split.amount <= BigDecimal.ZERO && remaining > BigDecimal("0.01")
        AnimatedVisibility(
            visible = canFillRemaining,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FilledTonalButton(
                onClick = {
                    val formatted = remaining.stripTrailingZeros().toPlainString()
                    amountText = formatted
                    onAmountChanged(remaining)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(
                        R.string.split_editor_fill_remaining,
                        CurrencyFormatter.formatCurrency(remaining, currency)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Card showing split breakdown in view mode (read-only).
 */
@Composable
fun SplitBreakdownCard(
    splits: List<SplitItem>,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.split_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            splits.forEachIndexed { index, split ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = when (index) {
                                    0 -> "1st"
                                    1 -> "2nd"
                                    2 -> "3rd"
                                    else -> "${index + 1}th"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        CategoryMapping.effectiveEmojiFor(split.category)?.let { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = categoryLabel(split.category),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(split.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
