package com.halovoid.bunori.ui.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingStep(
    title: String,
    subtitle: String,
    stepNumber: Int? = null,
    totalSteps: Int = 4,
    buttonText: String? = null,
    onBack: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    isNextEnabled: Boolean = true,
    nextButtonText: String = "Next",
    bottomAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (stepNumber != null && totalSteps > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 1..totalSteps) {
                                val isCurrent = i == stepNumber
                                val isVisited = i < stepNumber
                                val targetWidth = if (isCurrent) 22.dp else 7.dp
                                val width by animateDpAsState(
                                    targetValue = targetWidth,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "DotWidth_$i"
                                )
                                val targetColor = when {
                                    isCurrent -> BrandAccent
                                    isVisited -> BrandAccent.copy(alpha = 0.5f)
                                    else -> BorderColor.copy(alpha = 0.4f)
                                }
                                val color by animateColorAsState(
                                    targetValue = targetColor,
                                    animationSpec = tween(250),
                                    label = "DotColor_$i"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(width = width, height = 7.dp)
                                        .clip(RoundedCornerShape(3.5.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryText
                            )
                        }
                    }
                },
                actions = {
                    if (onNext != null && buttonText == null) {
                        TextButton(
                            onClick = onNext,
                            enabled = isNextEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = BrandAccent,
                                disabledContentColor = SecondaryText.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = nextButtonText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            if (buttonText != null && onNext != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Button(
                            onClick = onNext,
                            enabled = isNextEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandAccent,
                                contentColor = Color.White,
                                disabledContainerColor = Color.DarkGray.copy(alpha = 0.5f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = buttonText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else if (bottomAction != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        bottomAction()
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryText,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            content()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
