package com.nighthawkapps.lib.android.ui.screen.chat.view

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDmPubkeyParser
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.common.pastePlainText
import com.nighthawkapps.lib.android.ui.common.setSensitivePlainText
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatNewDmSheet(
    controller: DarkfiChatController,
    initialPeerPublicKey: String?,
    onDismiss: () -> Unit,
    onStarted: (contactLabel: String) -> Unit,
) {
    SecureScreen()

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasBinary = remember { DarkircCryptoManager.hasBundledDarkirc(context.applicationContext) }

    val initialCard = remember(initialPeerPublicKey) {
        initialPeerPublicKey?.let { DarkircDmPubkeyParser.parseCard(it) }
    }
    var label by remember(initialPeerPublicKey) { mutableStateOf(initialCard?.nick.orEmpty()) }
    var theirPublic by remember(initialPeerPublicKey) {
        mutableStateOf(initialCard?.publicKeyBase58 ?: initialPeerPublicKey.orEmpty())
    }
    var ratchetCapable by remember(initialPeerPublicKey) {
        mutableStateOf(initialCard?.ratchetCapable == true)
    }
    var myPublic by remember { mutableStateOf("") }
    var mySecret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showPasteConfirm by remember { mutableStateOf(false) }

    if (showPasteConfirm) {
        AlertDialog(
            onDismissRequest = { showPasteConfirm = false },
            title = { Text(stringResource(R.string.ns_chat_dm_paste_pubkey_title)) },
            text = { Text(stringResource(R.string.ns_chat_dm_paste_pubkey_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteConfirm = false
                        scope.launch {
                            val clip = clipboard.pastePlainText().orEmpty()
                            DarkircDmPubkeyParser.parseCard(clip)?.let { card ->
                                theirPublic = card.publicKeyBase58
                                if (label.isBlank() && !card.nick.isNullOrBlank()) {
                                    label = card.nick!!
                                }
                                ratchetCapable = card.ratchetCapable
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_dm_paste_clipboard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteConfirm = false }) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.ns_chat_new_dm),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!hasBinary) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ns_chat_dm_requires_embedded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.ns_chat_dm_contact_label)) },
                supportingText = { Text(stringResource(R.string.ns_chat_dm_contact_label_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = theirPublic,
                onValueChange = {
                    theirPublic = it
                    ratchetCapable = false
                },
                label = { Text(stringResource(R.string.ns_chat_dm_peer_public)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                enabled = !busy,
            )
            TextButton(
                onClick = { showPasteConfirm = true },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.ns_chat_dm_paste_clipboard))
            }
            TextButton(
                onClick = {
                    scope.launch {
                        val kp =
                            withContext(Dispatchers.IO) {
                                controller.generateEphemeralDmShareKeypair()
                            }
                        if (kp != null) {
                            // Keep the secret that matches the displayed pubkey —
                            // addDmContact must not generate a different keypair.
                            myPublic = kp.myDmChachaPublicBase58
                            mySecret = kp.myDmChachaSecretBase58
                        }
                    }
                },
                enabled = !busy && hasBinary,
            ) {
                Text(stringResource(R.string.ns_chat_dm_generate_keys))
            }
            if (myPublic.isNotBlank()) {
                val myPublicLabel = stringResource(R.string.ns_chat_dm_my_public)
                val myCard = DarkircDmPubkeyParser.formatContactCard(
                    publicKeyBase58 = myPublic,
                    nick = controller.ircNickname,
                    ratchetCapable = true,
                )
                Text(
                    text = myPublicLabel,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(text = myCard, style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setSensitivePlainText(
                                context = context,
                                text = myCard,
                                label = myPublicLabel,
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_copied))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                enabled = !busy && hasBinary && label.isNotBlank() && theirPublic.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val parsed = DarkircDmPubkeyParser.parseCard(theirPublic)
                        val result =
                            controller.addDmContact(
                                contactLabel = label,
                                theirPublicBase58 = parsed?.publicKeyBase58 ?: theirPublic,
                                generateMySecret = mySecret.isBlank(),
                                mySecretBase58 = mySecret.ifBlank { null },
                                ratchetCapable = ratchetCapable || (parsed?.ratchetCapable == true),
                            )
                        busy = false
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.ns_chat_dm_added_ok,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                onStarted(label.trim())
                                onDismiss()
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.ns_chat_dm_added_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    }
                },
                text = stringResource(R.string.ns_chat_dm_start),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
