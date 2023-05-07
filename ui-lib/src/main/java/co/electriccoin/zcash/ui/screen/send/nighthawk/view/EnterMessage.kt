package co.electriccoin.zcash.ui.screen.send.nighthawk.view

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TitleLarge
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Composable
@Preview
fun EnterMessagePreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            EnterMessage(
                onBack = { },
                onContinue = { }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterMessage(
    onBack: () -> Unit,
    onContinue: (String) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(dimensionResource(id = R.dimen.screen_standard_margin))
        .verticalScroll(rememberScrollState())
    ) {
        val message = remember {
            mutableStateOf("")
        }
        val continueBtnText = remember {
            derivedStateOf {
                if (message.value.isBlank()) R.string.ns_skip else R.string.ns_continue
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))) {
            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.receive_back_content_description))
        }
        Image(painter = painterResource(id = R.drawable.ic_nighthawk_logo), contentDescription = "logo", contentScale = ContentScale.Inside, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(text = stringResource(id = R.string.ns_nighthawk), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        BodyMedium(text = stringResource(id = R.string.ns_add_message), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally), color = ZcashTheme.colors.surfaceEnd)
        Spacer(modifier = Modifier.height(45.dp))
        OutlinedTextField(
            value = message.value,
            onValueChange = { message.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 105.dp),
            placeholder = {
                BodyMedium(text = stringResource(id = R.string.ns_write_something), color = ZcashTheme.colors.surfaceEnd)
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White
            ),
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(40.dp))
        PrimaryButton(
            onClick = { onContinue(message.value) },
            text = stringResource(id = continueBtnText.value).uppercase(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .sizeIn(minWidth = dimensionResource(id = R.dimen.button_min_width), minHeight = dimensionResource(id = R.dimen.button_height))
        )
    }
}
