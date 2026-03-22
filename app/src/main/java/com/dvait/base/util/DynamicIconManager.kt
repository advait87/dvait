package com.dvait.base.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.dvait.base.util.FileLogger

class DynamicIconManager(private val context: Context) {

    private val packageManager = context.packageManager
    private val packageName = context.packageName

    private val iconAliases = listOf(
        "orange" to "$packageName.MainActivityOrange",
        "teal" to "$packageName.MainActivityTeal",
        "blue" to "$packageName.MainActivityBlue",
        "mono" to "$packageName.MainActivityMono"
    )

    fun setIcon(accentColor: String) {
        val targetAlias = iconAliases.find { it.first == accentColor }?.second ?: return
        
        // Find which one is currently enabled
        val currentlyEnabled = iconAliases.find { (_, alias) ->
            try {
                val state = packageManager.getComponentEnabledSetting(ComponentName(context, alias))
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } catch (e: Exception) {
                false
            }
        }?.second

        // If target is already enabled, do nothing
        if (currentlyEnabled == targetAlias) {
            return
        }

        FileLogger.i("DynamicIcon", "Switching app icon to: $accentColor ($targetAlias)")

        // 1. Enable the target alias
        packageManager.setComponentEnabledSetting(
            ComponentName(context, targetAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 2. Disable all other enabled aliases
        iconAliases.filter { it.second != targetAlias }.forEach { (_, alias) ->
            try {
                val state = packageManager.getComponentEnabledSetting(ComponentName(context, alias))
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                     // Note: We only disable if it's currently enabled or default (if it's not the target)
                     packageManager.setComponentEnabledSetting(
                        ComponentName(context, alias),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (e: Exception) {}
        }
    }
}
