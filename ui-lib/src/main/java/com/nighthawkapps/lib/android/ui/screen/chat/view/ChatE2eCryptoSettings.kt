@file:Suppress("LongMethod", "CyclomaticComplexMethod")

package com.nighthawkapps.lib.android.ui.screen.chat.view

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircChannelCryptoConfig
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCliKeygen
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircContactCryptoConfig
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog as MaterialAlertDialog

@Composable
internal fun ChatE2eCryptoSettings(
    embeddedDarkircEnabled: Boolean,
    onApplied: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext

    var channels by remember { mutableStateOf(DarkircCryptoManager.loadChannels(appContext)) }
    var contacts by remember { mutableStateOf(DarkircCryptoManager.loadContacts(appContext)) }
    val hasBinary = remember { DarkircCryptoManager.hasBundledDarkirc(appContext) }

    var showAddChannel by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }

    fun reload() {
        channels = DarkircCryptoManager.loadChannels(appContext)
        contacts = DarkircCryptoManager.loadContacts(appContext)
    }

    fun applyCrypto(onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val ok = DarkircCryptoManager.applyAndRestartEmbeddedDaemon(appContext)
            withContext(Dispatchers.Main) {
                if (ok) {
                    reload()
                    onApplied()
                }
                onDone(ok)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TitleMedium(text = stringResource(R.string.ns_chat_e2e_section_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ns_chat_e2e_section_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!embeddedDarkircEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ns_chat_e2e_requires_embedded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!hasBinary) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ns_chat_e2e_no_binary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        TitleMedium(
            text = stringResource(R.string.ns_chat_e2e_channels_title),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (channels.isEmpty()) {
            Text(
                text = stringResource(R.string.ns_chat_e2e_channels_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            channels.forEach { ch ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(text = ch.channel, style = MaterialTheme.typography.bodyMedium)
                    ch.topic?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            DarkircCryptoManager.removeChannel(appContext, ch.channel)
                            applyCrypto { ok ->
                                Toast
                                    .makeText(
                                        context,
                                        if (ok) R.string.ns_chat_e2e_removed_ok else R.string.ns_chat_e2e_saved_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ns_chat_e2e_remove))
                    }
                }
            }
        }
        PrimaryButton(
            enabled = embeddedDarkircEnabled,
            onClick = { showAddChannel = true },
            text = stringResource(R.string.ns_chat_e2e_add_channel),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
        TitleMedium(
            text = stringResource(R.string.ns_chat_e2e_contacts_title),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (contacts.isEmpty()) {
            Text(
                text = stringResource(R.string.ns_chat_e2e_contacts_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            contacts.forEach { contact ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(text = contact.nick, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            DarkircCryptoManager.removeContact(appContext, contact.nick)
                            applyCrypto { ok ->
                                Toast
                                    .makeText(
                                        context,
                                        if (ok) R.string.ns_chat_e2e_removed_ok else R.string.ns_chat_e2e_saved_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ns_chat_e2e_remove))
                    }
                }
            }
        }
        PrimaryButton(
            enabled = embeddedDarkircEnabled,
            onClick = { showAddContact = true },
            text = stringResource(R.string.ns_chat_e2e_add_contact),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showAddChannel) {
        AddChannelDialog(
            canGenerate = hasBinary,
            onDismiss = { showAddChannel = false },
            onSave = { channel, secret, topic ->
                showAddChannel = false
                runCatching {
                    DarkircCryptoManager.saveChannel(
                        appContext,
                        DarkircChannelCryptoConfig(
                            channel = channel,
                            secretBase58 = secret,
                            topic = topic?.takeIf { it.isNotBlank() },
                        ),
                    )
                }.onSuccess {
                    applyCrypto { ok ->
                        Toast
                            .makeText(
                                context,
                                if (ok) R.string.ns_chat_e2e_saved_ok else R.string.ns_chat_e2e_saved_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }.onFailure {
                    Toast.makeText(context, R.string.ns_chat_e2e_saved_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onGenerateSecret = {
                withContext(Dispatchers.IO) {
                    DarkircCryptoManager.generateChannelSecret(appContext)
                }
            },
        )
    }

    if (showAddContact) {
        AddContactDialog(
            canGenerate = hasBinary,
            clipboard = clipboard,
            onDismiss = { showAddContact = false },
            onSave = { nick, theirPublic, mySecret ->
                showAddContact = false
                runCatching {
                    DarkircCryptoManager.saveContact(
                        appContext,
                        DarkircContactCryptoConfig(
                            nick = nick.trim(),
                            dmChachaPublicBase58 = theirPublic.trim(),
                            myDmChachaSecretBase58 = mySecret.trim(),
                        ),
                    )
                }.onSuccess {
                    applyCrypto { ok ->
                        Toast
                            .makeText(
                                context,
                                if (ok) R.string.ns_chat_e2e_saved_ok else R.string.ns_chat_e2e_saved_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }.onFailure {
                    Toast.makeText(context, R.string.ns_chat_e2e_saved_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onGenerateKeypair = {
                withContext(Dispatchers.IO) {
                    DarkircCryptoManager.generateDmKeypair(appContext)
                }
            },
        )
    }
}

@Composable
private fun AddChannelDialog(
    canGenerate: Boolean,
    onDismiss: () -> Unit,
    onSave: (channel: String, secret: String, topic: String?) -> Unit,
    onGenerateSecret: suspend () -> String?,
) {
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf("#") }
    var secret by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }

    MaterialAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_chat_e2e_channel_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text(stringResource(R.string.ns_chat_e2e_channel_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(R.string.ns_chat_e2e_channel_secret)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (canGenerate) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                onGenerateSecret()?.let { secret = it }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ns_chat_e2e_generate_secret))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.ns_chat_e2e_channel_topic)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized =
                        channel.trim().let { if (it.startsWith("#")) it else "#$it" }
                    onSave(normalized, secret.trim(), topic.trim())
                },
            ) {
                Text(stringResource(R.string.ns_chat_e2e_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ns_cancel))
            }
        },
    )
}

@Composable
private fun AddContactDialog(
    canGenerate: Boolean,
    clipboard: androidx.compose.ui.platform.Clipboard,
    onDismiss: () -> Unit,
    onSave: (nick: String, theirPublic: String, mySecret: String) -> Unit,
    onGenerateKeypair: suspend () -> DarkircCliKeygen.DmKeypair?,
) {
    val scope = rememberCoroutineScope()
    var nick by remember { mutableStateOf("") }
    var theirPublic by remember { mutableStateOf("") }
    var mySecret by remember { mutableStateOf("") }
    var myPublic by remember { mutableStateOf("") }

    MaterialAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_chat_e2e_contact_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nick,
                    onValueChange = { nick = it },
                    label = { Text(stringResource(R.string.ns_chat_e2e_contact_nick)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = theirPublic,
                    onValueChange = { theirPublic = it },
                    label = { Text(stringResource(R.string.ns_chat_e2e_contact_their_public)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mySecret,
                    onValueChange = { mySecret = it },
                    label = { Text(stringResource(R.string.ns_chat_e2e_contact_my_secret)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (canGenerate) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                onGenerateKeypair()?.let { kp ->
                                    mySecret = kp.myDmChachaSecretBase58
                                    myPublic = kp.myDmChachaPublicBase58
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ns_chat_e2e_generate_dm_keys))
                    }
                }
                if (myPublic.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ns_chat_e2e_my_public_key),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = myPublic,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setPlainText(myPublic)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ns_copied))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nick, theirPublic, mySecret) },
            ) {
                Text(stringResource(R.string.ns_chat_e2e_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ns_cancel))
            }
        },
    )
}
