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

@Composable
fun WelcomeScreen(
    onNext: () -> Unit
) {
    OnboardingStep(
        title = "Welcome to Bunori",
        subtitle = "A modern web novel reader, background crawler, and offline library.",
        buttonText = "Get Started",
        onNext = onNext
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier.size(76.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                FeatureItem(
                    title = "Multi-Source Aggregation",
                    description = "Search, discover, and track novels across numerous web sources with unified filtering and metadata."
                )

                FeatureItem(
                    title = "Modular .bext Extensions",
                    description = "Install and update source crawlers directly inside the app without needing system APK installs."
                )

                FeatureItem(
                    title = "Distraction-Free Reader",
                    description = "Customize typography, reading rulers, layout spacing, tap gestures, and true AMOLED dark mode."
                )

                FeatureItem(
                    title = "Offline Downloads & Export",
                    description = "Queue background chapter downloads for offline reading, and export full novels into clean EPUB books."
                )

                FeatureItem(
                    title = "Smart Library & Automation",
                    description = "Automated chapter update checks, silent background updates, intelligent cache pruning, and easy backups."
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
