package com.nighthawkapps.lib.android.ui.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines nine flows into a single flow using the provided transform function.
 * Kotlin's standard [combine] only supports up to 5 flows, so this nests
 * two combines.
 */
@Suppress("LongParameterList")
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combineNine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    transform: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R,
): Flow<R> {
    data class PartA<T1, T2, T3, T4, T5>(
        val v1: T1,
        val v2: T2,
        val v3: T3,
        val v4: T4,
        val v5: T5,
    )

    data class PartB<T6, T7, T8, T9>(
        val v6: T6,
        val v7: T7,
        val v8: T8,
        val v9: T9,
    )
    return combine(
        combine(flow1, flow2, flow3, flow4, flow5) { a, b, c, d, e ->
            PartA(a, b, c, d, e)
        },
        combine(flow6, flow7, flow8, flow9) { f, g, h, i ->
            PartB(f, g, h, i)
        },
    ) { partA, partB ->
        transform(
            partA.v1,
            partA.v2,
            partA.v3,
            partA.v4,
            partA.v5,
            partB.v6,
            partB.v7,
            partB.v8,
            partB.v9,
        )
    }
}
