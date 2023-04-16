package co.electriccoin.zcash.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import co.electriccoin.zcash.ui.screen.navigation.BottomNavigation
import co.electriccoin.zcash.ui.screen.navigation.MainNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainActivity.NavigationMainContent() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            BottomNavigation(navController = navController)
        }
    ) {
        MainNavigation(navHostController = navController)
        println(it)
    }
}