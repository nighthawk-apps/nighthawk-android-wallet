@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.common

fun <T> List<T>.first(count: Int) = subList(0, minOf(size, count))
