package io.github.waltoncomonline.samsungmimh02.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.waltoncomonline.samsungmimh02.ui.theme.AppSurface
import io.github.waltoncomonline.samsungmimh02.ui.theme.PrimaryGreen
import io.github.waltoncomonline.samsungmimh02.ui.theme.SecondaryText

private enum class AppTab { Control, Schedules }

@Composable
fun SamsungMimH02App(
    pairingViewModel: PairingViewModel,
    scheduleViewModel: ScheduleViewModel,
    onRevealToken: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(AppTab.Control) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = AppSurface) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Control,
                    onClick = { selectedTab = AppTab.Control },
                    icon = { Icon(Icons.Default.AcUnit, contentDescription = null) },
                    label = { Text("Control") },
                    colors = navigationColors(),
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Schedules,
                    onClick = { selectedTab = AppTab.Schedules },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label = { Text("Schedules") },
                    colors = navigationColors(),
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                AppTab.Control -> PairingScreen(pairingViewModel, onRevealToken)
                AppTab.Schedules -> ScheduleScreen(scheduleViewModel)
            }
        }
    }
}

@Composable
private fun navigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = PrimaryGreen,
    selectedTextColor = PrimaryGreen,
    indicatorColor = PrimaryGreen.copy(alpha = 0.14f),
    unselectedIconColor = SecondaryText,
    unselectedTextColor = SecondaryText,
)
