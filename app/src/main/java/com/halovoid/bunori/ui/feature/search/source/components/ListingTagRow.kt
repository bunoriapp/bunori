package com.halovoid.bunori.ui.feature.search.source.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.extension.api.models.ListingDto
import com.halovoid.bunori.ui.core.theme.BorderColor
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun ListingTagRow(
    listings: List<ListingDto>,
    selectedListingId: String,
    onSelectListing: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (listings.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listings.forEach { listing ->
            val isSelected = listing.id == selectedListingId
            val isSearchChip = listing.id == "__search__"

            Surface(
                onClick = { onSelectListing(listing.id) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) BrandAccent.copy(alpha = 0.18f) else DarkSurface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) BrandAccent.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.35f)
                ),
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSearchChip) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isSelected) BrandAccent else SecondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = listing.name,
                        color = if (isSelected) BrandAccent else SecondaryText,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
