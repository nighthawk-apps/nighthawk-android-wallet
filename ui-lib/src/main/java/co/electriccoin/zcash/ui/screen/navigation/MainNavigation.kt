package co.electriccoin.zcash.ui.screen.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.RECEIVE
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TOP_UP
import co.electriccoin.zcash.ui.screen.receive.nighthawk.AndroidReceive
import co.electriccoin.zcash.ui.screen.settings.nighthawk.AndroidSettings
import co.electriccoin.zcash.ui.screen.topup.AndroidTopUp
import co.electriccoin.zcash.ui.screen.transfer.AndroidTransfer
import co.electriccoin.zcash.ui.screen.wallet.AndroidWallet

@Composable
internal fun MainActivity.MainNavigation(navHostController: NavHostController, paddingValues: PaddingValues) {
    NavHost(navController = navHostController, startDestination = BottomNavItem.Wallet.route, modifier = Modifier.padding(paddingValues)) {
        composable(BottomNavItem.Wallet.route) {
            AndroidWallet()
        }
        composable(BottomNavItem.Transfer.route) {
            AndroidTransfer(
                onReceive = { navHostController.navigateJustOnce(RECEIVE) },
                onTopUp = { navHostController.navigateJustOnce(TOP_UP) }
            )
        }
        composable(BottomNavItem.Settings.route) {
            AndroidSettings()
        }
        composable(RECEIVE) {
            AndroidReceive(
                onBack = { navHostController.popBackStackJustOnce(RECEIVE) }
            )
        }
        composable(TOP_UP) {
            AndroidTopUp(
                onBack = { navHostController.popBackStackJustOnce(TOP_UP) }
            )
        }
    }
}

@Composable
internal fun BottomNavigation(navController: NavController, showBottomNavBar: Boolean = true, enableTransferTab: Boolean = false) {
    val navItemList = listOf(BottomNavItem.Wallet, BottomNavItem.Transfer, BottomNavItem.Settings)
    AnimatedVisibility(
        visible = showBottomNavBar,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        NavigationBar(
            containerColor = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_navy)
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            navItemList.forEach { bottomNavItem ->
                NavigationBarItem(
                    selected = isBottomNavItemSelected(bottomNavItem.route, currentRoute),
                    onClick = {
                        navController.navigate(bottomNavItem.route) {
                            navController.graph.startDestinationRoute?.let { screen_route ->
                                popUpTo(screen_route) {
                                    saveState = true
                                }
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painter = painterResource(id = bottomNavItem.icon), contentDescription = bottomNavItem.route) },
                    enabled = if (BottomNavItem.Transfer.route == bottomNavItem.route) enableTransferTab else true,
                    label = { BodySmall(text = stringResource(id = bottomNavItem.title)) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.White,
                        unselectedTextColor = Color.White
                    )
                )
            }
        }
    }
}

sealed class BottomNavItem(val route: String, @StringRes val title: Int, @DrawableRes val icon: Int) {
    object Wallet : BottomNavItem("Wallet", R.string.ns_wallet, R.drawable.ic_icon_wallet)
    object Transfer : BottomNavItem("Transfer", R.string.ns_transfer, R.drawable.ic_icon_transfer)
    object Settings : BottomNavItem("Settings", R.string.ns_settings, R.drawable.ic_icon_settings)
}

fun isBottomNavItemSelected(bottomNavItemRoute: String, currentRoute: String?): Boolean {
    return if (bottomNavItemRoute == BottomNavItem.Transfer.route) {
        bottomNavItemRoute == currentRoute || RECEIVE == currentRoute|| TOP_UP == currentRoute
    } else {
        bottomNavItemRoute == currentRoute
    }
}

object NavigationTargets {
    const val RECEIVE = "receive"
    const val TOP_UP = "top_up"
}

private fun NavHostController.navigateJustOnce(
    route: String,
    navOptionsBuilder: (NavOptionsBuilder.() -> Unit)? = null
) {
    if (currentDestination?.route == route) {
        return
    }

    if (navOptionsBuilder != null) {
        navigate(route, navOptionsBuilder)
    } else {
        navigate(route)
    }
}

/**
 * Pops up the current screen from the back stack. Parameter currentRouteToBePopped is meant to be
 * set only to the current screen so we can easily debounce multiple screen popping from the back stack.
 *
 * @param currentRouteToBePopped current screen which should be popped up.
 */
private fun NavHostController.popBackStackJustOnce(currentRouteToBePopped: String) {
    if (currentDestination?.route != currentRouteToBePopped) {
        return
    }
    popBackStack()
}
