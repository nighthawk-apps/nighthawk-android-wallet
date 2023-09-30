package co.electriccoin.zcash.ui.screen.changeserver.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.changeserver.model.LightWalletServer
import co.electriccoin.zcash.ui.screen.changeserver.model.MainnetServer
import kotlinx.collections.immutable.ImmutableList

@Preview
@Composable
fun ChangeServerPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            ChangeServer(
                onBack = {},
                serverOptionList = MainnetServer.allServers(),
                selectedServer = MainnetServer.ASIA_OCEANIA,
                onServerSelected = {}
            )
        }
    }
}

@Composable
fun ChangeServer(
    onBack: () -> Unit,
    serverOptionList: ImmutableList<LightWalletServer>,
    selectedServer: LightWalletServer?,
    onServerSelected: (LightWalletServer?) -> Unit
) {
    val localServerSelected = remember(selectedServer) {
        mutableStateOf(selectedServer)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.screen_standard_margin))
            .verticalScroll(rememberScrollState())
    ) {
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
            text = stringResource(id = R.string.ns_change_server),
            color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        BodyMedium(text = stringResource(id = R.string.ns_change_server_body))

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))

        serverOptionList.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = it == localServerSelected.value,
                    onClick = { localServerSelected.value = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                BodyMedium(text = it.region)
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))

        PrimaryButton(
            onClick = { onServerSelected(localServerSelected.value) },
            text = stringResource(id = R.string.ns_update).uppercase(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .sizeIn(
                    minWidth = dimensionResource(id = R.dimen.button_min_width),
                    minHeight = dimensionResource(id = R.dimen.button_height)
                )
        )
    }
}
