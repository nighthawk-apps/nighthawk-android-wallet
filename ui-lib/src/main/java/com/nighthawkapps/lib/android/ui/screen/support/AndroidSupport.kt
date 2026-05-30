@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.support

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.support.model.SupportInfo
import com.nighthawkapps.lib.android.ui.screen.support.model.SupportInfoType
import com.nighthawkapps.lib.android.ui.screen.support.util.EmailUtil
import com.nighthawkapps.lib.android.ui.screen.support.view.Support
import com.nighthawkapps.lib.android.ui.screen.support.viewmodel.SupportViewModel
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.WrapSupport(goBack: () -> Unit) {
    WrapSupport(this, goBack)
}

@Composable
internal fun WrapSupport(
    activity: ComponentActivity,
    goBack: () -> Unit
) {
    val viewModel by activity.viewModels<SupportViewModel>()
    val supportMessage = viewModel.supportInfo.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Support(
        snackbarHostState,
        onBack = goBack,
        onSend = { userMessage ->
            val fullMessage = formatMessage(userMessage, supportMessage)

            val mailIntent =
                EmailUtil
                    .newMailActivityIntent(
                        activity.getString(R.string.support_email_address),
                        activity.getString(R.string.app_name),
                        fullMessage
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }

            if (!mailIntent.canBeHandled(activity.packageManager)) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = activity.getString(R.string.support_unable_to_open_email)
                    )
                }
                return@Support
            }

            runCatching {
                activity.startActivity(mailIntent)
            }.onSuccess {
                goBack()
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = activity.getString(R.string.support_unable_to_open_email)
                    )
                }
            }
        }
    )
}

@SuppressLint("QueryPermissionsNeeded")
private fun Intent.canBeHandled(pm: PackageManager): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.resolveActivity(this, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) != null
    } else {
        @Suppress("DEPRECATION")
        pm.resolveActivity(this, PackageManager.MATCH_DEFAULT_ONLY) != null
    }

// Note that we don't need to localize this format string
private fun formatMessage(
    messageBody: String,
    appInfo: SupportInfo?,
    supportInfoValues: Set<SupportInfoType> = SupportInfoType.values().toSet()
): String = "$messageBody\n\n${appInfo?.toSupportString(supportInfoValues) ?: ""}"
