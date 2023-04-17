package co.electriccoin.zcash.ui

import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.navigation.BottomNavigation
import co.electriccoin.zcash.ui.screen.navigation.MainNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainActivity.NavigationMainContent() {
    val navController = rememberNavController()
    val homeViewModel by viewModels<HomeViewModel>()
    val showBottomNavBar = homeViewModel.isBottomNavBarVisible.collectAsStateWithLifecycle()
    val enableTransferTab = homeViewModel.isTransferStateEnabled.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            BottomNavigation(navController = navController, showBottomNavBar = showBottomNavBar.value, enableTransferTab = enableTransferTab.value)
        }
    ) {
        MainNavigation(navHostController = navController)
        println(it)
    }
}