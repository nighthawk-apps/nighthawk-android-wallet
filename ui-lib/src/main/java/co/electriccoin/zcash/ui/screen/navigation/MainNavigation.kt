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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.NavigationArguments
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.screen.navigation.ArgumentKeys.TRANSACTION_DETAILS_ID
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.RECEIVE_MONEY
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.RECEIVE_QR_CODES
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SCAN
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SEND_MONEY
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TOP_UP
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TRANSACTION_DETAILS
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TRANSACTION_HISTORY
import co.electriccoin.zcash.ui.screen.receive.nighthawk.AndroidReceive
import co.electriccoin.zcash.ui.screen.receiveqrcodes.AndroidReceiveQrCodes
import co.electriccoin.zcash.ui.screen.scan.WrapScanValidator
import co.electriccoin.zcash.ui.screen.send.nighthawk.AndroidSend
import co.electriccoin.zcash.ui.screen.settings.nighthawk.AndroidSettings
import co.electriccoin.zcash.ui.screen.topup.AndroidTopUp
import co.electriccoin.zcash.ui.screen.transactiondetails.AndroidTransactionDetails
import co.electriccoin.zcash.ui.screen.transactionhistory.AndroidTransactionHistory
import co.electriccoin.zcash.ui.screen.transfer.AndroidTransfer
import co.electriccoin.zcash.ui.screen.wallet.AndroidWallet

@Composable
internal fun MainActivity.MainNavigation(navHostController: NavHostController, paddingValues: PaddingValues) {
    NavHost(navController = navHostController, startDestination = BottomNavItem.Wallet.route, modifier = Modifier.padding(paddingValues)) {
        composable(BottomNavItem.Wallet.route) {
            AndroidWallet(
                onAddressQrCodes = { navHostController.navigateJustOnce(RECEIVE_QR_CODES) },
                onTransactionDetail = { navHostController.navigateJustOnce(NavigationTargets.navigationRouteTransactionDetails(transactionId = it)) },
                onViewTransactionHistory = { navHostController.navigateJustOnce(TRANSACTION_HISTORY) }
            )
        }
        composable(BottomNavItem.Transfer.route) {
            AndroidTransfer(
                onSendMoney = { navHostController.navigateJustOnce(SEND_MONEY) },
                onReceiveMoney = { navHostController.navigateJustOnce(RECEIVE_MONEY) },
                onTopUp = { navHostController.navigateJustOnce(TOP_UP) }
            )
        }
        composable(BottomNavItem.Settings.route) {
            AndroidSettings()
        }
        composable(SEND_MONEY) {
            AndroidSend(
                onBack = { navHostController.popBackStackJustOnce(SEND_MONEY) },
                navigateTo = { navHostController.popBackStack(it, false) },
                onMoreDetails = {
                    navHostController.popBackStack(BottomNavItem.Transfer.route, false)
                    navHostController.navigateJustOnce(NavigationTargets.navigationRouteTransactionDetails(transactionId = it))
                },
                onScan = { navHostController.navigateJustOnce(SCAN) }
            )
        }
        composable(RECEIVE_MONEY) {
            AndroidReceive(
                onBack = { navHostController.popBackStackJustOnce(RECEIVE_MONEY) }
            )
        }
        composable(TOP_UP) {
            AndroidTopUp(
                onBack = { navHostController.popBackStackJustOnce(TOP_UP) }
            )
        }
        composable(
            route = TRANSACTION_DETAILS,
            arguments = listOf(
                navArgument(TRANSACTION_DETAILS_ID) {
                    type = NavType.LongType
                    nullable = false
                }
            )
        ) {
            AndroidTransactionDetails(
                transactionId = it.arguments?.getLong(TRANSACTION_DETAILS_ID, -1) ?: -1,
                onBack = { navHostController.popBackStack() }
            )
        }
        composable(RECEIVE_QR_CODES) {
            AndroidReceiveQrCodes(
                onBack = { navHostController.popBackStackJustOnce(RECEIVE_QR_CODES) },
                onSeeMoreTopUpOption = { navHostController.navigateJustOnce(TOP_UP) }
            )
        }
        composable(SCAN) {
            WrapScanValidator(
                onScanValid = { result ->
                    Twig.info { "OnScanValid: $result" }
                    // At this point we only pass recipient address
                    navHostController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(NavigationArguments.SEND_RECIPIENT_ADDRESS, result)
                        set(NavigationArguments.SEND_AMOUNT, null)
                        set(NavigationArguments.SEND_MEMO, null)
                    }
                    navHostController.popBackStackJustOnce(SCAN)
                },
                goBack = { navHostController.popBackStackJustOnce(SCAN) }
            )
        }
        composable(TRANSACTION_HISTORY) {
            AndroidTransactionHistory(
                onBack = { navHostController.popBackStackJustOnce(TRANSACTION_HISTORY) },
                onTransactionDetail = { navHostController.navigateJustOnce(NavigationTargets.navigationRouteTransactionDetails(transactionId = it)) }
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
    return when (bottomNavItemRoute) {
        BottomNavItem.Wallet.route -> {
            currentRoute == bottomNavItemRoute || RECEIVE_QR_CODES == currentRoute
        }

        BottomNavItem.Transfer.route -> {
            currentRoute == bottomNavItemRoute || RECEIVE_MONEY == currentRoute || TOP_UP == currentRoute
        }

        else -> {
            currentRoute == bottomNavItemRoute
        }
    }
}

object NavigationTargets {
    const val SEND_MONEY = "send_money"
    const val RECEIVE_MONEY = "receive_money"
    const val TOP_UP = "top_up"
    const val SCAN = "scan"
    const val RECEIVE_QR_CODES = "receive_qr_codes"
    const val TRANSACTION_HISTORY = "transaction_history"
    const val TRANSACTION_DETAILS = "transaction_details/{$TRANSACTION_DETAILS_ID}"
    fun navigationRouteTransactionDetails(transactionId: Long): String {
        return TRANSACTION_DETAILS.replace("{$TRANSACTION_DETAILS_ID}", "$transactionId")
    }
}

object ArgumentKeys {
    const val TRANSACTION_DETAILS_ID = "transactionId"
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
