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
import androidx.compose.ui.text.style.TextAlign

@Composable
fun WelcomeScreen(
    onNext: () -> Unit
) {
    OnboardingStep(
        title = "Welcome to Bunori",
        subtitle = "A complete platform for discovering, reading, and downloading web novels with modular extensions.",
        onNext = onNext,
        isScrollable = false
    ) {
        // Splash icon in its previous position (below title & subtitle)
        Image(
            painter = painterResource(id = R.mipmap.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Info vertically centered in the remaining screen space
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
                    title = "A Reader Built for Long-Form Reading",
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
