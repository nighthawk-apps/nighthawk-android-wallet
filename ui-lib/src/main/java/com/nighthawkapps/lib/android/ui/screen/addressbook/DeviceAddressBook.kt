package com.nighthawkapps.lib.android.ui.screen.addressbook

import android.content.Context
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceSingleton
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys
import org.json.JSONArray
import org.json.JSONObject

/** Device-only labels for DarkFi addresses. No ENS, no network lookup. */
data class AddressBookEntry(
    val label: String,
    val address: String,
)

object DeviceAddressBook {
    suspend fun load(context: Context): List<AddressBookEntry> {
        val prefs = EncryptedPreferenceSingleton.getInstance(context.applicationContext)
        val raw = EncryptedPreferenceKeys.ADDRESS_BOOK_JSON.getValue(prefs)
        return parse(raw)
    }

    suspend fun save(
        context: Context,
        entries: List<AddressBookEntry>,
    ) {
        val prefs = EncryptedPreferenceSingleton.getInstance(context.applicationContext)
        EncryptedPreferenceKeys.ADDRESS_BOOK_JSON.putValue(prefs, serialize(entries))
    }

    suspend fun upsert(
        context: Context,
        label: String,
        address: String,
    ) {
        val trimmedLabel = label.trim().take(64)
        val trimmedAddress = address.trim()
        if (trimmedLabel.isEmpty() || trimmedAddress.isEmpty()) return
        val next =
            load(context)
                .filterNot { it.address.equals(trimmedAddress, ignoreCase = true) }
                .toMutableList()
        next.add(0, AddressBookEntry(trimmedLabel, trimmedAddress))
        save(context, next.take(50))
    }

    private fun parse(raw: String): List<AddressBookEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val label = obj.optString("label").trim()
                    val address = obj.optString("address").trim()
                    if (label.isNotEmpty() && address.isNotEmpty()) {
                        add(AddressBookEntry(label, address))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serialize(entries: List<AddressBookEntry>): String {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject()
                    .put("label", entry.label)
                    .put("address", entry.address),
            )
        }
        return arr.toString()
    }
}
