package co.electriccoin.zcash.ui.common

enum class ShortcutAction(val action: String) {
    SEND_MONEY_SCAN_QR_CODE("scan_qr_to_send"),
    RECEIVE_MONEY_QR_CODE("show_qr_code_to_receive");

    companion object {
        const val KEY_SHORT_CUT_CLICK = "shortcut_click"
        fun getShortcutAction(action: String?) = ShortcutAction.values().find { it.action == action }
    }
}
