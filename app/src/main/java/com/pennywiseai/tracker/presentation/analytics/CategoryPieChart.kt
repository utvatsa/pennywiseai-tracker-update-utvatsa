package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** Represents an angular sector for touch detection and rendering. */
private data class ArcSector(
    val startAngle: Float,
    val sweepAngle: Float,
    val parentCategory: CategoryData,
    val subCategory: SubCategoryData? = null,
    val isDirect: Boolean = false,
    val color: Color
)

@Composable
fun CategoryPieChart(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {},
    onSubCategoryClick: (SubCategoryData) -> Unit = {}
) {
    if (categories.isEmpty()) return

    val total = categories.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.amount }
    if (total <= BigDecimal.ZERO) return

    // Drill-down state: when non-null, shows a focused pie chart for this single parent category
    var drillDownCategory by remember { mutableStateOf<CategoryData?>(null) }
    // Ensure drill-down stays synced if categories update
    LaunchedEffect(categories) {
        val current = drillDownCategory
        if (current != null) {
            drillDownCategory = categories.find { it.name == current.name }
        }
    }

    // Active selection for center feedback on touch/hover
    var selectedParent by remember(categories, drillDownCategory) {
        mutableStateOf<CategoryData?>(drillDownCategory ?: categories.firstOrNull())
    }
    var selectedSub by remember(categories, drillDownCategory) {
        mutableStateOf<SubCategoryData?>(null)
    }

    // Legend inline expansion state
    var expandedLegendCategories by rememberSaveable { mutableStateOf(setOf<String>()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Donut Chart with Circumference Sub-Arcs & Dynamic Center
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            val currentDrillDown = drillDownCategory
            val sectors = remember(categories, currentDrillDown) {
                mutableListOf<ArcSector>().apply {
                    if (currentDrillDown == null) {
                        // Top-level View: All parent categories with subcategory circumference segments
                        val totalVal = total.toDouble()
                        var currentAngle = -90f
                        categories.forEach { cat ->
                            val catSweep = ((cat.amount.toDouble() / totalVal) * 360.0).toFloat()
                            val catColor = CategoryMapping.colorFor(cat.name, cat.color)

                            if (cat.subCategories.isEmpty()) {
                                add(
                                    ArcSector(
                                        startAngle = currentAngle,
                                        sweepAngle = catSweep,
                                        parentCategory = cat,
                                        color = catColor
                                    )
                                )
                            } else {
                                val catTotalVal = cat.amount.toDouble()
                                var subAngle = currentAngle
                                cat.subCategories.forEach { sub ->
                                    val subSweep = (catSweep * (sub.amount.toDouble() / catTotalVal)).toFloat()
                                    val subColor = CategoryMapping.colorFor(sub.name, sub.color)
                                    add(
                                        ArcSector(
                                            startAngle = subAngle,
                                            sweepAngle = subSweep,
                                            parentCategory = cat,
                                            subCategory = sub,
                                            color = subColor
                                        )
                                    )
                                    subAngle += subSweep
                                }
                                if (cat.directAmount > BigDecimal.ZERO) {
                                    val directSweep = (catSweep * (cat.directAmount.toDouble() / catTotalVal)).toFloat()
                                    add(
                                        ArcSector(
                                            startAngle = subAngle,
                                            sweepAngle = directSweep,
                                            parentCategory = cat,
                                            isDirect = true,
                                            color = catColor.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                            currentAngle += catSweep
                        }
                    } else {
                        // Drill-Down View: Focused on one category's subcategories
                        val catTotalVal = currentDrillDown.amount.toDouble()
                        var currentAngle = -90f
                        currentDrillDown.subCategories.forEach { sub ->
                            val subSweep = ((sub.amount.toDouble() / catTotalVal) * 360.0).toFloat()
                            val subColor = CategoryMapping.colorFor(sub.name, sub.color)
                            add(
                                ArcSector(
                                    startAngle = currentAngle,
                                    sweepAngle = subSweep,
                                    parentCategory = currentDrillDown,
                                    subCategory = sub,
                                    color = subColor
                                )
                            )
                            currentAngle += subSweep
                        }
                        if (currentDrillDown.directAmount > BigDecimal.ZERO) {
                            val directSweep = ((currentDrillDown.directAmount.toDouble() / catTotalVal) * 360.0).toFloat()
                            val catColor = CategoryMapping.colorFor(currentDrillDown.name, currentDrillDown.color)
                            add(
                                ArcSector(
                                    startAngle = currentAngle,
                                    sweepAngle = directSweep,
                                    parentCategory = currentDrillDown,
                                    isDirect = true,
                                    color = catColor.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }

            // Custom Compose Canvas Donut Chart
            Canvas(
                modifier = Modifier
                    .size(175.dp)
                    .pointerInput(sectors, currentDrillDown) {
                        fun handlePointer(offset: Offset) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val r = sqrt(dx * dx + dy * dy)
                            val strokePx = 16.dp.toPx()
                            val radiusPx = (min(size.width, size.height) - strokePx) / 2f

                            // If touched inside center hole
                            if (r < radiusPx - strokePx * 0.8f) {
                                if (currentDrillDown != null) {
                                    drillDownCategory = null
                                } else if (selectedParent?.subCategories?.isNotEmpty() == true) {
                                    drillDownCategory = selectedParent
                                }
                                return
                            }

                            // Calculate angle from 12 o'clock in [0, 360)
                            val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val angleFromTop = (deg + 90f + 360f) % 360f

                            // Find matching sector
                            for (sector in sectors) {
                                val normStart = (sector.startAngle + 90f + 360f) % 360f
                                val inSector = if (normStart + sector.sweepAngle <= 360f) {
                                    angleFromTop in normStart..(normStart + sector.sweepAngle)
                                } else {
                                    angleFromTop >= normStart || angleFromTop <= (normStart + sector.sweepAngle - 360f)
                                }
                                if (inSector) {
                                    selectedParent = sector.parentCategory
                                    selectedSub = sector.subCategory
                                    break
                                }
                            }
                        }

                        detectTapGestures(onTap = { handlePointer(it) })
                    }
                    .pointerInput(sectors, currentDrillDown) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dx = offset.x - cx
                                val dy = offset.y - cy
                                val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val angleFromTop = (deg + 90f + 360f) % 360f
                                for (sector in sectors) {
                                    val normStart = (sector.startAngle + 90f + 360f) % 360f
                                    val inSector = if (normStart + sector.sweepAngle <= 360f) {
                                        angleFromTop in normStart..(normStart + sector.sweepAngle)
                                    } else {
                                        angleFromTop >= normStart || angleFromTop <= (normStart + sector.sweepAngle - 360f)
                                    }
                                    if (inSector) {
                                        selectedParent = sector.parentCategory
                                        selectedSub = sector.subCategory
                                        break
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dx = change.position.x - cx
                                val dy = change.position.y - cy
                                val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val angleFromTop = (deg + 90f + 360f) % 360f
                                for (sector in sectors) {
                                    val normStart = (sector.startAngle + 90f + 360f) % 360f
                                    val inSector = if (normStart + sector.sweepAngle <= 360f) {
                                        angleFromTop in normStart..(normStart + sector.sweepAngle)
                                    } else {
                                        angleFromTop >= normStart || angleFromTop <= (normStart + sector.sweepAngle - 360f)
                                    }
                                    if (inSector) {
                                        selectedParent = sector.parentCategory
                                        selectedSub = sector.subCategory
                                        break
                                    }
                                }
                            }
                        )
                    }
            ) {
                val strokeWidth = 16.dp.toPx()
                val radius = (min(size.width, size.height) - strokeWidth) / 2f
                val arcTopLeft = Offset((size.width - 2 * radius) / 2f, (size.height - 2 * radius) / 2f)
                val arcSize = Size(radius * 2f, radius * 2f)

                // 1. Draw parent circumference indicator track in top-level view
                if (currentDrillDown == null) {
                    val innerRadius = radius - strokeWidth / 2f - 3.dp.toPx()
                    val innerTopLeft = Offset((size.width - 2 * innerRadius) / 2f, (size.height - 2 * innerRadius) / 2f)
                    val innerSize = Size(innerRadius * 2f, innerRadius * 2f)
                    var parentAngle = -90f
                    val totalVal = total.toDouble()
                    categories.forEach { cat ->
                        val catSweep = ((cat.amount.toDouble() / totalVal) * 360.0).toFloat()
                        val parentColor = CategoryMapping.colorFor(cat.name, cat.color)
                        val gap = if (catSweep > 4f) 2f else 0.5f
                        drawArc(
                            color = parentColor,
                            startAngle = parentAngle + gap / 2f,
                            sweepAngle = catSweep - gap,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = innerSize,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        parentAngle += catSweep
                    }
                }

                // 2. Draw circumference sub-arcs
                for (sector in sectors) {
                    val isSelected = if (sector.subCategory != null) {
                        sector.subCategory.name == selectedSub?.name
                    } else {
                        sector.parentCategory.name == selectedParent?.name && selectedSub == null
                    }
                    val gap = if (sector.sweepAngle > 3f) 1.5f else 0.5f
                    val currentStroke = if (isSelected) strokeWidth + 4.dp.toPx() else strokeWidth

                    drawArc(
                        color = sector.color,
                        startAngle = sector.startAngle + gap / 2f,
                        sweepAngle = sector.sweepAngle - gap,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = currentStroke)
                    )
                }
            }

            // Donut Center Content (Drill-down back button or active category/subcategory display)
            Crossfade(
                targetState = Triple(drillDownCategory, selectedParent, selectedSub),
                label = "pie_center_display"
            ) { (currentDrill, parent, sub) ->
                if (currentDrill != null) {
                    // In Drill-Down: Show Back Button & Parent Info in center
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .clickable { drillDownCategory = null }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.analytics_back_to_all_categories),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (sub != null) categoryLabel(sub.name) else categoryLabel(currentDrill.name),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(sub?.amount ?: currentDrill.amount, currency),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (sub != null) "${sub.percentageOfParent.toInt()}% of parent" else "Tap ➔ Back",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = TextUnit(9f, TextUnitType.Sp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (sub != null && parent != null) {
                    // Hovering / touching a Subcategory Arc
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .clickable { onSubCategoryClick(sub) }
                            .padding(4.dp)
                    ) {
                        CategoryIcon(
                            category = sub.name,
                            size = 24.dp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = categoryLabel(sub.name),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(sub.amount, currency),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${sub.percentageOfParent.toInt()}% of parent",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = TextUnit(9f, TextUnitType.Sp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (parent != null) {
                    // Hovering / touching a Parent Arc
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (parent.subCategories.isNotEmpty()) {
                                    drillDownCategory = parent
                                } else {
                                    onCategoryClick(parent)
                                }
                            }
                            .padding(4.dp)
                    ) {
                        CategoryIcon(
                            category = parent.name,
                            size = 24.dp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = categoryLabel(parent.name),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(parent.amount, currency),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (parent.subCategories.isNotEmpty()) {
                            Text(
                                text = "Drill in ➔",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = TextUnit(9f, TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "${parent.percentage.toInt()}% total",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = TextUnit(9f, TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Right Column: Smartly Expandable Legend
        LazyColumn(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .padding(start = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val currentDrill = drillDownCategory
            if (currentDrill != null) {
                // Header in Drill-Down mode: Return to All Categories
                item(key = "drill_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable { drillDownCategory = null }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.analytics_back_to_all_categories),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Subcategories of the drilled-down category
                items(currentDrill.subCategories, key = { "sub_${it.name}" }) { sub ->
                    val isSelected = selectedSub?.name == sub.name
                    SubCategoryLegendItem(
                        subCategory = sub,
                        currency = currency,
                        isSelected = isSelected,
                        onClick = {
                            selectedSub = sub
                            onSubCategoryClick(sub)
                        }
                    )
                }

                if (currentDrill.directAmount > BigDecimal.ZERO) {
                    item(key = "direct_spend_legend") {
                        DirectSpendLegendItem(
                            parentCategory = currentDrill,
                            currency = currency,
                            onClick = {
                                selectedSub = null
                                onCategoryClick(currentDrill)
                            }
                        )
                    }
                }
            } else {
                // Top-Level Legend with Smart Expansion
                items(categories, key = { "cat_${it.name}" }) { category ->
                    val isSelected = selectedParent?.name == category.name
                    val isExpanded = category.name in expandedLegendCategories

                    Column(modifier = Modifier.fillMaxWidth()) {
                        ParentLegendItem(
                            category = category,
                            currency = currency,
                            isSelected = isSelected,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedLegendCategories = if (isExpanded) {
                                    expandedLegendCategories - category.name
                                } else {
                                    expandedLegendCategories + category.name
                                }
                            },
                            onDrillDown = {
                                drillDownCategory = category
                                selectedParent = category
                                selectedSub = null
                            },
                            onClick = {
                                selectedParent = category
                                selectedSub = null
                                if (category.subCategories.isNotEmpty()) {
                                    drillDownCategory = category
                                } else {
                                    onCategoryClick(category)
                                }
                            }
                        )

                        // Inline expanded subcategories in legend
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = pwExpand(),
                            exit = pwCollapse()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                category.subCategories.forEach { sub ->
                                    SubCategoryLegendItem(
                                        subCategory = sub,
                                        currency = currency,
                                        isSelected = selectedSub?.name == sub.name,
                                        onClick = {
                                            selectedParent = category
                                            selectedSub = sub
                                            onSubCategoryClick(sub)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentLegendItem(
    category: CategoryData,
    currency: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDrillDown: () -> Unit,
    onClick: () -> Unit
) {
    val categoryColor = CategoryMapping.colorFor(category.name, category.color)
    val hasSubcategories = category.subCategories.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.medium)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            CategoryIcon(
                category = category.name,
                size = Dimensions.Icon.small,
                tint = categoryColor
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryLabel(category.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.formatAbbreviated(category.amount.toDouble(), currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.analytics_pie_percent, category.percentage.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action controls for subcategories
        if (hasSubcategories) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Expand chevron in distinct primary blue CTA style
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onToggleExpand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Drill-down button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDrillDown),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Drill in",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubCategoryLegendItem(
    subCategory: SubCategoryData,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val subColor = CategoryMapping.colorFor(subCategory.name, subCategory.color)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(subColor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryLabel(subCategory.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.formatAbbreviated(subCategory.amount.toDouble(), currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "(${subCategory.percentageOfParent.toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DirectSpendLegendItem(
    parentCategory: CategoryData,
    currency: String,
    onClick: () -> Unit
) {
    val parentColor = CategoryMapping.colorFor(parentCategory.name, parentCategory.color)
    val pctOfParent = if (parentCategory.amount > BigDecimal.ZERO) {
        (parentCategory.directAmount.divide(parentCategory.amount, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toInt()
    } else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(parentColor.copy(alpha = 0.6f))
        )

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.analytics_direct_spend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.formatAbbreviated(parentCategory.directAmount.toDouble(), currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "($pctOfParent%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
