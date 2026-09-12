package com.nighthawkapps.lib.android.configuration.api

import com.nighthawkapps.lib.android.configuration.model.map.Configuration
import com.nighthawkapps.lib.android.configuration.model.map.StringConfiguration
import com.nighthawkapps.lib.android.configuration.test.fixture.BooleanDefaultEntryFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MergingConfigurationProviderTest {
    @Test
    fun peek_ordering() {
        val configurationProvider =
            MergingConfigurationProvider(
                persistentListOf(
                    MockConfigurationProvider(
                        StringConfiguration(persistentMapOf(BooleanDefaultEntryFixture.KEY.key to true.toString()), null)
                    ),
                    MockConfigurationProvider(
                        StringConfiguration(persistentMapOf(BooleanDefaultEntryFixture.KEY.key to false.toString()), null)
                    )
                )
            )

        assertTrue(BooleanDefaultEntryFixture.newTrueEntry().getValue(configurationProvider.peekConfiguration()))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFlow_ordering() =
        runTest {
            val configurationProvider =
                MergingConfigurationProvider(
                    persistentListOf(
                        MockConfigurationProvider(
                            StringConfiguration(persistentMapOf(BooleanDefaultEntryFixture.KEY.key to true.toString()), null)
                        ),
                        MockConfigurationProvider(
                            StringConfiguration(persistentMapOf(BooleanDefaultEntryFixture.KEY.key to false.toString()), null)
                        )
                    )
                )

            assertTrue(
                BooleanDefaultEntryFixture.newTrueEntry().getValue(configurationProvider.getConfigurationFlow().first())
            )
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFlow_empty() =
        runTest {
            val configurationProvider =
                MergingConfigurationProvider(
                    emptyList<ConfigurationProvider>().toPersistentList()
                )

            val firstMergedConfiguration = configurationProvider.getConfigurationFlow().first()

            assertTrue(BooleanDefaultEntryFixture.newTrueEntry().getValue(firstMergedConfiguration))
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getUpdatedAt_newest() =
        runTest {
            val older = Instant.parse("2023-01-15T08:38:45.415Z")
            val newer = Instant.parse("2023-01-17T08:38:45.415Z")

            val configurationProvider =
                MergingConfigurationProvider(
                    persistentListOf(
                        MockConfigurationProvider(
                            StringConfiguration(persistentMapOf(BooleanDefaultEntryFixture.KEY.key to true.toString()), older)
                        ),
                        MockConfigurationProvider(
                            StringConfiguration(persistentMapOf(BooleanDefaultEntryFixture.KEY.key to false.toString()), newer)
                        )
                    )
                )

            val updatedAt = configurationProvider.getConfigurationFlow().first().updatedAt
            assertEquals(newer, updatedAt)
        }
}

private class MockConfigurationProvider(
    private val configuration: Configuration
) : ConfigurationProvider {
    override fun peekConfiguration(): Configuration = configuration

    override fun getConfigurationFlow(): Flow<Configuration> = flowOf(configuration)

    override fun hintToRefresh() {
        // no-op
    }
}
