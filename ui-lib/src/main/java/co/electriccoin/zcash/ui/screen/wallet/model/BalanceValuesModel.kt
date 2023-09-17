package co.electriccoin.zcash.ui.screen.wallet.model

data class BalanceValuesModel(
    val balance: String = "",
    val balanceUnit: String = "",
    val fiatBalance: String = "",
    val fiatUnit: String = ""
)

data class BalanceUIModel(
    val balance: String = "",
    val balanceUnit: String = "",
    val fiatBalance: String = "",
    val fiatUnit: String = ""
)
