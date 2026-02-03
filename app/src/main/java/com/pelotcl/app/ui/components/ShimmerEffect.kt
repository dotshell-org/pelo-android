package com.pelotcl.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colors for shimmer effect
 */
private val ShimmerColorShades = listOf(
    Color.LightGray.copy(alpha = 0.6f),
    Color.LightGray.copy(alpha = 0.2f),
    Color.LightGray.copy(alpha = 0.6f)
)

/**
 * Animated shimmer brush for skeleton loading effect
 */
@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translate"
        )
        Brush.linearGradient(
            colors = ShimmerColorShades,
            start = Offset(translateAnimation - 200f, translateAnimation - 200f),
            end = Offset(translateAnimation, translateAnimation)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

/**
 * Skeleton placeholder for a single line chip (80x48dp)
 */
@Composable
fun LineChipSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .size(width = 80.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

/**
 * Skeleton placeholder for a row of 4 line chips
 */
@Composable
fun LineRowSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) {
            LineChipSkeleton(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Skeleton placeholder for a category header
 */
@Composable
fun CategoryHeaderSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 100.dp
) {
    val brush = shimmerBrush()
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(width)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Full skeleton loading for the lines bottom sheet
 * Displays placeholder skeleton UI while lines are loading
 */
@Composable
fun LinesLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Simulate "Métro" category
        CategoryHeaderSkeleton(width = 60.dp)
        LineRowSkeleton()

        // Simulate "Tramway" category
        CategoryHeaderSkeleton(width = 80.dp)
        LineRowSkeleton()
        LineRowSkeleton()

        // Simulate "Chrono" category
        CategoryHeaderSkeleton(width = 70.dp)
        LineRowSkeleton()
        LineRowSkeleton()
        LineRowSkeleton()

        // Simulate "Bus" category
        CategoryHeaderSkeleton(width = 40.dp)
        LineRowSkeleton()
        LineRowSkeleton()
    }
}

/**
 * Skeleton placeholder for the map loading indicator
 * More visually appealing than a simple CircularProgressIndicator
 */
@Composable
fun MapLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush(targetValue = 2000f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    )
}

/**
 * Skeleton for a stop/station item in a list
 */
@Composable
fun StopItemSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Line icon placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Stop name placeholder
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

/**
 * Skeleton for journey/itinerary card
 */
@Composable
fun JourneyCardSkeleton(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Time and duration row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Line icons row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
            }
        }
    }
}
