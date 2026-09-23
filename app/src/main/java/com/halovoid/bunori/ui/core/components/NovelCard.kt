package com.halovoid.bunori.ui.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun NovelCard(
    novel: Novel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompactMode: Boolean = false,
    isInLibrary: Boolean? = null
) {
    val inLibrary = isInLibrary ?: novel.inLibrary
    val unreadCount = remember(novel.chapters) {
        novel.chapters.count { !it.read }
    }
    NovelCard(
        title = novel.title,
        coverUrl = novel.coverUrl,
        coverHttpsUrl = novel.coverHttpsUrl,
        crawlerName = novel.crawlerName.takeIf { it.isNotBlank() },
        subtitle = if (novel.chapters.isNotEmpty()) "${novel.chapters.size} Chapters" else null,
        unreadCount = unreadCount,
        isInLibrary = inLibrary,
        isCompactMode = isCompactMode,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun NovelCard(
    item: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompactMode: Boolean = false,
    isInLibrary: Boolean = false
) {
    NovelCard(
        title = item.title,
        coverUrl = item.imageUrl,
        coverHttpsUrl = item.imageUrl,
        crawlerName = item.source.takeIf { it.isNotBlank() },
        subtitle = null,
        unreadCount = 0,
        isInLibrary = isInLibrary,
        isCompactMode = isCompactMode,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun NovelCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverHttpsUrl: String? = null,
    crawlerName: String? = null,
    subtitle: String? = null,
    unreadCount: Int = 0,
    isInLibrary: Boolean = false,
    isCompactMode: Boolean = false
) {
    if (isCompactMode) {
        CompactNovelCardLayout(
            title = title,
            coverUrl = coverUrl,
            coverHttpsUrl = coverHttpsUrl,
            crawlerName = crawlerName,
            subtitle = subtitle,
            unreadCount = unreadCount,
            isInLibrary = isInLibrary,
            onClick = onClick,
            modifier = modifier
        )
    } else {
        ExpandedNovelCardLayout(
            title = title,
            coverUrl = coverUrl,
            coverHttpsUrl = coverHttpsUrl,
            subtitle = subtitle,
            unreadCount = unreadCount,
            isInLibrary = isInLibrary,
            onClick = onClick,
            modifier = modifier
        )
    }
}

@Composable
private fun ExpandedNovelCardLayout(
    title: String,
    coverUrl: String?,
    coverHttpsUrl: String?,
    subtitle: String?,
    unreadCount: Int,
    isInLibrary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.35f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NovelCoverImage(
                coverUrl = coverUrl,
                coverHttpsUrl = coverHttpsUrl,
                title = title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            )

            if (unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = "$unreadCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isInLibrary) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Surface(
                        color = BrandAccent,
                        shape = CircleShape,
                        modifier = Modifier.size(22.dp),
                        shadowElevation = 4.dp
                    ) {
                        Icon(
                            Icons.Default.CollectionsBookmark,
                            contentDescription = "In Library",
                            tint = Color.White,
                            modifier = Modifier.padding(3.5.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 220f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactNovelCardLayout(
    title: String,
    coverUrl: String?,
    coverHttpsUrl: String?,
    crawlerName: String?,
    subtitle: String?,
    unreadCount: Int,
    isInLibrary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceVariant)
            ) {
                NovelCoverImage(
                    coverUrl = coverUrl,
                    coverHttpsUrl = coverHttpsUrl,
                    title = title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(6.dp),
                    showTitleInFallback = false
                )

                if (unreadCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = "$unreadCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isInLibrary) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            color = BrandAccent,
                            shape = CircleShape,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                Icons.Default.CollectionsBookmark,
                                contentDescription = "In Library",
                                tint = Color.White,
                                modifier = Modifier.padding(2.5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!crawlerName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BrandAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = crawlerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SecondaryText.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
