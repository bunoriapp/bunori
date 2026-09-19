package com.halovoid.bunori.ui.feature.reader.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.domain.models.ReadingMode

/**
 * Visual guide overlay showing the active tap zones with distinct colors and descriptions.
 * - Left & Right regions: Orange tint
 * - Center region: Yellow tint
 * Displays for 2 seconds when reader opens or reading mode changes.
 * Direct clicks on cards immediately execute the zone's action and dismiss the guide.
 */
@Composable
fun TapZoneGuideOverlay(
    visible: Boolean,
    readingMode: ReadingMode,
    onDismiss: () -> Unit,
    onLeftTap: () -> Unit = {},
    onCenterTap: () -> Unit = {},
    onRightTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 250)),
        exit = fadeOut(animationSpec = tween(durationMillis = 400)),
        modifier = modifier.fillMaxSize()
    ) {

        /**
         * Distinctive background colors
         * Orange = Navigation (Horizontal)
         * Blue = Menu / FullScreen
         * Yellow = Navigation (Vertical)
         */
        val horizontalNavigationBg = Color(0xFFF97316).copy(alpha = 0.24f)
        val horizontalNavigationText = Color(0xFFFFF7ED)

        val centerBg = Color(0xFF3B82F6).copy(alpha = 0.28f)
        val centerText = Color(0xFFEFF6FF)

        val isContinuous = readingMode == ReadingMode.CONTINUOUS
        val isVerticalTap = readingMode == ReadingMode.VERTICAL_TAP
        val isRtl = readingMode == ReadingMode.PAGED_RTL

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        Log.d("BunoriReader", "TapZoneGuideOverlay: dismissed by background tap")
                        onDismiss()
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
            if (isVerticalTap) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Zone: 30%
                    TapZoneCardVertical(
                        weight = 0.30f,
                        bgColor = horizontalNavigationBg,
                        textColor = horizontalNavigationText,
                        icon = Icons.Default.ArrowUpward,
                        title = "Scroll Up",
                        subtitle = "Tap Top (30%)",
                        onClick = {
                            onDismiss()
                            onLeftTap()
                        }
                    )

                    // Center Zone: 40%
                    TapZoneCardVertical(
                        weight = 0.40f,
                        bgColor = centerBg,
                        textColor = centerText,
                        icon = Icons.Default.TouchApp,
                        title = "Menu / Fullscreen",
                        subtitle = "Tap Center (40%)",
                        onClick = {
                            onDismiss()
                            onCenterTap()
                        }
                    )

                    // Bottom Zone: 30%
                    TapZoneCardVertical(
                        weight = 0.30f,
                        bgColor = horizontalNavigationBg,
                        textColor = horizontalNavigationText,
                        icon = Icons.Default.ArrowDownward,
                        title = "Scroll Down",
                        subtitle = "Tap Bottom (30%)",
                        onClick = {
                            onDismiss()
                            onRightTap()
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Zone: Orange
                    TapZoneCard(
                        weight = if (isContinuous) 0.25f else 0.333f,
                        bgColor = horizontalNavigationBg,
                        textColor = horizontalNavigationText,
                        icon = when {
                            isContinuous -> Icons.Default.ArrowUpward
                            isRtl -> Icons.AutoMirrored.Filled.NavigateNext
                            else -> Icons.AutoMirrored.Filled.NavigateBefore
                        },
                        title = when {
                            isContinuous -> "Scroll Up"
                            isRtl -> "Next Page"
                            else -> "Prev Page"
                        },
                        subtitle = if (isContinuous) "Tap Left (25%)" else "Tap Left (33%)",
                        onClick = {
                            Log.d("BunoriReader", "TapZoneGuideOverlay: Left card clicked")
                            onDismiss()
                            if (isRtl) onRightTap() else onLeftTap()
                        }
                    )

                    // Center Zone: Blue
                    TapZoneCard(
                        weight = if (isContinuous) 0.50f else 0.334f,
                        bgColor = centerBg,
                        textColor = centerText,
                        icon = Icons.Default.TouchApp,
                        title = "Menu / Fullscreen",
                        subtitle = if (isContinuous) "Tap Center (50%)" else "Tap Center (34%)",
                        onClick = {
                            Log.d("BunoriReader", "TapZoneGuideOverlay: Center card clicked -> onCenterTap()")
                            onDismiss()
                            onCenterTap()
                        }
                    )

                    // Right Zone: Orange
                    TapZoneCard(
                        weight = if (isContinuous) 0.25f else 0.333f,
                        bgColor = horizontalNavigationBg,
                        textColor = horizontalNavigationText,
                        icon = when {
                            isContinuous -> Icons.Default.ArrowDownward
                            isRtl -> Icons.AutoMirrored.Filled.NavigateBefore
                            else -> Icons.AutoMirrored.Filled.NavigateNext
                        },
                        title = when {
                            isContinuous -> "Scroll Down"
                            isRtl -> "Prev Page"
                            else -> "Next Page"
                        },
                        subtitle = if (isContinuous) "Tap Right (25%)" else "Tap Right (33%)",
                        onClick = {
                            Log.d("BunoriReader", "TapZoneGuideOverlay: Right card clicked")
                            onDismiss()
                            if (isRtl) onLeftTap() else onRightTap()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TapZoneCard(
    weight: Float,
    bgColor: Color,
    textColor: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ColumnScope.TapZoneCardVertical(
    weight: Float,
    bgColor: Color,
    textColor: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

