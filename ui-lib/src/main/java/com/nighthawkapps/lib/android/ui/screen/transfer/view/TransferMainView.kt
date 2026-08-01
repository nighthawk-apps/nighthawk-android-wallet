package com.nighthawkapps.lib.android.ui.screen.transfer.view

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.NighthawkBrandingHeader
import com.nighthawkapps.lib.android.ui.common.SettingsListItem
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview
@Composable
fun WalletPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            TransferMainView(onSendMoney = {}, onReceiveMoney = {}, onTopUp = {}, onDaoHub = {})
        }
    }
}

@Composable
fun TransferMainView(
    onSendMoney: () -> Unit,
    onReceiveMoney: () -> Unit,
    onTopUp: () -> Unit,
    onDaoHub: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        NighthawkBrandingHeader()
        BodyMedium(text = stringResource(id = R.string.ns_send_and_receive_drk), color = WalletTheme.colors.secondaryTitleText)
        Spacer(modifier = Modifier.height(13.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_arrow_back_black_24dp,
            title = stringResource(id = R.string.ns_send_money),
            desc = stringResource(id = R.string.ns_send_money_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable {
                        onSendMoney.invoke()
                    },
            rotateByDegree = 180f
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_arrow_back_black_24dp,
            title = stringResource(id = R.string.ns_receive_money),
            desc = stringResource(id = R.string.ns_receive_money_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable {
                        onReceiveMoney.invoke()
                    }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_top_up,
            title = stringResource(id = R.string.ns_top_up),
            desc = stringResource(id = R.string.ns_top_up_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable {
                        onTopUp.invoke()
                    }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsListItem(
            iconRes = R.drawable.ic_icon_total,
            title = stringResource(id = R.string.ns_dao_hub),
            desc = stringResource(id = R.string.ns_dao_hub_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { onDaoHub() },
        )
    }
}
