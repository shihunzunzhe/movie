package com.earthvideo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthvideo.app.ui.theme.*

data class BottomTab(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

val bottomTabs = listOf(
    BottomTab("home", "首页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab("rank", "排行", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    BottomTab("discover", "找片", Icons.Filled.TravelExplore, Icons.Outlined.TravelExplore),
    BottomTab("profile", "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun BottomNavBar(currentRoute: String, onTabSelected: (String) -> Unit) {
    Surface(
        color = CardBg,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomNavHeight)
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                val tint = if (isSelected) Primary else TextHint
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab.route) }
                ) {
                    // Pill background for selected tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PrimarySoft else CardBg)
                            .padding(horizontal = 18.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.iconSelected else tab.iconUnselected,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        color = tint,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                            else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
            }
        }
    }
}
