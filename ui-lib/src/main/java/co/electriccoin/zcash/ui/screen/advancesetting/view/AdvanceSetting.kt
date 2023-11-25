package co.electriccoin.zcash.ui.screen.advancesetting.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.AlertDialog
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.advancesetting.model.AvailableLogo
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@Preview
@Composable
fun AdvanceSettingPreview() {
    ZcashTheme(darkTheme = true) {
        Surface {
            AdvanceSetting(
                isScreenOnEnabled = true,
                isBanditAvailable = true,
                isDarkThemeEnabled = false,
                preferredLogo = null,
                allAvailableLogo = AvailableLogo.entries.toPersistentList(),
                onScreenOnEnabledChanged = {},
                onBack = {},
                onNukeWallet = {},
                onLogoPreferenceChanged = {},
                onDarkThemePrefChanged = {}
            )
        }
    }
}

@Composable
fun AdvanceSetting(
    isScreenOnEnabled: Boolean?,
    isBanditAvailable: Boolean,
    isDarkThemeEnabled: Boolean?,
    preferredLogo: AvailableLogo?,
    allAvailableLogo: PersistentList<AvailableLogo>,
    onScreenOnEnabledChanged: (isEnabled: Boolean) -> Unit,
    onBack: () -> Unit,
    onNukeWallet: () -> Unit,
    onLogoPreferenceChanged: (availableLogo: AvailableLogo) -> Unit,
    onDarkThemePrefChanged: (isEnabled: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        var showNukeWalletDialog by remember {
            mutableStateOf(false)
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description)
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
        TitleMedium(
            text = stringResource(id = R.string.advanced),
            color = colorResource(
                id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        TitleMedium(
            text = stringResource(id = R.string.keep_screen_on),
            color = colorResource(
                id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyMedium(
                text = stringResource(id = R.string.keep_screen_on_detail_msg),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isScreenOnEnabled ?: false,
                onCheckedChange = { onScreenOnEnabledChanged(it) },
            )
        }

        if (isBanditAvailable) {
            Spacer(modifier = Modifier.height(24.dp))
            TitleMedium(
                text = stringResource(id = R.string.app_icon),
                color = colorResource(
                    id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet
                )
            )

            allAvailableLogo.forEach { logo ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = logo == preferredLogo,
                        onClick = { onLogoPreferenceChanged(logo) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        imageVector = ImageVector.vectorResource(id = logo.logoId),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = Color.Black, shape = RoundedCornerShape(4.dp))
                            .border(
                                width = 1.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 3.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BodyMedium(text = stringResource(id = logo.nameId))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleMedium(
                    text = stringResource(id = R.string.enable_dark_theme),
                    color = colorResource(
                        id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isDarkThemeEnabled ?: false,
                    onCheckedChange = { onDarkThemePrefChanged(it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        TitleMedium(
            text = stringResource(id = R.string.delete_wallet),
            color = colorResource(
                id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        BodyMedium(
            text = stringResource(id = R.string.nuke_wallet_caution),
            color = ZcashTheme.colors.dangerous
        )
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryButton(
            onClick = { showNukeWalletDialog = true },
            text = stringResource(id = R.string.delete_wallet).uppercase(),
            modifier = Modifier
                .align(Alignment.Start)
                .sizeIn(
                    minWidth = dimensionResource(id = R.dimen.button_min_width),
                    minHeight = dimensionResource(id = R.dimen.button_height)
                ),
        )

        if (showNukeWalletDialog) {
            AlertDialog(
                title = stringResource(id = R.string.are_you_sure),
                desc = stringResource(id = R.string.nuke_wallet_dialog_msg),
                confirmText = stringResource(id = R.string.delete_wallet),
                dismissText = stringResource(id = R.string.ns_cancel),
                onConfirm = {
                    onNukeWallet()
                    showNukeWalletDialog = false
                },
                onDismiss = {
                    showNukeWalletDialog = false
                }
            )
        }
    }
}
