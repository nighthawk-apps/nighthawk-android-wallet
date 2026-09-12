package com.nighthawkapps.lib.android.sdk.chat.hud

import java.io.File

/**
 * Resolves three outbound HUD slots from the daemon JSON sidecar, falling back
 * to a phase-based synthesis so the overlay is honest when the native library
 * has not been rebuilt yet.
 */
object OutboundPeerSlots {
    const val HUD_SLOT_COUNT: Int = 3
    const val FILE_NAME: String = "outbound_slots.json"

    fun fileIn(datastoreDir: File): File = File(datastoreDir, FILE_NAME)

    fun readFile(datastoreDir: File): String? {
        val file = fileIn(datastoreDir)
        if (!file.isFile) {
            return null
        }
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun resolve(
        fileJson: String?,
        phase: String,
        daemonRunning: Boolean,
    ): List<OutboundPeerSlot> {
        val parsed = fileJson?.let { parseJson(it) }.orEmpty()
        if (parsed.size == HUD_SLOT_COUNT) {
            return parsed
        }
        return synthesize(phase, daemonRunning)
    }

    fun synthesize(
        phase: String,
        daemonRunning: Boolean,
    ): List<OutboundPeerSlot> {
        val connecting =
            daemonRunning &&
                phase in
                setOf(
                    "starting",
                    "waiting_for_peers",
                    "static_sync",
                    "syncing_dag",
                    "loading_history",
                )
        val connected = daemonRunning && (phase == "connected" || phase == "running")
        return List(HUD_SLOT_COUNT) { index ->
            val state =
                when {
                    connected && index == 0 -> OutboundPeerState.CONNECTED
                    connecting -> OutboundPeerState.CONNECTING
                    else -> OutboundPeerState.SLEEPING
                }
            OutboundPeerSlot(
                slot = index,
                url = null,
                state = state,
            )
        }
    }

    fun parseJson(raw: String): List<OutboundPeerSlot> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[]") {
            return emptyList()
        }
        val objects = SLOT_OBJECT.findAll(trimmed).map { it.value }.toList()
        return objects.mapNotNull(::parseObject).sortedBy { it.slot }
    }

    private fun parseObject(obj: String): OutboundPeerSlot? {
        val slot =
            SLOT_FIELD
                .find(obj)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: return null
        val url =
            when {
                URL_NULL.containsMatchIn(obj) -> {
                    null
                }

                else -> {
                    URL_STRING
                        .find(obj)
                        ?.groupValues
                        ?.get(1)
                        ?.let(::unescapeJson)
                        ?.takeIf { it.isNotBlank() }
                }
            }
        val state =
            OutboundPeerState.fromWire(
                STATE_FIELD
                    .find(obj)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty(),
            )
        return OutboundPeerSlot(slot = slot, url = url, state = state)
    }

    private fun unescapeJson(value: String): String =
        value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    private val SLOT_OBJECT = Regex("""\{[^{}]+\}""")
    private val SLOT_FIELD = Regex(""""slot"\s*:\s*(\d+)""")
    private val URL_NULL = Regex(""""url"\s*:\s*null""")
    private val URL_STRING = Regex(""""url"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val STATE_FIELD = Regex(""""state"\s*:\s*"(\w+)"""")
}
