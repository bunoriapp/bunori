package com.halovoid.bunori.ui.feature.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.MarkdownContent
import com.halovoid.bunori.ui.core.theme.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDetailScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Update Details", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                    navigationIconContentColor = PrimaryText
                )
            )
        }
    ) { innerPadding ->
        val state = updateState
        if (state is AppUpdateState.UpdateAvailable || state is AppUpdateState.Downloading || state is AppUpdateState.ReadyToInstall || state is AppUpdateState.Installing) {
            val availableState = when (state) {
                is AppUpdateState.UpdateAvailable -> state
                else -> null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    val tagName = when (state) {
                        is AppUpdateState.UpdateAvailable -> state.tagName
                        else -> "Latest Version"
                    }

                    val publishedDate = remember(availableState?.publishedAt) {
                        availableState?.publishedAt?.let {
                            try {
                                val zdt = ZonedDateTime.parse(it)
                                zdt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH))
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column {
                        Text(
                            text = tagName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        if (publishedDate != null) {
                            Text(
                                text = publishedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(24.dp))

                    val body = availableState?.releaseNotes
                    if (!body.isNullOrBlank()) {
                        MarkdownContent(body)
                    } else {
                        Text(
                            text = "No detailed release notes available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText.copy(alpha = 0.5f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(120.dp))
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = DarkBackground,
                    tonalElevation = 4.dp
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        when (state) {
                            is AppUpdateState.UpdateAvailable -> {
                                Button(
                                    onClick = { 
                                        if (state.apkDownloadUrl != null) {
                                            viewModel.startUpdateDownload(state.apkDownloadUrl)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandAccent,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Download Update",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                            is AppUpdateState.Downloading -> {
                                val infiniteTransition = rememberInfiniteTransition(label = "downloadShimmer")
                                val shimmerTranslate by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 1200f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "shimmerTranslate"
                                )
                                val shimmerBrush = Brush.linearGradient(
                                    colors = listOf(
                                        BrandAccent.copy(alpha = 0.6f),
                                        BrandAccent.copy(alpha = 0.95f),
                                        BrandAccent.copy(alpha = 0.6f)
                                    ),
                                    start = Offset(shimmerTranslate - 400f, 0f),
                                    end = Offset(shimmerTranslate, 60f)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(shimmerBrush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Downloading Update...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            is AppUpdateState.ReadyToInstall -> {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                Button(
                                    onClick = { viewModel.installUpdate(context, state.uri) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuccessGreen,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Install Now",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                            is AppUpdateState.Installing -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = BrandAccent,
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Installing Update...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = PrimaryText
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandAccent)
            }
        }
    }
}

