package com.halovoid.bunori.ui.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.R
import com.halovoid.bunori.ui.core.theme.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun WelcomeScreen(
    onNext: () -> Unit
) {
    OnboardingStep(
        title = null,
        subtitle = null,
        onNext = onNext,
        isScrollable = false
    ) {
        // Outer Box fills the available space and is responsible for
        // horizontally centering the content block on wide screens.
        // (This replaces the old fillMaxSize()+widthIn() chain on the
        // Column, which never actually capped the width because
        // fillMaxSize() locks minWidth == maxWidth before widthIn() runs.)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Heading & Subtitle (left-aligned text inside the centered block)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Welcome to Bunori",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PrimaryText,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "A complete platform for discovering, reading, and downloading web novels",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Splash icon (this one stays visually centered within the block)
                Image(
                    painter = painterResource(id = R.mipmap.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Feature items list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FeatureItem(
                            title = "One Library, Many Sources",
                            description = "Discover and track novels across multiple web sources with unified search and filtering in one library."
                        )

                        FeatureItem(
                            title = "Extensible by Design",
                            description = "Add and update source extensions independently with lightweight .bext packages, without waiting for app updates."
                        )

                        FeatureItem(
                            title = "A Reader for Long Form Reading",
                            description = "Tailor typography, margins, reading rulers, tap gestures, and true AMOLED dark mode to your comfort."
                        )

                        FeatureItem(
                            title = "Powerful Offline Reading",
                            description = "Download specific chapter ranges in the background with auto-resume for uninterrupted offline reading."
                        )

                        FeatureItem(
                            title = "Built to Stay Out of Your Way",
                            description = "Crawling, chapter updates, downloads, and cache management run quietly in the background."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
            lineHeight = 19.sp,
            fontSize = 13.sp
        )
    }
}