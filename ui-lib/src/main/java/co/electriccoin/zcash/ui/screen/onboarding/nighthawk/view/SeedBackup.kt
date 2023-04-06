package co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TertiaryButton
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview
@Composable
fun SeedFeedBackPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            SeedBackup(
                onBack = {},
                onExportAsPdf = {},
                onContinue = {}
            )
        }
    }
}

@Composable
fun SeedBackup(
    onBack: () -> Unit,
    onExportAsPdf: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        val checkedState = remember { mutableStateOf(false) }

        IconButton(
            onClick = onBack,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description)
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        BodyMedium(text = stringResource(id = R.string.ns_create_wallet_title), color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet))
        Spacer(modifier = Modifier.height(11.dp))
        BodySmall(text = stringResource(id = R.string.ns_create_wallet_text))
        Spacer(modifier = Modifier.height(25.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(24) {
                SeedItem(seedIndex = it + 1, seedWord = "Word")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        BodyMedium(text = stringResource(id = R.string.ns_wallet_birthday, 1800000))
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checkedState.value,
                onCheckedChange = { checkedState.value = it },
                modifier = Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(uncheckedColor = Color.White)
            )
            BodyMedium(text = stringResource(id = R.string.ns_create_wallet_confirm_text), modifier = Modifier.padding(start = 11.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = onContinue,
            text = stringResource(id = R.string.ns_continue).uppercase(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .sizeIn(minWidth = dimensionResource(id = R.dimen.button_min_width), minHeight = dimensionResource(id = R.dimen.button_height)),
            enabled = checkedState.value
        )
        TertiaryButton(
            onClick = onExportAsPdf,
            text = stringResource(id = R.string.ns_restore_from_backup).uppercase(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = dimensionResource(id = R.dimen.button_height))
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_bottom_margin)))
    }
}

@Composable
internal fun SeedItem(seedIndex: Int, seedWord: String) {
    Row {
        BodyMedium(text = "$seedIndex.", color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet))
        BodyMedium(text = " $seedWord")
    }
}
