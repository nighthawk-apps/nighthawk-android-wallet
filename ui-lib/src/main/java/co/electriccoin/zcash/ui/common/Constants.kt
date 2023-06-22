package co.electriccoin.zcash.ui.common

import kotlin.time.Duration.Companion.seconds

// Recommended timeout for Android configuration changes to keep Kotlin Flow from restarting
val ANDROID_STATE_FLOW_TIMEOUT = 5.seconds
const val SIDE_SHIFT_AFFILIATE_LINK = "https://sideshift.ai/"
const val STEALTH_HEALTH_AFFILIATE_LINK = "https://stealthex.io/"
const val ZCASH_LEARN_MORE_LINK = "https://z.cash/the-basics/"
const val ZEC_MAX_AMOUNT = 21000000
const val AMOUNT_QUERY = "amount"
const val MEMO_QUERY = "memo"
const val LEARN_UNIFIED_ADDRESSES = "https://electriccoin.co/blog/unified-addresses-in-zcash-explained/"
const val WALLET_PASSWORD_LENGTH = 6
val SUCCESS_VIBRATION_PATTERN = arrayOf(0L, 200L, 100L, 100L, 800L).toLongArray()
val WRONG_VIBRATION_PATTERN = arrayOf(0L, 50L, 100L, 50L, 100L).toLongArray()
const val KEY_SYNC_NOTIFICATION = "constants.key_sync_notification"
const val WORKER_TAG_SYNC_NOTIFICATION = "constants.tag_sync_notification"
const val VIEW_SOURCE_URL = "https://github.com/nighthawk-apps/nighthawk-wallet-android"
const val PRIVACY_POLICY_LINK = "https://nighthawkwallet.com/privacypolicy/"
const val BLOCK_EXPLORER_URL_TESTNET = "https://explorer.testnet.z.cash/tx/" // append transaction id in the end
const val BLOCK_EXPLORER_URL_MAIN_NET = "https://zcashblockexplorer.com/transactions/" // append transaction id in the end