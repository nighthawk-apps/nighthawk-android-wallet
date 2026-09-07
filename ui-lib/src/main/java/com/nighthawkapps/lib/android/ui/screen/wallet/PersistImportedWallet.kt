package com.nighthawkapps.lib.android.ui.screen.wallet

import android.app.Application
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.sdk.wallet.darkfiNetworkFromPackage
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel

internal fun WalletViewModel.persistImportedWallet(
    seedWords: List<String>,
    birthdayHeight: Long?
) {
    val application = getApplication<Application>()
    val network = darkfiNetworkFromPackage(application)
    persistExistingWallet(
        PersistableDarkfiWallet(
            seedPhrase = seedWords,
            network = network,
            endpoint = DarkfiEndpoint.defaultForNetwork(network),
            birthdayHeight = birthdayHeight,
        ),
    )
}
