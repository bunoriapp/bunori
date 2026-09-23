package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.NovelCard
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarNovelsBottomSheet(
    similarNovels: List<Novel>,
    onAddAnyway: () -> Unit,
    onNovelClick: (Novel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Possible Duplicate",
        subtitle = "Similar novels already in your library",
        showCloseButton = true,
        modifier = modifier
    ) {
        Text(
            text = if (similarNovels.size == 1) {
                "A novel with a very similar title is already saved in your library:"
            } else {
                "${similarNovels.size} novels with similar titles are already saved in your library:"
            },
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(similarNovels, key = { it.url }) { novel ->
                Box(modifier = Modifier.width(125.dp)) {
                    NovelCard(
                        novel = novel,
                        isCompactMode = false,
                        showBookmark = false,
                        onClick = {
                            onDismiss()
                            onNovelClick(novel)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PrimaryText
                )
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = {
                    onAddAnyway()
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandAccent,
                    contentColor = PrimaryText
                )
            ) {
                Text(
                    text = "Add Anyway",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
