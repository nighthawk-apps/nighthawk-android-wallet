package co.electriccoin.zcash.ui.common

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity

internal fun ComponentActivity.onLaunchUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        print("Warning: failed to open browser due to $t")
    }
}