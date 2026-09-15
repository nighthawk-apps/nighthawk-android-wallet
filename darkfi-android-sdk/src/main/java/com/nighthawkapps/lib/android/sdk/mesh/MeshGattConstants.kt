package com.nighthawkapps.lib.android.sdk.mesh

import java.util.UUID

/** Nighthawk-only GATT IDs. Must not match BitChat `F47B5E2D-…`. */
object MeshGattConstants {
    val SERVICE_UUID: UUID = UUID.fromString("6e686d73-0001-4000-a000-4e6967687468")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("6e686d73-0002-4000-a000-4e6967687468")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEFAULT_FRAGMENT_BYTES: Int = 469
    const val MAX_CONNECTIONS: Int = 8
    const val REQUEST_MTU: Int = 517
}
