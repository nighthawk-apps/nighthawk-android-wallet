package com.nighthawkapps.lib.android.ui.screen.dao.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.nighthawkapps.lib.android.ui.R

@Composable
internal fun DaoField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    numeric: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun DaoAmountField(
    value: String,
    onValue: (String) -> Unit,
) {
    DaoField(value, onValue, stringResource(R.string.ns_dao_amount), true)
}
