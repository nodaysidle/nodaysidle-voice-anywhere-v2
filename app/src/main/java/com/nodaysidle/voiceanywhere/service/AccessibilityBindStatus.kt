package com.nodaysidle.voiceanywhere.service

/**
 * Honest Accessibility runtime status.
 *
 * On Xiaomi/MIUI, Settings can still list the service as enabled while
 * dumpsys shows Bound services empty and Crashed services populated.
 * That case is [ENABLED_UNBOUND] — the ACCESS tile must show DEAD, never ENABLED.
 */
enum class AccessibilityBindStatus {
    /** Not listed in ENABLED_ACCESSIBILITY_SERVICES. */
    DISABLED,

    /**
     * Listed as enabled in Settings, but our process has no live
     * [VoiceAccessibilityService] binding (crashed / unbound / DEAD).
     */
    ENABLED_UNBOUND,

    /** Service instance is connected and bound. */
    BOUND
}

object AccessibilityBindStatusResolver {
    fun resolve(settingsEnabled: Boolean, serviceBound: Boolean): AccessibilityBindStatus = when {
        !settingsEnabled -> AccessibilityBindStatus.DISABLED
        serviceBound -> AccessibilityBindStatus.BOUND
        else -> AccessibilityBindStatus.ENABLED_UNBOUND
    }

    /**
     * ACCESS tile copy. Never returns a bare "ENABLED" for a dead bind —
     * Settings can still list the service while Bound is empty.
     */
    fun tileDetail(status: AccessibilityBindStatus): String = when (status) {
        AccessibilityBindStatus.BOUND -> "BOUND"
        AccessibilityBindStatus.ENABLED_UNBOUND -> "DEAD"
        AccessibilityBindStatus.DISABLED -> "ENABLE"
    }

    fun tileOk(status: AccessibilityBindStatus): Boolean =
        status == AccessibilityBindStatus.BOUND
}
