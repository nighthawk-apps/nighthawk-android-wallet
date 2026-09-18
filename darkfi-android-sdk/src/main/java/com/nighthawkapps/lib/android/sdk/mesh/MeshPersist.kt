package com.nighthawkapps.lib.android.sdk.mesh

import android.content.Context

/**
 * SDK-owned mesh enable / always-on flags. The UI catalog
 * (`com.nighthawkapps.lib.android`) is mirrored so chat/wallet HUD
 * observers stay in sync when the FGS Stop action flips the radio off.
 */
object MeshPersist {
    private const val PREFS = "nighthawk_mesh"
    private const val KEY_ENABLED = "mesh_enabled"
    private const val KEY_ALWAYS_ON = "always_on"
    private const val UI_PREFS = "com.nighthawkapps.lib.android"
    private const val UI_KEY_ENABLED = "is_nighthawk_mesh_enabled"
    private const val UI_KEY_ALWAYS_ON = "is_nighthawk_mesh_always_on"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun alwaysOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ALWAYS_ON, true)

    fun setEnabled(
        context: Context,
        on: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        context.applicationContext
            .getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(UI_KEY_ENABLED, on.toString())
            .apply()
    }

    fun setAlwaysOn(
        context: Context,
        on: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_ALWAYS_ON, on).apply()
        context.applicationContext
            .getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(UI_KEY_ALWAYS_ON, on.toString())
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
