package com.nighthawkapps.lib.android.ui.common

import kotlin.time.Duration.Companion.seconds

// Recommended timeout for Android configuration changes to keep Kotlin Flow from restarting
val ANDROID_STATE_FLOW_TIMEOUT = 5.seconds

const val AMOUNT_QUERY = "amount"
const val MEMO_QUERY = "memo"

const val WALLET_PASSWORD_LENGTH = 6
val SUCCESS_VIBRATION_PATTERN = arrayOf(0L, 200L, 100L, 100L, 800L).toLongArray()
val WRONG_VIBRATION_PATTERN = arrayOf(0L, 50L, 100L, 50L, 100L).toLongArray()
const val WORKER_TAG_SYNC_NOTIFICATION = "constants.tag_sync_notification"

const val PIN_CODE = "const.pin.code"
const val IS_BIO_METRIC_OR_FACE_ID_ENABLED = "const.pin.is_biometric_or_face_id"

const val MAXIMUM_FRACTION_DIGIT = 8
