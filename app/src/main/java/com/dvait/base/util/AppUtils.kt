package com.dvait.base.util

import android.content.Context
import android.content.pm.PackageManager

data class AppItem(val packageName: String, val label: String)

object AppUtils {
    fun getInstalledApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { 
                // Include apps that have a launch intent (user-facing apps)
                pm.getLaunchIntentForPackage(it.packageName) != null 
            }
            .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
