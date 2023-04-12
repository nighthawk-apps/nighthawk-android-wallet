package co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.sdk.fixture.SeedPhraseFixture
import co.electriccoin.zcash.global.PdfUtil
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TertiaryButton
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.pdf.EncryptedPdfDialog
import kotlinx.collections.immutable.toPersistentList

@Preview
@Composable
fun SeedFeedBackPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            SeedBackupContent(
                seedPhrase = SeedPhraseFixture.new(),
                birthday = null,
                onContinue = {}
            )
        }
    }
}

@Composable
internal fun SeedBackup(persistableWallet: PersistableWallet, onBackupComplete: () -> Unit) {
    SeedBackupContent(persistableWallet.seedPhrase, persistableWallet.birthday, onContinue = onBackupComplete)
}

@Composable
fun SeedBackupContent(
    seedPhrase: SeedPhrase,
    birthday: BlockHeight?,
    onContinue: () -> Unit
) {
    val showEncryptedPdfDialog = remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.screen_standard_margin))
        ) {
            val checkedState = remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.back_icon_size)))
            Image(
                painter = painterResource(id = R.drawable.ic_nighthawk_logo),
                contentDescription = "logo", contentScale = ContentScale.Inside,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
            BodyMedium(text = stringResource(id = R.string.ns_create_wallet_title), color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet))
            Spacer(modifier = Modifier.height(11.dp))
            BodySmall(text = stringResource(id = R.string.ns_create_wallet_text))
            Spacer(modifier = Modifier.height(25.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(seedPhrase.split.toPersistentList()) { index: Int, word: String ->
                    SeedItem(seedIndex = index + 1, seedWord = word)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            BodyMedium(text = stringResource(id = R.string.ns_wallet_birthday, "${birthday?.value ?: ""}"))
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
                onClick = {
                    showEncryptedPdfDialog.value = true
                },
                text = stringResource(id = R.string.ns_export_as_pdf).uppercase(),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = dimensionResource(id = R.dimen.button_height))
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_bottom_margin)))
        }

        if (showEncryptedPdfDialog.value) {
            val context = LocalContext.current
            Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
                EncryptedPdfDialog(
                    onDismissRequest = {
                        showEncryptedPdfDialog.value = false
                    },
                    onExportPdf = {
                        PdfUtil.exportPasswordProtectedPdf(
                            context = context,
                            password = it,
                            seedWords = seedPhrase.split,
                            birthDay = birthday?.value
                        )
                        showEncryptedPdfDialog.value = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun SeedItem(seedIndex: Int, seedWord: String) {
    Row {
        BodyMedium(text = "$seedIndex.", color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet))
        BodyMedium(text = " $seedWord")
    }
}
