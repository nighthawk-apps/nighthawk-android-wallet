package com.nighthawkapps.lib.android.ui.screen.debug.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nighthawkapps.lib.android.spackle.model.Index
import com.nighthawkapps.lib.android.spackle.model.Progress
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.Chip
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.Header
import com.nighthawkapps.lib.android.ui.design.component.NavigationButton
import com.nighthawkapps.lib.android.ui.design.component.PinkProgress
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.SecondaryButton
import com.nighthawkapps.lib.android.ui.design.component.TertiaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview("DesignGuide")
@Composable
private fun ComposablePreview() {
    WalletTheme(darkTheme = false) {
        DesignGuide()
    }
}

@Composable
// Allowing magic numbers since this is debug-only
@Suppress("MagicNumber")
fun DesignGuide() {
    GradientSurface {
        Column {
            Header(text = "H1")
            Body(text = "body")
            NavigationButton(onClick = { }, text = "Back")
            NavigationButton(onClick = { }, text = "Next")
            PrimaryButton(onClick = { }, text = "Primary button")
            SecondaryButton(onClick = { }, text = "Secondary button")
            TertiaryButton(onClick = { }, text = "Tertiary button")
            PinkProgress(progress = Progress(Index(1), Index(4)), Modifier.fillMaxWidth())
            Chip(Index(1), "edict")
        }
    }
}
