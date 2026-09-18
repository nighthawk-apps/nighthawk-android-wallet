package com.nighthawkapps.lib.android.sdk.mesh

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose `LocalContext` is often a [ContextWrapper], not the Activity itself. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
