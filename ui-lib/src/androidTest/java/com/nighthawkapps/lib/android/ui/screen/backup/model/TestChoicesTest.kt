package com.nighthawkapps.lib.android.ui.screen.backup.model

import androidx.compose.runtime.saveable.SaverScope
import androidx.test.filters.SmallTest
import com.nighthawkapps.lib.android.spackle.model.Index
import com.nighthawkapps.lib.android.ui.fixture.TestChoicesFixture
import com.nighthawkapps.lib.android.ui.screen.backup.ext.Saver
import com.nighthawkapps.lib.android.ui.screen.backup.state.TestChoices
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestChoicesTest {
    @Test
    @SmallTest
    fun save_restore_comparison() {
        val original = TestChoicesFixture.new(TestChoicesFixture.INITIAL_CHOICES)
        val saved =
            with(TestChoices.Saver) {
                val allowingScope = SaverScope { true }
                allowingScope.save(original)
            }

        val restored = TestChoices.Saver.restore(saved!!)

        assertNotNull(restored)
        assertTrue(restored.current.value.isNotEmpty())
        assertEquals(restored.current.value.size, original.current.value.size)
        assertEquals(restored.current.value[Index(0)], original.current.value[Index(0)])
        assertEquals(restored.current.value[Index(3)], original.current.value[Index(3)])
    }

    @Test
    @SmallTest
    fun restore_empty() {
        val restored = TestChoices.Saver.restore(emptyList<Any?>())
        assertNotNull(restored)
        assertEquals(restored.current.value.size, 0)
    }
}
