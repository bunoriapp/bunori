package com.halovoid.bunori.ui.feature.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel

@Composable
fun ManualCrawlForm(
    viewModel: BrowseViewModel,
    searchUrl: String?,
    libraryUrls: Set<String>,
    onNavigateToDetail: (String, String) -> Unit
) {
    var urlInput by remember { mutableStateOf(searchUrl ?: "") }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val onSubmit = {
        if (!isLoading && urlInput.isNotBlank()) {
            val crawlerName = viewModel.validateUrl(urlInput)
            if (crawlerName != null) {
                if (libraryUrls.contains(urlInput)) {
                    onNavigateToDetail(crawlerName, urlInput)
                } else {
                    val novelStub = Novel(
                        url = urlInput,
                        title = urlInput,
                        crawlerName = crawlerName,
                        inLibrary = false,
                        refreshExpiry = 0L
                    )
                    viewModel.saveNovelStub(novelStub) {
                        onNavigateToDetail(crawlerName, urlInput)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
    ) {
        item {
            Text(
                text = "Batch a Novel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Can't find what you're looking for? Submit the novel's URL and we'll add it to the indexing queue.",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter novel page URL", color = SecondaryText, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = BrandAccent,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = onSubmit,
                    enabled = !isLoading && urlInput.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BrandAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Submit",
                            tint = if (urlInput.isNotBlank()) PrimaryText else SecondaryText.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error ?: "",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Text(
                text = "How requesting works",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            StepItem(
                number = "01",
                title = "Submit a novel URL",
                description = "Enter the novel's page URL with supported source. You can continue on with adding the novel to your library"
            )
            Spacer(modifier = Modifier.height(20.dp))
            StepItem(
                number = "02",
                title = "We process the request",
                description = "Your request is queued and processed by our server. We take a note if the url can be indexed and if it is possible we index it as soon as possible"
            )
            Spacer(modifier = Modifier.height(20.dp))
            StepItem(
                number = "03",
                title = "It gets indexed",
                description = "Once indexed, it becomes available through Search. Now someone who someday needs to look for the same novel will have easy access to the novel."
            )
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// Alias for compatibility
@Composable
fun ManualRequestContent(
    viewModel: BrowseViewModel,
    searchUrl: String?,
    libraryUrls: Set<String>,
    onNavigateToDetail: (String, String) -> Unit
) = ManualCrawlForm(viewModel, searchUrl, libraryUrls, onNavigateToDetail)

@Composable
fun StepItem(number: String, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = BrandAccent.copy(alpha = 0.3f),
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                lineHeight = 18.sp
            )
        }
    }
}
