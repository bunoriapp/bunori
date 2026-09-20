package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun RequestNovelFooter(
    onRequestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Did not find your novel? Try ", color = SecondaryText, fontSize = 14.sp)
        TextButton(
            onClick = onRequestClick,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Requesting", color = BrandAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
