package com.nighthawkapps.lib.android.ui.screen.security.view

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.AlertDialog
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview
@Composable
fun SecurityPreview() {
    WalletTheme {
        Surface {
            Security(
                onBack = {},
                isPinEnabled = true,
                isTouchIdOrFaceEnabled = false,
                isBioMetricEnabledOnMobile = true,
                onDisablePin = {},
                onSetPin = {},
                onTouchIdToggleChanged = {}
            )
        }
    }
}

@Composable
fun Security(
    onBack: () -> Unit,
    isPinEnabled: Boolean,
    isTouchIdOrFaceEnabled: Boolean,
    isBioMetricEnabledOnMobile: Boolean,
    onDisablePin: () -> Unit,
    onSetPin: () -> Unit,
    onTouchIdToggleChanged: (Boolean) -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        val context = LocalContext.current
        val setPinCodeRequestMessage = stringResource(id = R.string.set_pin_code_request)
        val isTouchIdErrorDialogShown =
            remember {
                mutableStateOf(false)
            }

        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = null,
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
        TitleMedium(
            text = stringResource(id = R.string.ns_security_text),
            color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable { onSetPin() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyMedium(text = stringResource(id = R.string.set_change_pin_code))
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = null
            )
        }
        if (isPinEnabled) {
            PrimaryButton(
                onClick = onDisablePin,
                text = stringResource(id = R.string.ns_disable_pin).uppercase(),
                modifier = Modifier.align(Alignment.End)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = WalletTheme.colors.surfaceEnd)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyMedium(text = stringResource(id = R.string.enable_face_id_touch_id))
            Switch(
                checked = isTouchIdOrFaceEnabled,
                onCheckedChange = {
                    Twig.debug { "toggle value changed $it" }
                    if (isPinEnabled.not()) {
                        Toast
                            .makeText(
                                context,
                                setPinCodeRequestMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        return@Switch
                    }
                    if (isBioMetricEnabledOnMobile.not()) {
                        isTouchIdErrorDialogShown.value = true
                        return@Switch
                    }
                    onTouchIdToggleChanged(it)
                }
            )
        }

        if (isTouchIdErrorDialogShown.value) {
            AlertDialog(
                title = stringResource(id = R.string.dialog_bio_metric_not_enabled_title),
                desc = stringResource(id = R.string.dialog_bio_metric_not_enabled_message),
                confirmText = stringResource(id = R.string.support_confirmation_dialog_ok),
                dismissText = "",
                onConfirm = { isTouchIdErrorDialogShown.value = false }
            )
        }
    }
}
