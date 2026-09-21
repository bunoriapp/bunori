package com.halovoid.bunori.ui.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.halovoid.bunori.ui.core.components.NovelCoverImage
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun NovelCard(
    novel: Novel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NovelCoverImage(
                coverUrl = novel.coverUrl,
                coverHttpsUrl = novel.coverHttpsUrl,
                title = novel.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            )

            val unreadCount = remember(novel.chapters) {
                novel.chapters.count { !it.read }
            }
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
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
                    text = novel.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                if (novel.chapters.isNotEmpty()) {
                    Text(
                        text = "${novel.chapters.size} Chapters",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
