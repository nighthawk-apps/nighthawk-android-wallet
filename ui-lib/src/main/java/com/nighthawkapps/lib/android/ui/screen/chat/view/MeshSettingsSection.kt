package com.nighthawkapps.lib.android.ui.screen.chat.view

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.mesh.MeshCoordinator
import com.nighthawkapps.lib.android.sdk.mesh.MeshPermissionGate
import com.nighthawkapps.lib.android.sdk.mesh.NighthawkMeshService
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import kotlinx.coroutines.launch

@Composable
internal fun MeshSettingsSection(showOemCopy: Boolean = false) {
    if (!MeshCoordinator.canShowMeshToggle()) {
        Text(
            text = stringResource(R.string.ns_chat_mesh_hidden_old_android),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var meshOn by remember { mutableStateOf(false) }
    var alwaysOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val prefs = StandardPreferenceSingleton.getInstance(context)
        meshOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.getValue(prefs)
        alwaysOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ALWAYS_ON.getValue(prefs)
        MeshCoordinator.setAlwaysOn(alwaysOn)
        MeshCoordinator.setGatewayOptIn(false)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            val bleOk =
                MeshPermissionGate.blePermissions().all { perm -> grants[perm] == true } ||
                    MeshPermissionGate.hasBlePermissions(context)
            if (bleOk) {
                val activity = context as? Activity
                MeshCoordinator.setAlwaysOn(alwaysOn)
                MeshCoordinator.setGatewayOptIn(false)
                val started = activity != null && MeshCoordinator.enableFromForeground(activity)
                if (started) {
                    meshOn = true
                    scope.launch {
                        val prefs = StandardPreferenceSingleton.getInstance(context)
                        StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.putValue(prefs, true)
                    }
                }
            }
        }

    Spacer(modifier = Modifier.height(16.dp))
    TitleMedium(text = stringResource(R.string.ns_chat_mesh_section))
    Text(
        text = stringResource(R.string.ns_chat_mesh_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text =
            if (NighthawkMeshService.isActive) {
                stringResource(R.string.ns_chat_mesh_hud_on, NighthawkMeshService.hudPeerCount)
            } else {
                stringResource(R.string.ns_chat_mesh_hud_off)
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MeshSwitchRow(
        title = stringResource(R.string.ns_chat_mesh_toggle),
        checked = meshOn,
        onCheckedChange = { on ->
            if (on) {
                if (MeshPermissionGate.hasBlePermissions(context)) {
                    val activity = context as? Activity
                    MeshCoordinator.setAlwaysOn(alwaysOn)
                    MeshCoordinator.setGatewayOptIn(false)
                    val started = activity != null && MeshCoordinator.enableFromForeground(activity)
                    if (started) {
                        meshOn = true
                        scope.launch {
                            val prefs = StandardPreferenceSingleton.getInstance(context)
                            StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.putValue(prefs, true)
                        }
                    }
                } else {
                    permissionLauncher.launch(
                        MeshPermissionGate.runtimeMeshPermissions(false),
                    )
                }
            } else {
                meshOn = false
                MeshCoordinator.disable(context)
                scope.launch {
                    val prefs = StandardPreferenceSingleton.getInstance(context)
                    StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.putValue(prefs, false)
                }
            }
        },
    )
    if (meshOn) {
        Spacer(modifier = Modifier.height(8.dp))
        MeshSwitchRow(
            title = stringResource(R.string.ns_chat_mesh_always_on),
            checked = alwaysOn,
            onCheckedChange = { on ->
                alwaysOn = on
                MeshCoordinator.setAlwaysOn(on)
                scope.launch {
                    val prefs = StandardPreferenceSingleton.getInstance(context)
                    StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ALWAYS_ON.putValue(prefs, on)
                }
            },
        )
        Text(
            text = stringResource(R.string.ns_chat_mesh_always_on_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showOemCopy) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ns_chat_mesh_oem_copy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ns_chat_mesh_play_copy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeshSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
