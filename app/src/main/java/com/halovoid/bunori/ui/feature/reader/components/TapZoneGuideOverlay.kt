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
        val orangeBg = Color(0xFFF97316).copy(alpha = 0.24f)
        val orangeBorder = Color(0xFFFB923C).copy(alpha = 0.6f)
        val orangeText = Color(0xFFFED7AA)

        val yellowBg = Color(0xFFEAB308).copy(alpha = 0.24f)
        val yellowBorder = Color(0xFFFACC15).copy(alpha = 0.6f)
        val yellowText = Color(0xFFFEF08A)

        val isContinuous = readingMode == ReadingMode.CONTINUOUS

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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Left Zone: Orange
                TapZoneCard(
                    weight = if (isContinuous) 0.25f else 0.333f,
                    bgColor = orangeBg,
                    borderColor = orangeBorder,
                    textColor = orangeText,
                    icon = if (isContinuous) Icons.Default.ArrowUpward else Icons.AutoMirrored.Filled.NavigateBefore,
                    title = if (isContinuous) "Scroll Up" else "Prev Page",
                    subtitle = if (isContinuous) "Tap Left (25%)" else "Tap Left (33%)",
                    onClick = {
                        Log.d("BunoriReader", "TapZoneGuideOverlay: Left card clicked -> onLeftTap()")
                        onDismiss()
                        onLeftTap()
                    }
                )

                // Center Zone: Yellow
                TapZoneCard(
                    weight = if (isContinuous) 0.50f else 0.334f,
                    bgColor = yellowBg,
                    borderColor = yellowBorder,
                    textColor = yellowText,
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
                    bgColor = orangeBg,
                    borderColor = orangeBorder,
                    textColor = orangeText,
                    icon = if (isContinuous) Icons.Default.ArrowDownward else Icons.AutoMirrored.Filled.NavigateNext,
                    title = if (isContinuous) "Scroll Down" else "Next Page",
                    subtitle = if (isContinuous) "Tap Right (25%)" else "Tap Right (33%)",
                    onClick = {
                        Log.d("BunoriReader", "TapZoneGuideOverlay: Right card clicked -> onRightTap()")
                        onDismiss()
                        onRightTap()
                    }
                )
            }
        }
    }
}

@Composable
private fun RowScope.TapZoneCard(
    weight: Float,
    bgColor: Color,
    borderColor: Color,
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
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(16.dp))
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
