package com.iptv.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class TimelineMarker(
    val positionFraction: Float,
    val label: String = ""
)

/**
 * Enhanced video timeline scrubber following mobile and TV UI/UX best practices:
 * - 56dp touch target with sleek animated track height
 * - Expanding thumb on touch/scrub with subtle haptic feedback
 * - Floating time and EPG programme preview tooltip bubble above the thumb
 * - EPG programme boundary markers along the track
 * - Smooth 60/120fps drag tracking with single pointer loop and commit-on-release seeking
 */
@Composable
fun EnhancedPlayerScrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    onSeek: (Long) -> Unit,
    markers: List<TimelineMarker> = emptyList(),
    isLiveMode: Boolean = false,
    liveOffsetMs: Long = 0L,
    isAtLiveEdge: Boolean = true,
    formatTimeLabel: (Long) -> String = { ms -> formatDuration(ms) },
    programmeTitleProvider: ((Long) -> String?)? = null,
    onScrubbingChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var dragProgressFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 5.dp,
        label = "trackHeight"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 22.dp else 14.dp,
        label = "thumbSize"
    )

    // Derived preview seconds to prevent per-pixel text layout recalculation
    val previewSeconds by remember(durationMs) {
        derivedStateOf {
            val frac = if (isDragging) dragProgressFraction else currentFraction
            ((frac.toDouble() * durationMs) / 1000.0).toLong().coerceAtLeast(0L)
        }
    }

    val previewPositionMs: Long = previewSeconds * 1000L

    val previewProgTitle by remember(durationMs, programmeTitleProvider) {
        derivedStateOf {
            if (programmeTitleProvider == null) null
            else {
                val frac = if (isDragging) dragProgressFraction else currentFraction
                val targetMs = (frac.toDouble() * durationMs).toLong()
                programmeTitleProvider.invoke(targetMs)
            }
        }
    }

    val isNearLiveEdge by remember(isLiveMode, isAtLiveEdge) {
        derivedStateOf {
            if (!isLiveMode) false
            else isAtLiveEdge && (!isDragging || dragProgressFraction >= 0.96f)
        }
    }

    val activeColor = if (isLiveMode) {
        if (isNearLiveEdge) Color(0xFF4CAF50) else Color(0xFFFF9800)
    } else {
        MaterialTheme.colorScheme.primary
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp), // 48dp+ hit target area
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()

        // Floating Tooltip Bubble above Thumb
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val tooltipEstimatedWidthPx = with(density) { 150.dp.toPx() }
                    val fraction = if (isDragging) dragProgressFraction else currentFraction
                    val thumbCenterPx = fraction * totalWidthPx
                    val targetX = (thumbCenterPx - (tooltipEstimatedWidthPx / 2f))
                        .coerceIn(0f, (totalWidthPx - tooltipEstimatedWidthPx).coerceAtLeast(0f))
                    IntOffset(targetX.roundToInt(), with(density) { (-6).dp.roundToPx() })
                }
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xEE1E1E1E),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time Label in Tooltip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isLiveMode) {
                                val offsetBehind = (durationMs - previewPositionMs).coerceAtLeast(0L)
                                if (offsetBehind <= 3000L) "LIVE" else "-${formatTimeLabel(offsetBehind)}"
                            } else {
                                "${formatTimeLabel(previewPositionMs)} / ${formatTimeLabel(durationMs)}"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (isLiveMode && (durationMs - previewPositionMs) <= 3000L) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                        }
                    }

                    // Programme Info in Tooltip (if matched)
                    if (!previewProgTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = previewProgTitle.orEmpty(),
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Scrubber Track Canvas with markers and played progress
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val width = size.width
            val height = size.height
            val cornerRadius = CornerRadius(height / 2f, height / 2f)
            val effectiveFrac = if (isDragging) dragProgressFraction else currentFraction

            // 1. Inactive Track Background
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                size = Size(width, height),
                cornerRadius = cornerRadius
            )

            // 2. Buffered Track
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    size = Size(width * bufferedFraction, height),
                    cornerRadius = cornerRadius
                )
            }

            // 3. Active Played Track
            drawRoundRect(
                color = activeColor,
                size = Size(width * effectiveFrac, height),
                cornerRadius = cornerRadius
            )

            // 4. EPG Programme Boundary Markers
            markers.forEach { marker ->
                if (marker.positionFraction in 0.02f..0.98f) {
                    val markerX = width * marker.positionFraction
                    // Marker tick line
                    drawRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(markerX - 1.5f, 0f),
                        size = Size(3f, height)
                    )
                }
            }
        }

        // Thumb Component with elevation
        Box(
            modifier = Modifier
                .offset {
                    val thumbRadiusPx = with(density) { (thumbSize / 2).toPx() }
                    val fraction = if (isDragging) dragProgressFraction else currentFraction
                    val thumbCenterPx = fraction * totalWidthPx
                    val x = (thumbCenterPx - thumbRadiusPx).roundToInt()
                    IntOffset(x, 0)
                }
                .size(thumbSize)
                .align(Alignment.CenterStart)
                .shadow(elevation = if (isDragging) 6.dp else 2.dp, shape = CircleShape)
                .background(Color.White, CircleShape)
                .padding(2.5.dp)
                .background(activeColor, CircleShape)
        )

        // Unified Touch & Drag Input Area spanning full 56dp height
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalWidthPx, durationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startFraction = (down.position.x / totalWidthPx).coerceIn(0f, 1f)
                        isDragging = true
                        dragProgressFraction = startFraction
                        onScrubbingChanged?.invoke(true)
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (_: Exception) {}

                        val pointerId = down.id
                        var hasReleased = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null) {
                                break
                            }
                            if (!change.pressed) {
                                if (change.previousPressed) {
                                    change.consume()
                                    hasReleased = true
                                }
                                break
                            }
                            change.consume()
                            val curFraction = (change.position.x / totalWidthPx).coerceIn(0f, 1f)
                            dragProgressFraction = curFraction
                        }

                        val finalFraction = dragProgressFraction
                        isDragging = false
                        onScrubbingChanged?.invoke(false)

                        if (hasReleased) {
                            onSeek((finalFraction.toDouble() * durationMs).toLong())
                        }
                    }
                }
        )
    }
}
