package co.electriccoin.zcash.ui.screen.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
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
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.about.nighthawk.AndroidAboutView
import co.electriccoin.zcash.ui.screen.advancesetting.AndroidAdvancedSetting
import co.electriccoin.zcash.ui.screen.changeserver.AndroidChangeServer
import co.electriccoin.zcash.ui.screen.externalservices.AndroidExternalServicesView
import co.electriccoin.zcash.ui.screen.fiatcurrency.AndroidFiatCurrency
import co.electriccoin.zcash.ui.screen.navigation.ArgumentKeys.IS_PIN_SETUP
import co.electriccoin.zcash.ui.screen.navigation.ArgumentKeys.TRANSACTION_DETAILS_ID
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.ABOUT
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.ADVANCED_SETTING
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.CHANGE_SERVER
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.EXTERNAL_SERVICES
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.FIAT_CURRENCY
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.PIN
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.RECEIVE_MONEY
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.RECEIVE_QR_CODES
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SCAN
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SECURITY
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SEND_MONEY
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SETTING_BACK_UP_WALLET
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SHIELD
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.SYNC_NOTIFICATION
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TOP_UP
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TRANSACTION_DETAILS
import co.electriccoin.zcash.ui.screen.navigation.NavigationTargets.TRANSACTION_HISTORY
import co.electriccoin.zcash.ui.screen.pin.AndroidPin
import co.electriccoin.zcash.ui.screen.receive.nighthawk.AndroidReceive
import co.electriccoin.zcash.ui.screen.receiveqrcodes.AndroidReceiveQrCodes
import co.electriccoin.zcash.ui.screen.scan.WrapScanValidator
import co.electriccoin.zcash.ui.screen.security.AndroidSecurity
import co.electriccoin.zcash.ui.screen.send.model.SendArgumentsWrapper
import co.electriccoin.zcash.ui.screen.send.nighthawk.AndroidSend
import co.electriccoin.zcash.ui.screen.settingbackupwallet.AndroidSettingBackUpWallet
import co.electriccoin.zcash.ui.screen.settings.nighthawk.AndroidSettings
import co.electriccoin.zcash.ui.screen.shield.AndroidShield
import co.electriccoin.zcash.ui.screen.syncnotification.AndroidSyncNotification
import co.electriccoin.zcash.ui.screen.topup.AndroidTopUp
import co.electriccoin.zcash.ui.screen.transactiondetails.AndroidTransactionDetails
import co.electriccoin.zcash.ui.screen.transactionhistory.AndroidTransactionHistory
import co.electriccoin.zcash.ui.screen.transfer.AndroidTransfer
import co.electriccoin.zcash.ui.screen.wallet.AndroidWallet
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun MainActivity.MainNavigation(navHostController: NavHostController, paddingValues: PaddingValues) {
    NavHost(navController = navHostController, startDestination = BottomNavItem.Wallet.route, modifier = Modifier.padding(paddingValues)) {
        composable(BottomNavItem.Wallet.route) { backStackEntry ->
            AndroidWallet(
                sendArgumentsWrapper = getScanSavedData(backStackEntry = backStackEntry),
                onAddressQrCodes = { navHostController.navigateJustOnce(RECEIVE_QR_CODES) },
                onShieldNow = { navHostController.navigateJustOnce(SHIELD) },
                onTransactionDetail = { navHostController.navigateJustOnce(NavigationTargets.navigationRouteTransactionDetails(transactionId = it)) },
                onViewTransactionHistory = { navHostController.navigateJustOnce(TRANSACTION_HISTORY) },
                onSendFromDeepLink = { navHostController.navigateJustOnce(SEND_MONEY) },
                onScanToSend = { navHostController.navigateJustOnce(SCAN) }
            )
            removeScanSavedData(backStackEntry = backStackEntry)
        }
        composable(BottomNavItem.Transfer.route) {
            AndroidTransfer(
                onSendMoney = { navHostController.navigateJustOnce(SEND_MONEY) },
                onReceiveMoney = { navHostController.navigateJustOnce(RECEIVE_MONEY) },
                onTopUp = { navHostController.navigateJustOnce(TOP_UP) }
            )
        }
        composable(BottomNavItem.Settings.route) {
            AndroidSettings(
                onSyncNotifications = { navHostController.navigateJustOnce(SYNC_NOTIFICATION) },
                onFiatCurrency = { navHostController.navigateJustOnce(FIAT_CURRENCY) },
                onSecurity = { navHostController.navigateJustOnce(SECURITY) },
                onBackupWallet = { navHostController.navigateJustOnce(SETTING_BACK_UP_WALLET) },
                onAdvancedSetting = { navHostController.navigateJustOnce(ADVANCED_SETTING) },
                onChangeServer = { navHostController.navigateJustOnce(CHANGE_SERVER) },
                onExternalServices = { navHostController.navigateJustOnce(EXTERNAL_SERVICES) },
                onAbout = { navHostController.navigateJustOnce(ABOUT) }
            )
        }
        composable(SEND_MONEY) { backStackEntry ->
            AndroidSend(
                onBack = { navHostController.popBackStackJustOnce(SEND_MONEY) },
                onTopUpWallet = {
                    navHostController.popBackStackJustOnce(SEND_MONEY)
                    navHostController.navigateJustOnce(TOP_UP)
                },
                navigateTo = { navHostController.popBackStack(it, false) },
               /* onMoreDetails = {
                    navHostController.popBackStack(BottomNavItem.Transfer.route, false)
                    navHostController.navigateJustOnce(NavigationTargets.navigationRouteTransactionDetails(transactionId = it))
                },*/
                onScan = { navHostController.navigateJustOnce(SCAN) },
                sendArgumentsWrapper = getScanSavedData(backStackEntry = backStackEntry)
            )
            removeScanSavedData(backStackEntry = backStackEntry)
        }
        composable(RECEIVE_MONEY) {
            AndroidReceive(
                onBack = { navHostController.popBackStackJustOnce(RECEIVE_MONEY) },
                onShowQrCode = { navHostController.navigateJustOnce(RECEIVE_QR_CODES) },
                onTopUpWallet = { navHostController.navigateJustOnce(TOP_UP) }
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
                    type = NavType.StringType
                    nullable = false
                }
            )
        ) {
            AndroidTransactionDetails(
                transactionId = it.arguments?.getString(TRANSACTION_DETAILS_ID, "") ?: "",
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
                    Twig.debug { "OnScanValid: $result" }
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
        composable(SHIELD) {
            AndroidShield(
                onBack = { navHostController.popBackStackJustOnce(SHIELD) }
            )
        }
        composable(
            route = PIN,
            arguments = listOf(
                navArgument(IS_PIN_SETUP) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            AndroidPin(
                isPinSetup = it.arguments?.getBoolean(IS_PIN_SETUP, false) ?: false,
                onBack = { navHostController.popBackStackJustOnce(PIN) }
            )
        }
        composable(SYNC_NOTIFICATION) {
            AndroidSyncNotification(
                onBack = { navHostController.popBackStackJustOnce(SYNC_NOTIFICATION) }
            )
        }
        composable(FIAT_CURRENCY) {
            AndroidFiatCurrency(
                onBack = { navHostController.popBackStackJustOnce(FIAT_CURRENCY) }
            )
        }
        composable(SECURITY) {
            AndroidSecurity(
                onBack = { navHostController.popBackStackJustOnce(SECURITY) },
                onSetPin = { navHostController.navigateJustOnce(NavigationTargets.navigateToPinScreen(isPinSetUp = true)) }
            )
        }
        composable(SETTING_BACK_UP_WALLET) {
            AndroidSettingBackUpWallet(
                onBack = { navHostController.popBackStackJustOnce(SETTING_BACK_UP_WALLET) }
            )
        }
        composable(ABOUT) {
            AndroidAboutView(
                onBack = { navHostController.popBackStackJustOnce(ABOUT) }
            )
        }
        composable(ADVANCED_SETTING) {
            AndroidAdvancedSetting(
                onBack = { navHostController.popBackStackJustOnce(ADVANCED_SETTING) }
            )
        }
        composable(EXTERNAL_SERVICES) {
            AndroidExternalServicesView(
                onBack = { navHostController.popBackStackJustOnce(EXTERNAL_SERVICES) }
            )
        }
        composable(CHANGE_SERVER) {
            AndroidChangeServer(
                onBack = { navHostController.popBackStackJustOnce(CHANGE_SERVER) }
            )
        }
    }
}

private fun getScanSavedData(backStackEntry: NavBackStackEntry) =
    SendArgumentsWrapper(
        recipientAddress = backStackEntry.savedStateHandle[NavigationArguments.SEND_RECIPIENT_ADDRESS],
        amount = backStackEntry.savedStateHandle[NavigationArguments.SEND_AMOUNT],
        memo = backStackEntry.savedStateHandle[NavigationArguments.SEND_MEMO]
    )

private fun removeScanSavedData(backStackEntry: NavBackStackEntry) {
    backStackEntry.savedStateHandle.remove<String>(NavigationArguments.SEND_RECIPIENT_ADDRESS)
    backStackEntry.savedStateHandle.remove<String>(NavigationArguments.SEND_AMOUNT)
    backStackEntry.savedStateHandle.remove<String>(NavigationArguments.SEND_MEMO)
}

@Composable
internal fun BottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNavBar = destinationsWithBottomBar.any { it.contentEquals(currentRoute, true) }
    if (showBottomNavBar) {
        NavigationBar(
            containerColor = ZcashTheme.colors.navigationContainer
        ) {
            navItemList.forEach { bottomNavItem ->
                NavigationBarItem(
                    selected = isBottomNavItemSelected(bottomNavItem.route, currentRoute),
                    onClick = {
                        navController.navigate(bottomNavItem.route) {
                            navController.graph.startDestinationRoute?.let { screenRoute ->
                                popUpTo(screenRoute) {
                                    saveState = true
                                }
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painter = painterResource(id = bottomNavItem.icon), contentDescription = bottomNavItem.route) },
                    enabled = true,
                    label = { BodySmall(text = stringResource(id = bottomNavItem.title)) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ZcashTheme.colors.navigationIcon,
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

private val navItemList = persistentListOf(BottomNavItem.Wallet, BottomNavItem.Transfer, BottomNavItem.Settings)

private val destinationsWithBottomBar = persistentListOf(
    BottomNavItem.Wallet.route,
    BottomNavItem.Transfer.route,
    BottomNavItem.Settings.route,
    RECEIVE_MONEY,
    TOP_UP,
    RECEIVE_QR_CODES,
    )

object NavigationTargets {
    const val SEND_MONEY = "send_money"
    const val RECEIVE_MONEY = "receive_money"
    const val TOP_UP = "top_up"
    const val SCAN = "scan"
    const val RECEIVE_QR_CODES = "receive_qr_codes"
    const val SHIELD = "shield"
    const val SYNC_NOTIFICATION = "sync_notification"
    const val FIAT_CURRENCY = "fiat_currency"
    const val SECURITY = "security"
    const val SETTING_BACK_UP_WALLET = "setting_back_up_wallet"
    const val ADVANCED_SETTING = "advanced_settings"
    const val CHANGE_SERVER = "change_server"
    const val EXTERNAL_SERVICES = "external_services"
    const val ABOUT = "about"
    const val TRANSACTION_HISTORY = "transaction_history"
    const val TRANSACTION_DETAILS = "transaction_details/{$TRANSACTION_DETAILS_ID}"
    const val PIN = "pin?$IS_PIN_SETUP={$IS_PIN_SETUP}"
    fun navigationRouteTransactionDetails(transactionId: String): String {
        return TRANSACTION_DETAILS.replace("{$TRANSACTION_DETAILS_ID}", transactionId)
    }
    fun navigateToPinScreen(isPinSetUp: Boolean = false): String {
        return PIN.replace("{$IS_PIN_SETUP}", "$isPinSetUp")
    }
}

object ArgumentKeys {
    const val TRANSACTION_DETAILS_ID = "transactionId"
    const val IS_PIN_SETUP = "is_pin_setup"
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
