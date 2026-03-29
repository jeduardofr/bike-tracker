package com.biketracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.biketracker.ui.navigation.AppNavGraph
import com.biketracker.ui.navigation.Routes
import com.biketracker.ui.theme.BikeTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BikeTrackerTheme {
                BikeTrackerApp()
            }
        }
    }
}

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun BikeTrackerApp() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem(Routes.HOME, "Home", Icons.Default.Home),
        BottomNavItem(Routes.HISTORY, "History", Icons.Default.DateRange),
        BottomNavItem(Routes.WEEKLY, "Weekly", Icons.Default.DateRange),
        BottomNavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings)
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        AppNavGraph(navController = navController, modifier = Modifier.padding(padding))
    }
}
