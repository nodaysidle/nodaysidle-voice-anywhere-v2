package com.nodaysidle.voiceanywhere.persistence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Best-effort Xiaomi/MIUI screens for Autostart + Battery "No restrictions".
 * No hidden APIs, no secure-settings writes — intents only; may no-op on non-MIUI.
 */
object XiaomiPersistenceHints {
    const val AUTOSTART_COPY =
        "Xiaomi: Settings → Apps → Manage apps → Voice Anywhere → Autostart → On. " +
            "Then Battery saver → No restrictions. Without this, MIUI may kill the Accessibility binding."

    const val BATTERY_COPY =
        "Set Battery → No restrictions for Voice Anywhere. " +
            "Also allow Autostart so the keep-alive service can restart after force-stop."

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return manufacturer.contains("xiaomi", ignoreCase = true) ||
            brand.contains("xiaomi", ignoreCase = true) ||
            brand.contains("redmi", ignoreCase = true) ||
            brand.contains("poco", ignoreCase = true)
    }

    /** Opens MIUI autostart manager when present; otherwise app details. */
    fun autostartIntent(context: Context): Intent {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            )
        )
        for (intent in candidates) {
            if (intent.resolveActivity(context.packageManager) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return intent
            }
        }
        return appDetailsIntent(context)
    }

    fun batteryNoRestrictionsIntent(context: Context): Intent {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            ).apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Voice Anywhere")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            BatteryOptimizationHelper.appBatterySettingsIntent(context)
        )
        for (intent in candidates) {
            if (intent.resolveActivity(context.packageManager) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return intent
            }
        }
        return appDetailsIntent(context)
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
