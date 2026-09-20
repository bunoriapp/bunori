package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun ActivityActiveCarousel(
    activeBatches: List<Batch>,
    onRequestClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeBatches.isEmpty()) return

    if (activeBatches.size == 1) {
        val batch = activeBatches.first()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            JobCard(
                batch = batch,
                onClick = { onRequestClick(batch.id) },
                allowAction = false
            )
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { activeBatches.size })
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val batch = activeBatches[page]
                JobCard(
                    batch = batch,
                    onClick = { onRequestClick(batch.id) },
                    allowAction = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(activeBatches.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) BrandAccent else SecondaryText.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}
