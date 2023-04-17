package co.electriccoin.zcash.ui.screen.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.screen.settings.nighthawk.AndroidSettings
import co.electriccoin.zcash.ui.screen.transfer.AndroidTransfer
import co.electriccoin.zcash.ui.screen.wallet.AndroidWallet

@Composable
internal fun MainActivity.MainNavigation(navHostController: NavHostController) {
    NavHost(navController = navHostController, startDestination = BottomNavItem.Wallet.route) {
        composable(BottomNavItem.Wallet.route) {
            AndroidWallet()
        }
        composable(BottomNavItem.Transfer.route) {
            AndroidTransfer()
        }
        composable(BottomNavItem.Settings.route) {
            AndroidSettings()
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
                    selected = bottomNavItem.route == currentRoute,
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
                    icon = { Icon(painter = painterResource(id = bottomNavItem.icon), contentDescription = bottomNavItem.route)},
                    enabled = if (BottomNavItem.Transfer.route == bottomNavItem.route) enableTransferTab else true,
                    label = { BodySmall(text = stringResource(id = bottomNavItem.title))},
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
