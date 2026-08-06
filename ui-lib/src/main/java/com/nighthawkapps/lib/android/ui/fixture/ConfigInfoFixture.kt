package com.nighthawkapps.lib.android.ui.fixture

import com.nighthawkapps.lib.android.ui.screen.support.model.ConfigInfo
import kotlin.time.Instant

// Magic Number doesn't matter here for hard-coded fixture values
@Suppress("MagicNumber")
object ConfigInfoFixture {
    val UPDATED_AT = Instant.parse("2023-01-15T08:38:45.415Z")

    fun new(updatedAt: Instant? = UPDATED_AT,) = ConfigInfo(updatedAt)
}
