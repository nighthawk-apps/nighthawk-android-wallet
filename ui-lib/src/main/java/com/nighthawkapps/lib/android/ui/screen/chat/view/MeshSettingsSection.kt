package com.nighthawkapps.lib.android.ui.screen.chat.view

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.mesh.MeshCoordinator
import com.nighthawkapps.lib.android.sdk.mesh.MeshNative
import com.nighthawkapps.lib.android.sdk.mesh.MeshPermissionGate
import com.nighthawkapps.lib.android.sdk.mesh.NighthawkMeshService
import com.nighthawkapps.lib.android.sdk.mesh.enableFromForeground
import com.nighthawkapps.lib.android.sdk.mesh.findActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.scan.util.SettingsUtil
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.MeshSettingsSection(showOemCopy: Boolean = false) {
    if (MeshCoordinator.canShowMeshToggle()) {
        with(MeshSettingsPages) { this@MeshSettingsSection.Controls(showOemCopy) }
    } else {
        MeshSettingsPages.Notice()
    }
}

private object MeshSettingsPages {
    @Composable
    fun Notice() {
        Text(
            text = stringResource(R.string.ns_chat_mesh_hidden_old_android),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    fun ColumnScope.Controls(showOemCopy: Boolean) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var meshOn by remember { mutableStateOf(false) }
        var alwaysOn by remember { mutableStateOf(true) }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        val host =
            MeshToggleHost(
                context = context,
                scope = scope,
                alwaysOn = { alwaysOn },
                onMeshOn = { meshOn = it },
                onStatus = { statusMessage = it },
                onAlwaysOn = { alwaysOn = it },
            )
        LoadPreferences(context, host)
        Intro(statusMessage)
        val toggle = MeshSettingsBits.Handler(host)
        EnableSwitch(meshOn, toggle.onChecked)
        if (meshOn) {
            WhileEnabled(alwaysOn, host)
        }
        if (showOemCopy) {
            MeshSettingsBits.OemCopy()
        }
    }

    @Composable
    fun LoadPreferences(
        context: Context,
        host: MeshToggleHost,
    ) {
        LaunchedEffect(Unit) {
            val prefs = StandardPreferenceSingleton.getInstance(context)
            val meshOn =
                StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.getValue(prefs) ||
                    NighthawkMeshService.isActive
            val alwaysOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ALWAYS_ON.getValue(prefs)
            MeshCoordinator.setAlwaysOn(alwaysOn)
            MeshCoordinator.setGatewayOptIn(false)
            host.onMeshOn(meshOn)
            host.onAlwaysOn(alwaysOn)
            host.onStatus(MeshToggleLogic.startupStatus(context, meshOn))
        }
    }

    @Composable
    fun ColumnScope.Intro(statusMessage: String?) {
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
        if (statusMessage != null) {
            StatusBlock(statusMessage)
        }
    }

    @Composable
    fun ColumnScope.StatusBlock(message: String) {
        val context = LocalContext.current
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = {
                context.startActivity(SettingsUtil.newSettingsIntent(context.packageName))
            },
        ) {
            Text(stringResource(R.string.ns_chat_mesh_open_settings))
        }
    }

    @Composable
    fun ColumnScope.EnableSwitch(
        meshOn: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        MeshSettingsBits.SwitchRow(
            title = stringResource(R.string.ns_chat_mesh_toggle),
            checked = meshOn,
            onCheckedChange = onCheckedChange,
        )
    }

    @Composable
    fun ColumnScope.WhileEnabled(
        alwaysOn: Boolean,
        host: MeshToggleHost,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        MeshSettingsBits.SwitchRow(
            title = stringResource(R.string.ns_chat_mesh_always_on),
            checked = alwaysOn,
            onCheckedChange = { on ->
                host.onAlwaysOn(on)
                MeshCoordinator.setAlwaysOn(on)
                MeshToggleLogic.persistAlwaysOn(host, on)
            },
        )
        Text(
            text = stringResource(R.string.ns_chat_mesh_always_on_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RuntimeWarnings()
    }

    @Composable
    fun ColumnScope.RuntimeWarnings() {
        if (!MeshNative.neighborsReady()) {
            Warning(
                stringResource(R.string.ns_chat_mesh_unavailable),
                MaterialTheme.colorScheme.error,
            )
        }
        if (MeshNative.cacheEvicted) {
            Warning(
                stringResource(R.string.ns_chat_mesh_cache_full),
                MaterialTheme.colorScheme.error,
            )
        }
        DaemonWarning()
    }

    @Composable
    fun ColumnScope.DaemonWarning() {
        val daemon = runCatching { darkircStatus() }.getOrDefault("not_running")
        if (daemon == "not_running" || daemon == "failed") {
            Warning(
                stringResource(R.string.ns_chat_mesh_idle),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    fun ColumnScope.Warning(
        text: String,
        color: Color,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

private object MeshSettingsBits {
    @Composable
    fun OemCopy() {
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

    @Composable
    fun SwitchRow(
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

    @Composable
    fun Handler(host: MeshToggleHost): MeshToggleActions {
        val enableBt =
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                MeshToggleLogic.onBluetoothResult(host)
            }
        val permissions =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                MeshToggleLogic.onPermissionResult(host, enableBt, grants)
            }
        return MeshToggleActions { on ->
            MeshToggleLogic.onChecked(host, enableBt, permissions, on)
        }
    }
}

private class MeshToggleHost(
    val context: Context,
    val scope: CoroutineScope,
    val alwaysOn: () -> Boolean,
    val onMeshOn: (Boolean) -> Unit,
    val onStatus: (String?) -> Unit,
    val onAlwaysOn: (Boolean) -> Unit = {},
)

private class MeshToggleActions(
    val onChecked: (Boolean) -> Unit,
)

private object MeshToggleLogic {
    fun startupStatus(
        context: Context,
        meshOn: Boolean,
    ): String? {
        if (!meshOn || NighthawkMeshService.isActive) return null
        return blockedStatus(context)
    }

    fun persistAlwaysOn(
        host: MeshToggleHost,
        on: Boolean,
    ) {
        host.scope.launch {
            val prefs = StandardPreferenceSingleton.getInstance(host.context)
            StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ALWAYS_ON.putValue(prefs, on)
        }
    }

    fun onBluetoothResult(host: MeshToggleHost) {
        if (MeshPermissionGate.isBluetoothAdapterEnabled(host.context)) {
            startMesh(host)
        } else {
            host.onStatus(host.context.getString(R.string.ns_chat_mesh_bt_off))
        }
    }

    fun afterBleGranted(
        host: MeshToggleHost,
        enableBt: ActivityResultLauncher<Intent>,
    ) {
        if (MeshPermissionGate.isBluetoothAdapterEnabled(host.context)) {
            startMesh(host)
            return
        }
        host.onStatus(host.context.getString(R.string.ns_chat_mesh_bt_off))
        try {
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
        }
    }

    fun onPermissionResult(
        host: MeshToggleHost,
        enableBt: ActivityResultLauncher<Intent>,
        grants: Map<String, Boolean>,
    ) {
        if (grantsAllow(host.context, grants)) {
            afterBleGranted(host, enableBt)
        } else {
            host.onStatus(host.context.getString(R.string.ns_chat_mesh_permission_denied))
        }
    }

    fun onChecked(
        host: MeshToggleHost,
        enableBt: ActivityResultLauncher<Intent>,
        permissions: ActivityResultLauncher<Array<String>>,
        on: Boolean,
    ) {
        if (on) {
            MeshToggleApply.enable(host, enableBt, permissions)
        } else {
            MeshToggleApply.disable(host)
        }
    }

    private fun blockedStatus(context: Context): String? =
        when {
            !MeshPermissionGate.hasBlePermissions(context) -> {
                context.getString(R.string.ns_chat_mesh_permission_denied)
            }

            !MeshPermissionGate.isBluetoothAdapterEnabled(context) -> {
                context.getString(R.string.ns_chat_mesh_bt_off)
            }

            else -> {
                null
            }
        }

    private fun startMesh(host: MeshToggleHost): Boolean {
        val activity = host.context.findActivity()
        MeshCoordinator.setAlwaysOn(host.alwaysOn())
        MeshCoordinator.setGatewayOptIn(false)
        val started = activity != null && MeshCoordinator.enableFromForeground(activity)
        if (started) {
            host.onMeshOn(true)
            persistEnabled(host, true)
            host.onStatus(null)
        }
        return started
    }

    private fun persistEnabled(
        host: MeshToggleHost,
        on: Boolean,
    ) {
        host.scope.launch {
            val prefs = StandardPreferenceSingleton.getInstance(host.context)
            StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.putValue(prefs, on)
        }
    }

    private fun grantsAllow(
        context: Context,
        grants: Map<String, Boolean>,
    ): Boolean {
        if (MeshPermissionGate.blePermissions().all { perm -> grants[perm] == true }) return true
        return MeshPermissionGate.hasBlePermissions(context)
    }
}

private object MeshToggleApply {
    fun enable(
        host: MeshToggleHost,
        enableBt: ActivityResultLauncher<Intent>,
        permissions: ActivityResultLauncher<Array<String>>,
    ) {
        if (MeshPermissionGate.hasBlePermissions(host.context)) {
            MeshToggleLogic.afterBleGranted(host, enableBt)
        } else {
            permissions.launch(MeshPermissionGate.runtimeMeshPermissions(false))
        }
    }

    fun disable(host: MeshToggleHost) {
        host.onMeshOn(false)
        MeshCoordinator.disable(host.context)
        host.scope.launch {
            val prefs = StandardPreferenceSingleton.getInstance(host.context)
            StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.putValue(prefs, false)
        }
        host.onStatus(null)
    }
}
