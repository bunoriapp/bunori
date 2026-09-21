package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.halovoid.bunori.ui.core.components.NovelCoverImage
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun NovelHeroSection(novel: Novel) {
    var coverModel by remember(novel.coverUrl, novel.coverHttpsUrl) {
        mutableStateOf(novel.coverUrl ?: novel.coverHttpsUrl)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(DarkBackground)
    ) {
        if (!coverModel.isNullOrBlank()) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.12f),
                contentScale = ContentScale.Crop,
                onError = {
                    if (coverModel != novel.coverHttpsUrl) {
                        coverModel = novel.coverHttpsUrl
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DarkBackground.copy(alpha = 0.5f),
                            DarkBackground
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            NovelCoverImage(
                coverUrl = novel.coverUrl,
                coverHttpsUrl = novel.coverHttpsUrl,
                title = novel.title,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = SecondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = novel.author ?: "Unknown Author",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Source,
                        contentDescription = null,
                        tint = SecondaryText,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = novel.crawlerName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
