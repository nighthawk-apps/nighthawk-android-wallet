package co.electriccoin.zcash.ui.screen.changeserver.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.MaxWidthHorizontalDivider
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.changeserver.model.CustomServer
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
                serverOptionList = MainnetServer.allServers().subList(0, 5),
                selectedServer = MainnetServer.ZR_AP,
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

    val isCustomServerOptionSelected by remember(localServerSelected) {
        derivedStateOf { localServerSelected.value is CustomServer }
    }

    var customServerHostName by remember(isCustomServerOptionSelected) {
        mutableStateOf(if (isCustomServerOptionSelected) selectedServer?.host ?: "" else "")
    }
    var customServerPortNo by remember(isCustomServerOptionSelected) {
        mutableStateOf(if (isCustomServerOptionSelected) selectedServer?.port?.toString() ?: "" else "")
    }


    Twig.info { "selectedServer is $selectedServer and localSelectedServerIs ${localServerSelected.value} and isCustomServerSelected $isCustomServerOptionSelected" }

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

        // Custom Server
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        MaxWidthHorizontalDivider()
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = localServerSelected.value is CustomServer,
                onClick = { localServerSelected.value = CustomServer("", -1) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            BodyMedium(text = "CustomServer")
        }

        AnimatedVisibility(visible = isCustomServerOptionSelected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = customServerHostName,
                    onValueChange = { customServerHostName = it },
                    placeholder = {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_host),
                            color = ZcashTheme.colors.secondaryTitleText
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    maxLines = 1,
                    modifier = Modifier.weight(0.6f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = customServerPortNo,
                    onValueChange = { customServerPortNo = it },
                    placeholder = {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_port),
                            color = ZcashTheme.colors.secondaryTitleText
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))

        PrimaryButton(
            onClick = {
                if (localServerSelected.value is CustomServer) {
                    localServerSelected.value =
                        CustomServer(customServerHostName, customServerPortNo.toIntOrNull() ?: -1)
                }
                onServerSelected(localServerSelected.value)
            },
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
