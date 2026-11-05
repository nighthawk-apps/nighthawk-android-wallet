package com.nighthawkapps.lib.android.ui.screen.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.design.theme.LocalCyberpunkChrome
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

/**
 * Folded width keeps the bottom bar. Width at or above [NighthawkExpandedWidth]
 * uses a navigation rail. material3-adaptive and androidx.window are not in the
 * version catalog, so this measures the window with [BoxWithConstraints].
 */
internal val NighthawkExpandedWidth = 600.dp

@Composable
internal fun NighthawkAdaptiveShell(
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTabChrome = destinationsWithBottomBar.any { it.contentEquals(currentRoute, true) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= NighthawkExpandedWidth && showTabChrome
        val showBottom = showTabChrome && !useRail
        val cyberpunk = LocalCyberpunkChrome.current
        Scaffold(
            // Transparent so the wallpaper continues under the status bar
            // and the system navigation bar. Content still respects insets.
            containerColor = if (cyberpunk) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                if (showBottom) {
                    BottomNavigation(navController = navController)
                }
            },
        ) { padding ->
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(if (useRail) NavigationRailWidth else 0.dp),
                ) {
                    if (useRail) {
                        NighthawkNavigationRail(navController = navController)
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    content(PaddingValues())
                }
            }
        }
    }
}

private val NavigationRailWidth = 80.dp

@Composable
private fun NighthawkNavigationRail(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val cyberpunk = LocalCyberpunkChrome.current
    NavigationRail(
        containerColor =
            if (cyberpunk) {
                Color(0x6610141C)
            } else {
                WalletTheme.colors.navigationContainer
            },
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        navItemList.forEach { item ->
            NavigationRailItem(
                selected = isBottomNavItemSelected(item.route, currentRoute),
                onClick = { navController.navigateBottomDestination(item.route) },
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.route,
                    )
                },
                label = { BodySmall(text = stringResource(id = item.title)) },
                alwaysShowLabel = true,
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        unselectedIconColor = WalletTheme.colors.navigationIcon,
                        unselectedTextColor = WalletTheme.colors.navigationIcon,
                    ),
            )
        }
    }
}

internal fun NavController.navigateBottomDestination(route: String) {
    navigate(route) {
        graph.startDestinationRoute?.let { screenRoute ->
            popUpTo(screenRoute) {
                saveState = true
            }
        }
        launchSingleTop = true
        restoreState = true
    }
}
