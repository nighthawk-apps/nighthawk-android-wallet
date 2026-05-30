@file:Suppress("TooManyFunctions", "LongParameterList")

package com.nighthawkapps.lib.android.ui.screen.restore.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiChainBounds
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.spackle.model.Index
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.design.MINIMAL_WEIGHT
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.CHIP_GRID_ROW_SIZE
import com.nighthawkapps.lib.android.ui.design.component.Chip
import com.nighthawkapps.lib.android.ui.design.component.FormTextField
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.Header
import com.nighthawkapps.lib.android.ui.design.component.NavigationButton
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TertiaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme.dimens
import com.nighthawkapps.lib.android.ui.screen.restore.RestoreTag
import com.nighthawkapps.lib.android.ui.screen.restore.model.ParseResult
import com.nighthawkapps.lib.android.ui.screen.restore.model.RestoreStage
import com.nighthawkapps.lib.android.ui.screen.restore.model.SeedPhraseValidation
import com.nighthawkapps.lib.android.ui.screen.restore.state.RestoreState
import com.nighthawkapps.lib.android.ui.screen.restore.state.WordList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Preview("Restore Wallet")
@Composable
private fun PreviewRestore() {
    WalletTheme(darkTheme = true) {
        GradientSurface {
            RestoreWallet(
                DarkfiNetwork.Testnet,
                restoreState = RestoreState(RestoreStage.Seed),
                completeWordList =
                    persistentHashSetOf(
                        "abandon",
                        "ability",
                        "able",
                        "about",
                        "above",
                        "absent",
                        "absorb",
                        "abstract",
                        "rib",
                        "ribbon"
                    ),
                userWordList = WordList(listOf("abandon", "absorb")),
                restoreHeight = null,
                setRestoreHeight = {},
                seedPhraseValidation = remember { MutableStateFlow<SeedPhraseValidation?>(null) },
                onBack = {},
                paste = { "" },
                onFinished = {}
            )
        }
    }
}

@Preview("Restore Complete")
@Composable
private fun PreviewRestoreComplete() {
    WalletTheme(darkTheme = true) {
        RestoreComplete(
            onComplete = {}
        )
    }
}

/**
 * Note that the restore review doesn't allow the user to go back once the seed is entered correctly.
 *
 * @param restoreHeight A null height indicates no user input.
 */
@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RestoreWallet(
    darkfiNetwork: DarkfiNetwork,
    restoreState: RestoreState,
    completeWordList: ImmutableSet<String>,
    userWordList: WordList,
    restoreHeight: Long?,
    setRestoreHeight: (Long?) -> Unit,
    seedPhraseValidation: StateFlow<SeedPhraseValidation?>,
    onBack: () -> Unit,
    paste: () -> String?,
    onFinished: () -> Unit
) {
    var textState by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val parseResult = ParseResult.new(completeWordList, textState)

    val currentStage = restoreState.current.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            RestoreTopAppBar(
                onBack = {
                    if (currentStage.hasPrevious()) {
                        restoreState.goPrevious()
                    } else {
                        onBack()
                    }
                },
                isShowClear = currentStage == RestoreStage.Seed,
                onClear = { userWordList.set(emptyList()) }
            )
        },
        bottomBar = {
            when (currentStage) {
                RestoreStage.Seed -> {
                    RestoreSeedBottomBar(
                        userWordList = userWordList,
                        parseResult = parseResult,
                        setTextState = { textState = it },
                        focusRequester = focusRequester,
                        seedPhraseValidation = seedPhraseValidation,
                    )
                }

                RestoreStage.Birthday -> {
                    // No content
                }

                RestoreStage.Complete -> {
                    // No content
                }
            }
        },
        content = { paddingValues ->
            val commonModifier =
                Modifier
                    // We intentionally set the bottom smaller to save space in case of the software keyboard is visible
                    .padding(
                        top = paddingValues.calculateTopPadding() + dimens.spacingDefault,
                        bottom = paddingValues.calculateBottomPadding() + dimens.spacingSmall,
                        start = dimens.spacingDefault,
                        end = dimens.spacingDefault
                    )

            when (currentStage) {
                RestoreStage.Seed -> {
                    SecureScreen()

                    RestoreSeedMainContent(
                        userWordList = userWordList,
                        textState = textState,
                        setTextState = { textState = it },
                        focusRequester = focusRequester,
                        parseResult = parseResult,
                        paste = paste,
                        goNext = { restoreState.goNext() },
                        seedPhraseValidation = seedPhraseValidation,
                        modifier = commonModifier
                    )
                }

                RestoreStage.Birthday -> {
                    RestoreBirthday(
                        darkfiNetwork = darkfiNetwork,
                        initialRestoreHeight = restoreHeight,
                        setRestoreHeight = setRestoreHeight,
                        onNext = { restoreState.goNext() },
                        modifier = commonModifier
                    )
                }

                RestoreStage.Complete -> {
                    // In some cases we need to hide the software keyboard manually, as it stays shown after
                    // input on prior screens
                    LocalSoftwareKeyboardController.current?.hide()

                    RestoreComplete(
                        onComplete = onFinished,
                        modifier = commonModifier
                    )
                }
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RestoreTopAppBar(
    onBack: () -> Unit,
    isShowClear: Boolean,
    onClear: () -> Unit
) {
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.restore_title)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.restore_back_content_description)
                )
            }
        },
        actions = {
            if (isShowClear) {
                NavigationButton(onClick = onClear, stringResource(R.string.restore_button_clear))
            }
        }
    )
}

// TODO [#672]: Implement custom seed phrase pasting for wallet import
// TODO [#672]:

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("UNUSED_PARAMETER", "LongParameterList")
@Composable
private fun RestoreSeedMainContent(
    userWordList: WordList,
    textState: String,
    setTextState: (String) -> Unit,
    focusRequester: FocusRequester,
    parseResult: ParseResult,
    paste: () -> String?,
    goNext: () -> Unit,
    seedPhraseValidation: StateFlow<SeedPhraseValidation?>,
    modifier: Modifier = Modifier
) {
    val currentUserWordList = userWordList.current.collectAsStateWithLifecycle().value
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    if (parseResult is ParseResult.Add) {
        setTextState("")
        userWordList.append(parseResult.words)
    }

    val isSeedValid =
        seedPhraseValidation.collectAsStateWithLifecycle(null).value is SeedPhraseValidation.Valid

    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .then(modifier)
    ) {
        Body(text = stringResource(id = R.string.restore_seed_instructions))

        Spacer(Modifier.height(dimens.spacingSmall))

        ChipGridWithText(currentUserWordList)

        if (!isSeedValid) {
            NextWordTextField(
                parseResult = parseResult,
                text = textState,
                setText = { setTextState(it) },
                modifier = Modifier.focusRequester(focusRequester)
            )
        }

        Spacer(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(MINIMAL_WEIGHT)
        )

        PrimaryButton(
            onClick = goNext,
            text = stringResource(id = R.string.restore_seed_button_restore),
            enabled = isSeedValid,
            outerPaddingValues = PaddingValues(top = dimens.spacingSmall)
        )
    }

    if (isSeedValid) {
        // Hides the keyboard, making it easier for users to see the next button
        LocalSoftwareKeyboardController.current?.hide()
    }

    // Cause text field to refocus
    DisposableEffect(parseResult) {
        if (!isSeedValid) {
            focusRequester.requestFocus()
        }
        scope.launch {
            scrollState.scrollTo(scrollState.maxValue)
        }
        onDispose { }
    }
}

@Suppress("LongParameterList")
@Composable
private fun RestoreSeedBottomBar(
    userWordList: WordList,
    parseResult: ParseResult,
    setTextState: (String) -> Unit,
    focusRequester: FocusRequester,
    seedPhraseValidation: StateFlow<SeedPhraseValidation?>,
    modifier: Modifier = Modifier
) {
    val isSeedValid =
        seedPhraseValidation.collectAsStateWithLifecycle(null).value is SeedPhraseValidation.Valid
    // Hide the field once the user has completed the seed phrase; if they need the field back then
    // the user can hit the clear button
    if (!isSeedValid) {
        Column(modifier) {
            Warn(
                parseResult = parseResult,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Note we don't set the top, as it's set by the confirm button above
                        .padding(
                            bottom = dimens.spacingDefault,
                            start = dimens.spacingDefault,
                            end = dimens.spacingDefault
                        )
            )
            Autocomplete(parseResult = parseResult, {
                setTextState("")
                userWordList.append(listOf(it))
                focusRequester.requestFocus()
            })
        }
    }
}

@Composable
private fun ChipGridWithText(userWordList: ImmutableList<String>) {
    Column(Modifier.testTag(RestoreTag.CHIP_LAYOUT)) {
        userWordList.chunked(CHIP_GRID_ROW_SIZE).forEachIndexed { chunkIndex, chunk ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                val remainder = (chunk.size % CHIP_GRID_ROW_SIZE)

                val singleItemWeight = 1f / CHIP_GRID_ROW_SIZE
                chunk.forEachIndexed { subIndex, word ->
                    Chip(
                        index = Index(chunkIndex * CHIP_GRID_ROW_SIZE + subIndex),
                        text = word,
                        modifier = Modifier.weight(singleItemWeight)
                    )
                }

                if (0 != remainder) {
                    Spacer(modifier = Modifier.weight((CHIP_GRID_ROW_SIZE - chunk.size) * singleItemWeight))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NextWordTextField(
    parseResult: ParseResult,
    text: String,
    setText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(dimens.spacingTiny),
        shape = RoundedCornerShape(WalletTheme.dimens.spacingSmall),
        color = MaterialTheme.colorScheme.secondary,
        shadowElevation = WalletTheme.dimens.spacingSmall
    ) {
        /*
         * Treat the user input as a password for more secure input, but disable the transformation
         * to obscure typing.
         */
        TextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dimens.spacingTiny)
                    .testTag(RestoreTag.SEED_WORD_TEXT_FIELD),
            value = text,
            onValueChange = setText,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
            keyboardActions = KeyboardActions(onAny = {}),
            shape = RoundedCornerShape(WalletTheme.dimens.spacingSmall),
            isError = parseResult is ParseResult.Warn,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { setText("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.restore_button_clear)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun Autocomplete(
    parseResult: ParseResult,
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (isHighlight, suggestions) =
        when (parseResult) {
            is ParseResult.Autocomplete -> {
                Pair(false, parseResult.suggestions)
            }

            is ParseResult.Warn -> {
                return
            }

            else -> {
                Pair(false, null)
            }
        }
    suggestions?.let {
        val highlightModifier =
            if (isHighlight) {
                modifier.border(WalletTheme.dimens.spacingXtiny, WalletTheme.colors.highlight)
            } else {
                modifier
            }

        @Suppress("ModifierReused")
        LazyRow(
            modifier = highlightModifier.testTag(RestoreTag.AUTOCOMPLETE_LAYOUT),
            // Note we don't set the top, as it's set by the confirm button above
            // And we also set the bottom smaller, as the keyboard will be always visible
            contentPadding =
                PaddingValues(
                    bottom = dimens.spacingDefault,
                    start = dimens.spacingDefault,
                    end = dimens.spacingSmall
                )
        ) {
            items(it) {
                Chip(
                    text = it,
                    modifier =
                        Modifier
                            .testTag(RestoreTag.AUTOCOMPLETE_ITEM)
                            .clickable { onSuggestionSelected(it) }
                )
            }
        }
    }
}

@Composable
private fun Warn(
    parseResult: ParseResult,
    modifier: Modifier = Modifier
) {
    if (parseResult is ParseResult.Warn) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(WalletTheme.dimens.spacingSmall),
            color = MaterialTheme.colorScheme.secondary,
            shadowElevation = WalletTheme.dimens.spacingTiny
        ) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(dimens.spacingTiny),
                textAlign = TextAlign.Center,
                text =
                    if (parseResult.suggestions.isEmpty()) {
                        stringResource(id = R.string.restore_seed_warning_no_suggestions)
                    } else {
                        stringResource(id = R.string.restore_seed_warning_suggestions)
                    }
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER", "LongMethod")
@Composable
private fun RestoreBirthday(
    darkfiNetwork: DarkfiNetwork,
    initialRestoreHeight: Long?,
    setRestoreHeight: (Long?) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (height, setHeight) =
        rememberSaveable {
            mutableStateOf(initialRestoreHeight?.toString() ?: "")
        }

    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .then(modifier)
    ) {
        Header(stringResource(R.string.restore_birthday_header))

        Spacer(modifier = Modifier.height(dimens.spacingDefault))

        Body(stringResource(R.string.restore_birthday_body))

        Spacer(modifier = Modifier.height(dimens.spacingDefault))

        FormTextField(
            value = height,
            onValueChange = { heightString ->
                val filteredHeightString = heightString.filter { it.isDigit() }
                setHeight(filteredHeightString)
            },
            Modifier
                .fillMaxWidth()
                .padding(dimens.spacingTiny)
                .testTag(RestoreTag.BIRTHDAY_TEXT_FIELD),
            label = { Text(stringResource(id = R.string.restore_birthday_hint)) },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
            keyboardActions = KeyboardActions(onAny = {}),
            shape = RoundedCornerShape(WalletTheme.dimens.spacingSmall),
        )

        Spacer(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(MINIMAL_WEIGHT)
        )

        val isBirthdayValid =
            height.toLongOrNull()?.let {
                it >= DarkfiChainBounds.MIN_WALLET_BIRTHDAY_HEIGHT
            } ?: false

        PrimaryButton(
            onClick = {
                setRestoreHeight(height.toLongOrNull())
                onNext()
            },
            text = stringResource(R.string.restore_birthday_button_restore),
            enabled = isBirthdayValid,
            outerPaddingValues = PaddingValues(top = dimens.spacingSmall)
        )

        TertiaryButton(
            onClick = {
                setRestoreHeight(null)
                onNext()
            },
            text = stringResource(R.string.restore_birthday_button_skip),
            outerPaddingValues = PaddingValues(top = dimens.spacingSmall)
        )
    }
}

@Composable
private fun RestoreComplete(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .then(modifier)
    ) {
        Header(stringResource(R.string.restore_complete_header))

        Spacer(modifier = Modifier.height(dimens.spacingDefault))

        Body(stringResource(R.string.restore_complete_info))

        Spacer(modifier = Modifier.height(dimens.spacingDefault))

        Spacer(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(MINIMAL_WEIGHT)
        )

        PrimaryButton(
            onClick = onComplete,
            text = stringResource(R.string.restore_button_see_wallet),
            outerPaddingValues = PaddingValues(top = dimens.spacingSmall)
        )
    }
}
