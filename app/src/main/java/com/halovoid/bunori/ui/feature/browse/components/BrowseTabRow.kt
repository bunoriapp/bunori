package com.halovoid.bunori.ui.feature.browse.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.BorderColor
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.browse.BrowseTab

@Composable
fun BrowseTabRow(
    selectedTab: BrowseTab,
    updatesCount: Int,
    onTabSelected: (BrowseTab) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = Color.Transparent,
        contentColor = BrandAccent,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                color = BrandAccent,
                height = 3.dp
            )
        },
        divider = {
            HorizontalDivider(color = BorderColor.copy(alpha = 0.4f), thickness = 1.dp)
        },
        modifier = modifier.fillMaxWidth()
    ) {
        BrowseTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                text = {
                    if (tab == BrowseTab.EXTENSIONS) {
                        BadgedBox(
                            badge = {
                                if (updatesCount > 0) {
                                    Badge(
                                        containerColor = BrandAccent,
                                        contentColor = Color.White
                                    ) {
                                        Text("$updatesCount", fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BrandAccent else SecondaryText
                            )
                        }
                    } else {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) BrandAccent else SecondaryText
                        )
                    }
                }
            )
        }
    }
}
