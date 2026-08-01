@file:Suppress("MaxLineLength")

package com.nighthawkapps.lib.android.ui.screen.changeserver.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.global.AppDaemonCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.daemon.DarkfiRestartConnectionDialog
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.MaxWidthHorizontalDivider
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.CUSTOM_DARKFI_ENDPOINT_LABEL
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.DarkfiEndpointCatalog
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.DarkfiEndpointPreset
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.validateDarkfiRpcHostPort
import kotlinx.collections.immutable.ImmutableList

@Preview
@Composable
fun ChangeServerPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            ChangeServer(
                onBack = {},
                presetOptionList = DarkfiEndpointCatalog.presets(DarkfiNetwork.Mainnet),
                selectedPreset = DarkfiEndpointCatalog.presets(DarkfiNetwork.Mainnet)[0],
                onPresetSelected = {},
            )
        }
    }
}

@Composable
fun ChangeServer(
    onBack: () -> Unit,
    presetOptionList: ImmutableList<DarkfiEndpointPreset>,
    selectedPreset: DarkfiEndpointPreset?,
    onPresetSelected: (DarkfiEndpointPreset?) -> Unit,
) {
    val localPresetSelected =
        remember(selectedPreset) {
            mutableStateOf(selectedPreset)
        }

    val isCustomOptionSelected by remember(localPresetSelected) {
        derivedStateOf {
            localPresetSelected.value?.label == CUSTOM_DARKFI_ENDPOINT_LABEL
        }
    }

    var customServerHostName by remember(isCustomOptionSelected) {
        mutableStateOf(
            if (isCustomOptionSelected) localPresetSelected.value?.endpoint?.host ?: "" else "",
        )
    }
    var customServerPortNo by remember(isCustomOptionSelected) {
        mutableStateOf(
            if (isCustomOptionSelected) {
                localPresetSelected.value
                    ?.endpoint
                    ?.port
                    ?.toString() ?: ""
            } else {
                ""
            },
        )
    }

    var showRestartDialog by remember { mutableStateOf(false) }

    Twig.info {
        "selectedPreset=$selectedPreset local=${localPresetSelected.value} custom=$isCustomOptionSelected"
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(rememberScrollState()),
    ) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = null,
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
        TitleMedium(
            text = stringResource(id = R.string.ns_change_server),
            color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        BodyMedium(text = stringResource(id = R.string.ns_change_server_body))

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))

        presetOptionList.forEach { preset ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = localPresetSelected.value == preset,
                    onClick = { localPresetSelected.value = preset },
                )
                Spacer(modifier = Modifier.width(8.dp))
                BodyMedium(text = preset.label)
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        MaxWidthHorizontalDivider()
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = localPresetSelected.value?.label == CUSTOM_DARKFI_ENDPOINT_LABEL,
                onClick = { localPresetSelected.value = DarkfiEndpointCatalog.incompleteCustom() },
            )
            Spacer(modifier = Modifier.width(8.dp))
            BodyMedium(text = CUSTOM_DARKFI_ENDPOINT_LABEL)
        }

        AnimatedVisibility(visible = isCustomOptionSelected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedTextField(
                    value = customServerHostName,
                    onValueChange = { customServerHostName = it },
                    placeholder = {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_host),
                            color = WalletTheme.colors.secondaryTitleText,
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    maxLines = 1,
                    modifier = Modifier.weight(0.6f),
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = customServerPortNo,
                    onValueChange = { customServerPortNo = it },
                    placeholder = {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_port),
                            color = WalletTheme.colors.secondaryTitleText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    maxLines = 1,
                    modifier = Modifier.weight(0.4f),
                )
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))

        PrimaryButton(
            onClick = {
                val base = localPresetSelected.value
                val outgoing =
                    if (base?.label == CUSTOM_DARKFI_ENDPOINT_LABEL) {
                        if (!validateDarkfiRpcHostPort(customServerHostName, customServerPortNo)) {
                            null
                        } else {
                            DarkfiEndpointPreset(
                                label = CUSTOM_DARKFI_ENDPOINT_LABEL,
                                endpoint =
                                    DarkfiEndpoint(
                                        host = customServerHostName.trim(),
                                        port = customServerPortNo.toInt(),
                                        isTls = false,
                                    ),
                            )
                        }
                    } else {
                        base
                    }
                outgoing?.let {
                    localPresetSelected.value = it
                    onPresetSelected(it)
                    showRestartDialog = true
                }
            },
            text = stringResource(id = R.string.ns_update).uppercase(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height),
                    ),
        )

        if (showRestartDialog) {
            DarkfiRestartConnectionDialog(
                onConfirm = {
                    AppDaemonCoordinator.get().restartConnection()
                    onBack()
                },
                onDismiss = {
                    showRestartDialog = false
                    onBack()
                },
            )
        }
    }
}
