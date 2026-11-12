package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

/** Represents a parent category arc capsule (the outer containing rectangle curved into an arc). */
private data class ParentCapsule(
    val category: CategoryData,
    val color: Color,
    val actualStartAngle: Float,
    val actualSweepAngle: Float,
    val subSectors: List<ArcSector>
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        val currentDrillDown = drillDownCategory
        val (parentCapsules, allSectors) = remember(categories, currentDrillDown) {
            val capsules = mutableListOf<ParentCapsule>()
            val sectors = mutableListOf<ArcSector>()

            if (currentDrillDown == null) {
                // Top-level View: All parent categories with subcategory nested inside arc capsules
                val totalVal = total.toDouble()
                var currentAngle = -90f
                val parentGap = 3.5f // Distinct gap between parent category capsules in degrees

                categories.forEach { cat ->
                    val catRawSweep = ((cat.amount.toDouble() / totalVal) * 360.0).toFloat()
                    if (catRawSweep > 0.1f) {
                        val actualStart = currentAngle + parentGap / 2f
                        val actualSweep = (catRawSweep - parentGap).coerceAtLeast(0.5f)
                        val parentColor = CategoryMapping.colorFor(cat.name, cat.color)

                        // Margin inside the parent rectangle (encloses the inner rectangle on all sides)
                        val angularMargin = if (actualSweep > 8f) 1.6f else 0.4f
                        val innerStart = actualStart + angularMargin
                        val innerSpan = (actualSweep - 2f * angularMargin).coerceAtLeast(0.2f)

                        val subList = mutableListOf<ArcSector>()
                        if (cat.subCategories.isEmpty()) {
                            // No subcategories: Inner solid rectangle in parent color
                            val sector = ArcSector(
                                startAngle = innerStart,
                                sweepAngle = innerSpan,
                                parentCategory = cat,
                                color = parentColor
                            )
                            subList.add(sector)
                            sectors.add(sector)
                        } else {
                            // Subcategories present: Divided into nested rectangles with distinct curated colors
                            val catTotalVal = cat.amount.toDouble()
                            val totalInnerItems = cat.subCategories.size + (if (cat.directAmount > BigDecimal.ZERO) 1 else 0)
                            val subGap = if (totalInnerItems > 1) 1.2f else 0f
                            val totalGaps = subGap * (totalInnerItems - 1)
                            val availableForSectors = (innerSpan - totalGaps).coerceAtLeast(0.2f)

                            var subCurrAngle = innerStart
                            cat.subCategories.forEachIndexed { index, sub ->
                                val subRatio = sub.amount.toDouble() / catTotalVal
                                val subSweep = (availableForSectors * subRatio).toFloat()
                                val subColor = CategoryMapping.subCategoryColor(
                                    name = sub.name,
                                    overrideHex = sub.color,
                                    index = index,
                                    parentName = cat.name
                                )
                                val sector = ArcSector(
                                    startAngle = subCurrAngle,
                                    sweepAngle = subSweep,
                                    parentCategory = cat,
                                    subCategory = sub,
                                    color = subColor
                                )
                                subList.add(sector)
                                sectors.add(sector)
                                subCurrAngle += subSweep + subGap
                            }

                            if (cat.directAmount > BigDecimal.ZERO) {
                                val directRatio = cat.directAmount.toDouble() / catTotalVal
                                val directSweep = (availableForSectors * directRatio).toFloat()
                                val directSector = ArcSector(
                                    startAngle = subCurrAngle,
                                    sweepAngle = directSweep,
                                    parentCategory = cat,
                                    isDirect = true,
                                    color = parentColor.copy(alpha = 0.85f)
                                )
                                subList.add(directSector)
                                sectors.add(directSector)
                            }
                        }

                        capsules.add(
                            ParentCapsule(
                                category = cat,
                                color = parentColor,
                                actualStartAngle = actualStart,
                                actualSweepAngle = actualSweep,
                                subSectors = subList
                            )
                        )
                        currentAngle += catRawSweep
                    }
                }
            } else {
                // Drill-Down View: Focused on one category's subcategories (full thickness, no radial padding)
                val catTotalVal = currentDrillDown.amount.toDouble()
                val totalItems = currentDrillDown.subCategories.size + (if (currentDrillDown.directAmount > BigDecimal.ZERO) 1 else 0)
                val subGap = if (totalItems > 1) 2.5f else 0f
                val totalGaps = subGap * totalItems
                val availableSweep = (360f - totalGaps).coerceAtLeast(10f)

                var subCurrAngle = -90f + subGap / 2f
                currentDrillDown.subCategories.forEachIndexed { index, sub ->
                    val subRatio = sub.amount.toDouble() / catTotalVal
                    val subSweep = (availableSweep * subRatio).toFloat()
                    val subColor = CategoryMapping.subCategoryColor(
                        name = sub.name,
                        overrideHex = sub.color,
                        index = index,
                        parentName = currentDrillDown.name
                    )
                    sectors.add(
                        ArcSector(
                            startAngle = subCurrAngle,
                            sweepAngle = subSweep,
                            parentCategory = currentDrillDown,
                            subCategory = sub,
                            color = subColor
                        )
                    )
                    subCurrAngle += subSweep + subGap
                }
                if (currentDrillDown.directAmount > BigDecimal.ZERO) {
                    val directRatio = currentDrillDown.directAmount.toDouble() / catTotalVal
                    val directSweep = (availableSweep * directRatio).toFloat()
                    val catColor = CategoryMapping.colorFor(currentDrillDown.name, currentDrillDown.color)
                    sectors.add(
                        ArcSector(
                            startAngle = subCurrAngle,
                            sweepAngle = directSweep,
                            parentCategory = currentDrillDown,
                            isDirect = true,
                            color = catColor.copy(alpha = 0.85f)
                        )
                    )
                }
            }
            Pair(capsules, sectors)
        }

        // 1. Top Section: Big, Beautiful Donut Chart Centered Horizontally
        Box(
            modifier = Modifier
                .size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(allSectors, parentCapsules, currentDrillDown) {
                        fun handlePointer(offset: Offset, isTap: Boolean) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val r = sqrt(dx * dx + dy * dy)
                            val parentTrackPx = 26.dp.toPx()
                            val radiusPx = (min(size.width, size.height) - parentTrackPx) / 2f
                            val innerHoleRadius = radiusPx - parentTrackPx / 2f
                            val outerRimRadius = radiusPx + parentTrackPx / 2f

                            // If touched inside center hole
                            if (r < innerHoleRadius - 4.dp.toPx()) {
                                if (isTap) {
                                    if (currentDrillDown != null) {
                                        drillDownCategory = null
                                        selectedSub = null
                                    } else if (selectedSub != null) {
                                        // Tapping center hole while a subcategory is active returns to parent overview!
                                        selectedSub = null
                                    } else if (selectedParent?.subCategories?.isNotEmpty() == true) {
                                        drillDownCategory = selectedParent
                                    }
                                }
                                return
                            }

                            // Beyond outer rim
                            if (r > outerRimRadius + 18.dp.toPx()) {
                                return
                            }

                            // Calculate angle from 12 o'clock in [0, 360)
                            val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val angleFromTop = (deg + 90f + 360f) % 360f

                            if (currentDrillDown != null) {
                                // Drill-Down View: Focused on one category's subcategories
                                for (sector in allSectors) {
                                    val normStart = (sector.startAngle + 90f + 360f) % 360f
                                    val inSector = if (normStart + sector.sweepAngle <= 360f) {
                                        angleFromTop in normStart..(normStart + sector.sweepAngle)
                                    } else {
                                        angleFromTop >= normStart || angleFromTop <= (normStart + sector.sweepAngle - 360f)
                                    }
                                    if (inSector) {
                                        selectedParent = currentDrillDown
                                        selectedSub = sector.subCategory
                                        break
                                    }
                                }
                            } else {
                                // Top Overview View: Smart radial & angular detection
                                // 1. Find matching parent capsule by angle
                                val matchedCapsule = parentCapsules.firstOrNull { capsule ->
                                    val normStart = (capsule.actualStartAngle + 90f + 360f) % 360f
                                    if (normStart + capsule.actualSweepAngle <= 360f) {
                                        angleFromTop in normStart..(normStart + capsule.actualSweepAngle)
                                    } else {
                                        angleFromTop >= normStart || angleFromTop <= (normStart + capsule.actualSweepAngle - 360f)
                                    }
                                }

                                if (matchedCapsule != null) {
                                    selectedParent = matchedCapsule.category

                                    if (matchedCapsule.category.subCategories.isEmpty()) {
                                        selectedSub = null
                                    } else {
                                        // Radial discrimination between outer main category container and inner subcategory
                                        val radialThreshold = 4.dp.toPx()
                                        val isOuterRim = r > radiusPx + radialThreshold
                                        val isInnerRim = r < radiusPx - radialThreshold

                                        if (isOuterRim || isInnerRim) {
                                            // User is touching/hovering the outer or inner frame of the main category
                                            selectedSub = null
                                        } else {
                                            // User is touching/hovering the center track where inner subcategories live
                                            val matchedSubSector = matchedCapsule.subSectors.firstOrNull { subSector ->
                                                val normSubStart = (subSector.startAngle + 90f + 360f) % 360f
                                                if (normSubStart + subSector.sweepAngle <= 360f) {
                                                    angleFromTop in normSubStart..(normSubStart + subSector.sweepAngle)
                                                } else {
                                                    angleFromTop >= normSubStart || angleFromTop <= (normSubStart + subSector.sweepAngle - 360f)
                                                }
                                            }

                                            if (matchedSubSector != null && matchedSubSector.subCategory != null) {
                                                if (isTap && selectedSub?.name == matchedSubSector.subCategory.name) {
                                                    // Tapping the already selected subcategory toggles to parent category overview!
                                                    selectedSub = null
                                                } else {
                                                    selectedSub = matchedSubSector.subCategory
                                                }
                                            } else {
                                                selectedSub = null
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            handlePointer(down.position, isTap = true)
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                handlePointer(change.position, isTap = false)
                            }
                        }
                    }
            ) {
                val parentTrackWidth = 26.dp.toPx()
                val subTrackWidth = 14.dp.toPx()
                val radius = (min(size.width, size.height) - parentTrackWidth) / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arcTopLeft = Offset(cx - radius, cy - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                if (currentDrillDown == null) {
                    // 1. Draw Parent Container Enclosures (The distinct outer rectangles curved into arcs with flat ends)
                    parentCapsules.forEach { capsule ->
                        val isParentSelected = (capsule.category.name == selectedParent?.name)
                        val isParentFocused = isParentSelected && selectedSub == null
                        val baseColor = capsule.color

                        // Capsule background body fill (distinct tinted background)
                        drawArc(
                            color = baseColor.copy(alpha = if (isParentFocused) 0.55f else if (isParentSelected) 0.40f else 0.28f),
                            startAngle = capsule.actualStartAngle,
                            sweepAngle = capsule.actualSweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = parentTrackWidth, cap = StrokeCap.Butt)
                        )

                        // Solid distinct outer and inner border lines
                        val borderWidth = if (isParentFocused) 2.dp.toPx() else 1.5.dp.toPx()
                        val outerR = radius + (parentTrackWidth - borderWidth) / 2f
                        drawArc(
                            color = baseColor,
                            startAngle = capsule.actualStartAngle,
                            sweepAngle = capsule.actualSweepAngle,
                            useCenter = false,
                            topLeft = Offset(cx - outerR, cy - outerR),
                            size = Size(outerR * 2f, outerR * 2f),
                            style = Stroke(width = borderWidth, cap = StrokeCap.Butt)
                        )

                        val innerR = radius - (parentTrackWidth - borderWidth) / 2f
                        drawArc(
                            color = baseColor,
                            startAngle = capsule.actualStartAngle,
                            sweepAngle = capsule.actualSweepAngle,
                            useCenter = false,
                            topLeft = Offset(cx - innerR, cy - innerR),
                            size = Size(innerR * 2f, innerR * 2f),
                            style = Stroke(width = borderWidth, cap = StrokeCap.Butt)
                        )

                        // Flat radial end edge lines to fully enclose the rectangle at both ends
                        val startRad = Math.toRadians(capsule.actualStartAngle.toDouble())
                        val cosStart = cos(startRad).toFloat()
                        val sinStart = sin(startRad).toFloat()
                        drawLine(
                            color = baseColor,
                            start = Offset(cx + innerR * cosStart, cy + innerR * sinStart),
                            end = Offset(cx + outerR * cosStart, cy + outerR * sinStart),
                            strokeWidth = borderWidth,
                            cap = StrokeCap.Butt
                        )

                        val endAngle = capsule.actualStartAngle + capsule.actualSweepAngle
                        val endRad = Math.toRadians(endAngle.toDouble())
                        val cosEnd = cos(endRad).toFloat()
                        val sinEnd = sin(endRad).toFloat()
                        drawLine(
                            color = baseColor,
                            start = Offset(cx + innerR * cosEnd, cy + innerR * sinEnd),
                            end = Offset(cx + outerR * cosEnd, cy + outerR * sinEnd),
                            strokeWidth = borderWidth,
                            cap = StrokeCap.Butt
                        )
                    }

                    // 2. Draw Inner Subcategory Rectangles (Thinner, flat ends StrokeCap.Butt, enclosed with margin on all sides)
                    allSectors.forEach { sector ->
                        val isSubSelected = (sector.subCategory != null && sector.subCategory.name == selectedSub?.name)
                        val isParentOnlySelected = (sector.parentCategory.name == selectedParent?.name && selectedSub == null)
                        val currentStroke = if (isSubSelected) subTrackWidth + 2.5.dp.toPx() else subTrackWidth

                        val drawAlpha = when {
                            selectedSub != null && isSubSelected -> 1.0f
                            selectedSub != null && !isSubSelected -> 0.45f
                            isParentOnlySelected -> 0.85f
                            else -> 1.0f
                        }

                        drawArc(
                            color = sector.color.copy(alpha = drawAlpha),
                            startAngle = sector.startAngle,
                            sweepAngle = max(0.1f, sector.sweepAngle),
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = currentStroke, cap = StrokeCap.Butt)
                        )
                    }
                } else {
                    // Drill-Down View: Focused on a single parent category
                    // Subcategories fill the full donut band thickness with clean flat cuts (zero radial padding!)
                    val drillStroke = parentTrackWidth
                    allSectors.forEach { sector ->
                        val isSelected = (sector.subCategory?.name == selectedSub?.name)
                        val currentStroke = if (isSelected) drillStroke + 3.dp.toPx() else drillStroke
                        drawArc(
                            color = sector.color,
                            startAngle = sector.startAngle,
                            sweepAngle = max(0.1f, sector.sweepAngle),
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = currentStroke, cap = StrokeCap.Butt)
                        )
                    }
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
                            .size(160.dp)
                            .clip(CircleShape)
                            .clickable {
                                drillDownCategory = null
                                selectedSub = null
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.analytics_back_to_all_categories),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (sub != null) categoryLabel(sub.name) else categoryLabel(currentDrill.name),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(sub?.amount ?: currentDrill.amount, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (sub != null) {
                                "${sub.percentageOfParent.toInt()}% of ${currentDrill.name}"
                            } else {
                                "Tap ➔ Back to All"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (sub != null && parent != null) {
                    // Hovering / touching an Inner Subcategory Arc
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .clickable {
                                // Tapping center when sub is selected resets to parent category overview
                                selectedSub = null
                            }
                            .padding(8.dp)
                    ) {
                        CategoryIcon(
                            category = sub.name,
                            size = 28.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = categoryLabel(sub.name),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(sub.amount, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${sub.percentageOfParent.toInt()}% of ${parent.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "(${sub.percentageOfTotal.toInt()}% total · Tap for ${parent.name})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = TextUnit(10f, TextUnitType.Sp)),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (parent != null) {
                    // Hovering / touching a Parent Arc (Outer Main Category)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (parent.subCategories.isNotEmpty()) {
                                    drillDownCategory = parent
                                } else {
                                    onCategoryClick(parent)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        CategoryIcon(
                            category = parent.name,
                            size = 32.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = categoryLabel(parent.name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(parent.amount, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        if (parent.subCategories.isNotEmpty()) {
                            Text(
                                text = "${parent.percentage.toInt()}% total · ${parent.subCategories.size} subcategories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Drill in ➔",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "${parent.percentage.toInt()}% total spending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Bottom Section: Smartly Expandable Full-Width Legend Centered Below Pie
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            val currentDrill = drillDownCategory
            if (currentDrill != null) {
                // Header banner in Drill-Down mode: Return to All Categories
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
                        .clickable {
                            drillDownCategory = null
                            selectedSub = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.analytics_back_to_all_categories),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${categoryLabel(currentDrill.name)} (${currentDrill.subCategories.size})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subcategories of the drilled-down category
                currentDrill.subCategories.forEachIndexed { index, sub ->
                    val isSelected = selectedSub?.name == sub.name
                    SubCategoryLegendItem(
                        subCategory = sub,
                        parentCategory = currentDrill,
                        index = index,
                        currency = currency,
                        isSelected = isSelected,
                        onClick = {
                            selectedSub = sub
                            onSubCategoryClick(sub)
                        }
                    )
                }

                if (currentDrill.directAmount > BigDecimal.ZERO) {
                    DirectSpendLegendItem(
                        parentCategory = currentDrill,
                        currency = currency,
                        onClick = {
                            selectedSub = null
                            onCategoryClick(currentDrill)
                        }
                    )
                }
            } else {
                // Top-Level Legend with Smart Expansion
                categories.forEach { category ->
                    val isSelected = selectedParent?.name == category.name && selectedSub == null
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
                                    .padding(start = 32.dp, top = 2.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                category.subCategories.forEachIndexed { index, sub ->
                                    val isSubSelected = selectedParent?.name == category.name && selectedSub?.name == sub.name
                                    SubCategoryLegendItem(
                                        subCategory = sub,
                                        parentCategory = category,
                                        index = index,
                                        currency = currency,
                                        isSelected = isSubSelected,
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
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
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

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryLabel(category.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = CurrencyFormatter.formatCurrency(category.amount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.analytics_pie_percent, category.percentage.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action controls for subcategories
        if (hasSubcategories) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Expand chevron chip in distinct primary blue CTA style
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onToggleExpand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand subcategories",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Drill-in button
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onDrillDown)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Drill in",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Drill in",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubCategoryLegendItem(
    subCategory: SubCategoryData,
    parentCategory: CategoryData? = null,
    index: Int = 0,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val subColor = CategoryMapping.subCategoryColor(
        name = subCategory.name,
        overrideHex = subCategory.color,
        index = index,
        parentName = parentCategory?.name
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(subColor)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryLabel(subCategory.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = CurrencyFormatter.formatCurrency(subCategory.amount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${subCategory.percentageOfParent.toInt()}% of parent (${subCategory.percentageOfTotal.toInt()}% total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
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
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parentColor.copy(alpha = 0.6f))
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.analytics_direct_spend),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = CurrencyFormatter.formatCurrency(parentCategory.directAmount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "($pctOfParent% of parent)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
