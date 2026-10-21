package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ApkSummary(
    val apk: ApkShare.InstalledApk,
    val abis: Set<String>,
)

@Composable
fun ShareAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<ApkSummary?>(null) }
    var preparing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        summary =
            withContext(Dispatchers.IO) {
                val apk = ApkShare.installedApk(context)
                ApkSummary(apk, runCatching { ApkShare.nativeAbis(apk.base) }.getOrDefault(emptySet()))
            }
    }

    fun launch(build: suspend () -> Intent?) {
        if (preparing) return
        preparing = true
        scope.launch {
            val intent = runCatching { build() }.onFailure { Twig.warn(it) { "APK share failed" } }.getOrNull()
            preparing = false
            if (intent == null) {
                toast(context, R.string.ns_share_app_failed)
                return@launch
            }
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            } catch (e: ActivityNotFoundException) {
                Twig.warn(e) { "No activity for APK share" }
                toast(context, R.string.ns_share_app_failed)
            }
        }
    }

    val chooserTitle = stringResource(id = R.string.ns_share_app_chooser_title)

    AlertDialog(
        onDismissRequest = { if (!preparing) onDismiss() },
        title = { Body(text = stringResource(id = R.string.ns_share_app_title), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                BodyMedium(text = stringResource(id = R.string.ns_share_app_body), color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                val current = summary
                if (current == null) {
                    CircularProgressIndicator()
                } else {
                    BodyMedium(
                        text =
                            stringResource(
                                id = R.string.ns_share_app_details,
                                current.apk.versionName,
                                Formatter.formatShortFileSize(context, current.apk.totalBytes),
                                current.abis.ifEmpty { setOf("any") }.joinToString(", "),
                            ),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BodyMedium(text = stringResource(id = R.string.ns_share_app_receiver_hint), color = Color.White)
                    if (current.apk.isSplit) {
                        Spacer(modifier = Modifier.height(12.dp))
                        BodyMedium(text = stringResource(id = R.string.ns_share_app_split_warning), color = Color.White)
                    }
                }
                if (preparing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    BodyMedium(text = stringResource(id = R.string.ns_share_app_preparing), color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = summary != null && !preparing,
                onClick = {
                    val apk = summary?.apk ?: return@TextButton
                    launch {
                        withContext(Dispatchers.IO) {
                            val zip = ApkShare.zipForBluetooth(context, apk)
                            ApkShare.bluetoothIntent(context, ApkShare.uriFor(context, zip))
                                ?: ApkShare.chooserIntent(context, listOf(zip), chooserTitle).also {
                                    withContext(Dispatchers.Main) {
                                        toast(context, R.string.ns_share_app_no_bluetooth)
                                    }
                                }
                        }
                    }
                },
            ) { Text(stringResource(id = R.string.ns_share_app_bluetooth)) }
        },
        dismissButton = {
            Column {
                TextButton(
                    enabled = summary != null && !preparing,
                    onClick = {
                        val apk = summary?.apk ?: return@TextButton
                        launch {
                            withContext(Dispatchers.IO) {
                                ApkShare.chooserIntent(context, ApkShare.copyForSharing(context, apk), chooserTitle)
                            }
                        }
                    },
                ) { Text(stringResource(id = R.string.ns_share_app_other)) }
                TextButton(enabled = !preparing, onClick = onDismiss) {
                    Text(stringResource(id = R.string.ns_cancel))
                }
            }
        },
    )
}

private fun toast(context: Context, resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
}
