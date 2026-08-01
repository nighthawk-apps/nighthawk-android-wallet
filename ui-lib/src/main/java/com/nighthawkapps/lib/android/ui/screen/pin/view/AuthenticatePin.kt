package com.nighthawkapps.lib.android.ui.screen.pin.view

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.WALLET_PASSWORD_LENGTH
import com.nighthawkapps.lib.android.ui.common.WRONG_VIBRATION_PATTERN
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.model.NumberPadValueTypes
import com.nighthawkapps.lib.android.ui.security.WalletPinSecureStore
import kotlinx.coroutines.delay

@Preview
@Composable
fun AuthenticatePinPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            AuthenticatePin(
                verifyPin = { pin ->
                    if (pin == "111111") {
                        WalletPinSecureStore.PinVerifyResult.Success
                    } else {
                        WalletPinSecureStore.PinVerifyResult.WrongPin
                    }
                },
                onBack = {},
                onAuthentication = {},
            )
        }
    }
}

@Composable
fun AuthenticatePin(
    verifyPin: suspend (String) -> WalletPinSecureStore.PinVerifyResult,
    onBack: () -> Unit,
    onAuthentication: (Boolean) -> Unit,
    @StringRes pinPromptResId: Int = R.string.enter_six_digit_pin_code,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.screen_standard_margin)),
    ) {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    LocalContext.current.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                LocalContext.current.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

        val enteredPassword = remember { mutableStateOf("") }
        var lockoutSeconds by remember { mutableIntStateOf(0) }
        var verifying by remember { mutableStateOf(false) }
        var showWrongPin by remember { mutableStateOf(false) }

        val isFullPasswordEntered =
            remember {
                derivedStateOf {
                    enteredPassword.value.length == WALLET_PASSWORD_LENGTH
                }
            }
        val incorrectPassword =
            remember {
                derivedStateOf {
                    showWrongPin && !verifying && lockoutSeconds == 0
                }
            }

        LaunchedEffect(isFullPasswordEntered.value, enteredPassword.value) {
            if (!isFullPasswordEntered.value || verifying || lockoutSeconds > 0) return@LaunchedEffect
            verifying = true
            showWrongPin = false
            when (val result = verifyPin(enteredPassword.value)) {
                WalletPinSecureStore.PinVerifyResult.Success -> {
                    onAuthentication(true)
                }

                WalletPinSecureStore.PinVerifyResult.WrongPin -> {
                    showWrongPin = true
                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                WRONG_VIBRATION_PATTERN,
                                -1,
                            ),
                        )
                    }
                    delay(1000)
                    enteredPassword.value = ""
                    showWrongPin = false
                }

                is WalletPinSecureStore.PinVerifyResult.LockedOut -> {
                    lockoutSeconds = result.remainingSeconds.toInt()
                    enteredPassword.value = ""
                }
            }
            verifying = false
        }

        LaunchedEffect(lockoutSeconds) {
            if (lockoutSeconds <= 0) return@LaunchedEffect
            delay(1000)
            lockoutSeconds -= 1
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description),
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = stringResource(id = R.string.ns_logo_desc),
            contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(
            text = stringResource(id = R.string.ns_nighthawk),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(35.dp))

        if (lockoutSeconds > 0) {
            Text(
                text = stringResource(R.string.ns_security_pin_locked, lockoutSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        EnterPinCommonUI(
            incorrectPassword = incorrectPassword.value,
            enteredPassword = enteredPassword.value,
            message =
                stringResource(
                    id =
                        when {
                            lockoutSeconds > 0 -> R.string.ns_security_pin_locked_short
                            incorrectPassword.value -> R.string.ns_security_try_again
                            else -> pinPromptResId
                        },
                ),
            onKeyPressed = {
                if (lockoutSeconds > 0 || verifying) return@EnterPinCommonUI
                if (it is NumberPadValueTypes.BackSpace) {
                    if (enteredPassword.value.isNotBlank()) {
                        enteredPassword.value = enteredPassword.value.dropLast(1)
                    }
                } else {
                    if (enteredPassword.value.length < WALLET_PASSWORD_LENGTH) {
                        enteredPassword.value += it.keyValue
                    }
                }
            },
        )
    }
}
