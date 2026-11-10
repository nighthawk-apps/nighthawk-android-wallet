package com.nighthawkapps.lib.android.sdk.mesh

/**
 * One mesh identity can show up on a new Bluetooth address while the old
 * connection is still up. Keep the address that just spoke. Cancelling the
 * new one and keeping the stale address reconnects in a loop.
 */
object MeshRedundantLink {
    fun radioToRetire(
        boundRadio: String?,
        speakingRadio: String,
    ): String? = if (boundRadio != null && boundRadio != speakingRadio) boundRadio else null
}
