package com.nighthawkapps.lib.android.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview
@Composable
fun SettingListItemPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            SettingsListItem(
                iconRes = R.drawable.ic_icon_transparent,
                title = stringResource(id = R.string.ns_send_money),
                desc = stringResource(id = R.string.ns_send_money_desc),
                modifier = Modifier.heightIn(min = 56.dp)
            )
        }
    }
}

@Composable
fun SettingsListItem(
    @DrawableRes iconRes: Int,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    rotateByDegree: Float = 0f,
    showDivider: Boolean = true
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .then(modifier)
                    .padding(vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).rotate(rotateByDegree)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                TitleMedium(text = title)
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BodySmall(text = desc)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = WalletTheme.colors.divider
            )
        }
    }
}
