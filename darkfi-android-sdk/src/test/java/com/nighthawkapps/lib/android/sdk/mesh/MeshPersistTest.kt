package com.nighthawkapps.lib.android.sdk.mesh

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MeshPersistTest {
    @Test
    fun enabledAndAlwaysOnRoundTrip() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        MeshPersist.setEnabled(ctx, true)
        MeshPersist.setAlwaysOn(ctx, false)
        assertTrue(MeshPersist.isEnabled(ctx))
        assertFalse(MeshPersist.alwaysOn(ctx))
        MeshPersist.setEnabled(ctx, false)
        MeshPersist.setAlwaysOn(ctx, true)
        assertFalse(MeshPersist.isEnabled(ctx))
        assertTrue(MeshPersist.alwaysOn(ctx))
    }

    @Test
    fun findActivityUnwrapsThemeWrapper() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped = ContextWrapper(activity)
        assertSame(activity, wrapped.findActivity())
        val app: Context = ApplicationProvider.getApplicationContext()
        assertTrue(app.findActivity() == null)
    }

    @Test
    fun bootStartRefusesWhenDisabled() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        MeshPersist.setEnabled(ctx, false)
        MeshPersist.setAlwaysOn(ctx, true)
        assertFalse(MeshCoordinator.startFromBootIfEnabled(ctx))
    }
}
