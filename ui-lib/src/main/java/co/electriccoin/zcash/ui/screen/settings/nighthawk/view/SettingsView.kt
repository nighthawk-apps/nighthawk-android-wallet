package co.electriccoin.zcash.ui.screen.settings.nighthawk.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.SettingsListItem
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.TitleLarge
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview
@Composable
fun SettingsPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            SettingsView()
        }
    }
}

@Composable
fun SettingsView() {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.back_icon_size)))
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = "logo", contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(text = stringResource(id = R.string.ns_nighthawk), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(40.dp))
        BodyMedium(text = stringResource(id = R.string.ns_settings), color = ZcashTheme.colors.surfaceEnd)
        Spacer(modifier = Modifier.height(13.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_bell,
            title = stringResource(id = R.string.ns_sync_notifications),
            desc = stringResource(id = R.string.ns_sync_notifications_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height)),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_fiat_currency,
            title = stringResource(id = R.string.ns_fiat_currency),
            desc = stringResource(id = R.string.ns_fiat_currency_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_security,
            title = stringResource(id = R.string.ns_security),
            desc = stringResource(id = R.string.ns_security_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_backup,
            title = stringResource(id = R.string.ns_backup_wallet),
            desc = stringResource(id = R.string.ns_backup_wallet_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_rescan_wallet,
            title = stringResource(id = R.string.ns_rescan_wallet),
            desc = stringResource(id = R.string.ns_rescan_wallet_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_change_server,
            title = stringResource(id = R.string.ns_change_server),
            desc = stringResource(id = R.string.ns_change_server_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_external_services,
            title = stringResource(id = R.string.ns_external_services),
            desc = stringResource(id = R.string.ns_external_services_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_about,
            title = stringResource(id = R.string.ns_about),
            desc = stringResource(id = R.string.ns_about_text),
            modifier = Modifier.heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}