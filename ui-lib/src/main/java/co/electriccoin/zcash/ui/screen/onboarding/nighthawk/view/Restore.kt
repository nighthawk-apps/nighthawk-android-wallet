package co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TitleLarge
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview
@Composable
fun RestorePreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            Restore()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Restore() {
    val scrollState = rememberScrollState()
    var seeds by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    val isSeedValid by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.screen_standard_margin))
            .verticalScroll(scrollState)
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description)
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = "logo", contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(dimensionResource(id = R.dimen.top_margin_back_btn)))
        TitleLarge(text = stringResource(id = R.string.ns_nighthawk), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
        TitleMedium(text = stringResource(id = R.string.ns_restore_from_backup), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.text_margin)))
        BodyMedium(
            text = stringResource(id = R.string.ns_restore_wallet_text),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = seeds, onValueChange = {
            seeds = it
        },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 109.dp),
            label = {
                BodyMedium(text = stringResource(id = R.string.ns_your_seed_phrase))
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = if (isSeedValid) MaterialTheme.colorScheme.primary else Color.White,
                unfocusedBorderColor = if (isSeedValid) MaterialTheme.colorScheme.primary else Color.White,
            )
        )
        Spacer(modifier = Modifier.size(21.dp))
        OutlinedTextField(value = birthday, onValueChange = {
            birthday = it
        },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            label = {
                BodyMedium(text = stringResource(id = R.string.ns_birthday_height))
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.White,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(29.dp))
        PrimaryButton(
            onClick = {},
            text = stringResource(id = R.string.ns_continue).uppercase(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .sizeIn(minWidth = dimensionResource(id = R.dimen.button_min_width), minHeight = dimensionResource(id = R.dimen.button_height)),
            enabled = isSeedValid
        )
    }
}
