package com.nighthawkapps.lib.android.ui.screen.chat.view

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatIdentity
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonBootstrap
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.AlertDialog
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog as MaterialAlertDialog

@Composable
internal fun ChatSettingsScreen(
    controller: DarkfiChatController,
    identity: DarkfiChatIdentity,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val chatPrefs = remember { DarkfiChatPreferences(context.applicationContext) }
    var embeddedDarkirc by remember { mutableStateOf(chatPrefs.runEmbeddedDarkirc) }

    var publicId by remember { mutableStateOf(identity.publicIdHex()) }
    var hasIdentity by remember { mutableStateOf(identity.hasIdentity()) }

    var ircHost by remember { mutableStateOf(controller.ircServerHost) }
    var ircPortText by remember { mutableStateOf(controller.ircServerPort.toString()) }
    var ircNick by remember { mutableStateOf(controller.ircNickname.ifBlank { "" }) }
    var ircPass by remember { mutableStateOf(controller.ircPassword.orEmpty()) }

    var importPhraseText by remember { mutableStateOf("") }
    var showGenerateConfirm by remember { mutableStateOf(false) }
    var showRevealPhrase by remember { mutableStateOf(false) }
    var showImportPhrase by remember { mutableStateOf(false) }
    var revealedPhrase by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        publicId = identity.publicIdHex()
        hasIdentity = identity.hasIdentity()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(id = R.dimen.screen_standard_margin)),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description),
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))

        TitleMedium(text = stringResource(R.string.ns_chat_settings_screen_title))

        Spacer(modifier = Modifier.height(16.dp))
        TitleMedium(
            text = stringResource(R.string.ns_chat_identity_section_title),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.ns_chat_identity_section_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = ircNick,
            onValueChange = {
                ircNick = it
                controller.ircNickname = it
            },
            label = { Text(stringResource(R.string.ns_chat_irc_nick)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))

        PrimaryButton(
            onClick = {
                if (hasIdentity) {
                    showGenerateConfirm = true
                } else {
                    scope.launch {
                        val ok =
                            withContext(Dispatchers.IO) {
                                identity.generateNew(context.assets)
                            }
                        if (ok) {
                            publicId = identity.publicIdHex()
                            hasIdentity = identity.hasIdentity()
                            Toast.makeText(context, R.string.ns_chat_identity_generated, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.ns_chat_identity_generate_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            text = stringResource(R.string.ns_chat_generate_identity),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButton(
            onClick = { showImportPhrase = true },
            text = stringResource(R.string.ns_chat_import_phrase),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.ns_chat_public_id_label),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedTextField(
            value = publicId.orEmpty(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.ns_chat_public_id_placeholder)) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButton(
            enabled = !publicId.isNullOrBlank(),
            onClick = {
                publicId?.let { clip ->
                    scope.launch {
                        clipboard.setPlainText(clip)
                    }
                    Toast.makeText(context, R.string.ns_copied, Toast.LENGTH_SHORT).show()
                }
            },
            text = stringResource(R.string.ns_chat_copy_public_id),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButton(
            enabled = !publicId.isNullOrBlank(),
            onClick = {
                val share =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, publicId ?: "")
                    }
                context.startActivity(Intent.createChooser(share, context.getString(R.string.ns_chat_share_public_id)))
            },
            text = stringResource(R.string.ns_chat_share_public_id),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButton(
            enabled = hasIdentity,
            onClick = {
                val words = identity.secretPhraseWords()
                revealedPhrase = words?.joinToString(" ")
                showRevealPhrase = true
            },
            text = stringResource(R.string.ns_chat_reveal_secret_phrase),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))
        TitleMedium(text = stringResource(R.string.ns_chat_transport_section_title))

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ns_chat_embedded_darkirc),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = embeddedDarkirc,
                onCheckedChange = { on ->
                    embeddedDarkirc = on
                    chatPrefs.runEmbeddedDarkirc = on
                    if (on) {
                        DarkircDaemonBootstrap.maybeStart(context.applicationContext)
                    } else {
                        DarkircDaemonBootstrap.stop(context.applicationContext)
                    }
                },
            )
        }
        Text(
            text = stringResource(R.string.ns_chat_embedded_darkirc_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (embeddedDarkirc) {
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = DarkircDaemonBootstrap.restartEmbeddedLoopback(context.applicationContext)
                        withContext(Dispatchers.Main) {
                            val msg =
                                if (ok) {
                                    R.string.ns_chat_embedded_darkirc_restart_ok
                                } else {
                                    R.string.ns_chat_embedded_darkirc_restart_failed
                                }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        controller.connectOrRetry()
                    }
                },
                text = stringResource(R.string.ns_chat_embedded_darkirc_restart),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        ChatE2eCryptoSettings(
            embeddedDarkircEnabled = embeddedDarkirc,
            onApplied = { controller.connectOrRetry() },
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = ircHost,
            onValueChange = {
                ircHost = it
                controller.ircServerHost = it
            },
            label = { Text(stringResource(R.string.ns_chat_irc_host)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = ircPortText,
            onValueChange = { raw ->
                if (raw.all { it.isDigit() } && raw.length <= 5) {
                    ircPortText = raw
                    raw.toIntOrNull()?.let { controller.ircServerPort = it }
                }
            },
            label = { Text(stringResource(R.string.ns_chat_irc_port)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = ircPass,
            onValueChange = {
                ircPass = it
                controller.ircPassword = it.takeIf { s -> s.isNotBlank() }
            },
            label = { Text(stringResource(R.string.ns_chat_irc_password)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.ns_chat_tor_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryButton(
            onClick = { controller.connectOrRetry() },
            text = stringResource(R.string.ns_chat_apply_reconnect),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showGenerateConfirm) {
        AlertDialog(
            title = stringResource(R.string.ns_chat_regenerate_title),
            desc = stringResource(R.string.ns_chat_regenerate_body),
            confirmText = stringResource(R.string.ns_chat_regenerate_confirm),
            dismissText = stringResource(R.string.ns_cancel),
            onConfirm = {
                showGenerateConfirm = false
                scope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            identity.generateNew(context.assets)
                        }
                    if (ok) {
                        publicId = identity.publicIdHex()
                        hasIdentity = identity.hasIdentity()
                        Toast.makeText(context, R.string.ns_chat_identity_generated, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.ns_chat_identity_generate_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showGenerateConfirm = false },
        )
    }

    if (showRevealPhrase && revealedPhrase != null) {
        AlertDialog(
            title = stringResource(R.string.ns_chat_secret_phrase_title),
            desc = revealedPhrase!!,
            confirmText = stringResource(R.string.ns_chat_copy_phrase),
            dismissText = stringResource(R.string.ns_cancel),
            onConfirm = {
                scope.launch {
                    clipboard.setPlainText(revealedPhrase!!)
                }
                Toast.makeText(context, R.string.ns_copied, Toast.LENGTH_SHORT).show()
                showRevealPhrase = false
                revealedPhrase = null
            },
            onDismiss = {
                showRevealPhrase = false
                revealedPhrase = null
            },
        )
    }

    if (showImportPhrase) {
        MaterialAlertDialog(
            onDismissRequest = {
                showImportPhrase = false
                importPhraseText = ""
            },
            title = { Text(stringResource(R.string.ns_chat_import_phrase_title)) },
            text = {
                OutlinedTextField(
                    value = importPhraseText,
                    onValueChange = { importPhraseText = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    label = { Text(stringResource(R.string.ns_chat_import_phrase_hint)) },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val raw = importPhraseText
                        showImportPhrase = false
                        importPhraseText = ""
                        scope.launch {
                            val ok =
                                withContext(Dispatchers.IO) {
                                    identity.restoreFromPhraseText(raw, context.assets)
                                }
                            if (ok) {
                                publicId = identity.publicIdHex()
                                hasIdentity = identity.hasIdentity()
                                Toast.makeText(context, R.string.ns_chat_import_success, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, R.string.ns_chat_import_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_import_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportPhrase = false
                        importPhraseText = ""
                    },
                ) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }
}
