package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Keystore-backed contact book. AES via [DarkfiChatSecureStore].
 * DM secrets are copied from the existing DM key entry so one record holds
 * nickname, DM key, and wallet address.
 */
object NighthawkContactStore {
    private const val KEY = "nighthawk_contacts_v1"
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Serializable
    private data class File(
        val contacts: List<Record> = emptyList(),
    )

    @Serializable
    private data class Record(
        val id: String,
        val nickname: String,
        val dmPublicKey: String,
        val myDmSecret: String? = null,
        val walletAddress: String? = null,
    )

    fun load(context: Context): List<NighthawkContact> = decode(context).contacts.map { it.toContact() }

    fun findByNick(
        context: Context,
        nick: String,
    ): NighthawkContact? {
        val key = nick.trim().lowercase()
        return load(context).firstOrNull { it.nickname.trim().lowercase() == key || it.id == key }
    }

    fun upsert(
        context: Context,
        contact: NighthawkContact,
        myDmSecret: String? = null,
    ) {
        val current = decode(context).contacts.toMutableList()
        val id = contact.id.ifBlank { contact.nickname.trim().lowercase() }
        val idx = current.indexOfFirst { it.id == id || it.nickname.equals(contact.nickname, ignoreCase = true) }
        val previous = current.getOrNull(idx)
        val record =
            Record(
                id = id,
                nickname = contact.nickname.trim(),
                dmPublicKey = contact.dmPublicKey.trim(),
                myDmSecret = myDmSecret ?: previous?.myDmSecret,
                walletAddress = contact.walletAddress?.trim()?.takeIf { it.isNotEmpty() } ?: previous?.walletAddress,
            )
        if (idx >= 0) current[idx] = record else current.add(0, record)
        write(context, File(current.take(200)))
    }

    fun remove(
        context: Context,
        nick: String,
    ) {
        val key = nick.trim()
        val next =
            decode(context).contacts.filterNot {
                it.nickname.equals(key, ignoreCase = true) || it.id.equals(key, ignoreCase = true)
            }
        write(context, File(next))
    }

    private fun decode(context: Context): File {
        val raw = DarkfiChatSecureStore.getString(context, KEY) ?: return File()
        return runCatching { json.decodeFromString<File>(raw) }.getOrDefault(File())
    }

    private fun write(
        context: Context,
        file: File,
    ) {
        DarkfiChatSecureStore.putString(context, KEY, json.encodeToString(file))
    }

    private fun Record.toContact() =
        NighthawkContact(
            id = id,
            nickname = nickname,
            dmPublicKey = dmPublicKey,
            walletAddress = walletAddress,
        )
}
