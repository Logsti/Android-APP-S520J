package com.s520j.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

data class TabSpec(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

val MainTabs = listOf(
    TabSpec("heart", "心动", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    TabSpec("gallery", "拾光", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    TabSpec("tacit", "默契", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    TabSpec("us", "我们", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun S520JBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    Surface(
        color = S520JColors.Surface.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.background(S520JColors.Surface.copy(alpha = 0.94f))) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(S520JColors.Divider)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.BottomBarHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(tab.route) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) S520JColors.Primary else S520JColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tab.label,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = if (selected) S520JColors.Primary else S520JColors.TextSecondary,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(if (selected) 18.dp else 0.dp)
                                .height(2.dp)
                                .background(
                                    if (selected) S520JColors.Primary else S520JColors.Surface,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}
