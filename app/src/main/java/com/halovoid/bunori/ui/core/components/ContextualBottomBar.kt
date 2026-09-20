package com.halovoid.bunori.ui.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.ErrorRed
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ContextualAction(
    val title: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit
)

/**
 * A fluid, animated contextual bottom action bar inspired by Mihon's bottom action menu.
 * Automatically handles bottom window insets, smooth expand/shrink transitions,
 * and animated label expansion on long-press with haptic feedback.
 */
@Composable
fun ContextualBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    selectedCount: Int? = null,
    actions: List<ContextualAction> = emptyList(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    customContent: (@Composable RowScope.() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = containerColor,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            val scope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current
            var confirmingIndex by remember { mutableStateOf<Int?>(null) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (customContent != null) {
                    customContent()
                } else {
                    actions.forEachIndexed { index, action ->
                        val isConfirming = confirmingIndex == index
                        val animatedWeight by animateFloatAsState(
                            targetValue = if (isConfirming) 1.8f else 1f,
                            label = "action_weight"
                        )

                        val tint = if (!action.enabled) {
                            SecondaryText.copy(alpha = 0.38f)
                        } else {
                            PrimaryText
                        }

                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .weight(animatedWeight)
                                .combinedClickable(
                                    enabled = action.enabled,
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = 24.dp),
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        action.onLongClick?.invoke()
                                        confirmingIndex = index
                                        scope.launch {
                                            delay(1500)
                                            if (confirmingIndex == index) {
                                                confirmingIndex = null
                                            }
                                        }
                                    },
                                    onClick = {
                                        action.onClick()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.title,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )

                                AnimatedVisibility(
                                    visible = isConfirming,
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                ) {
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 2.dp)
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
