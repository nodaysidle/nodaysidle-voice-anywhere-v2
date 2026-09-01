package com.nodaysidle.voiceanywhere.service

/**
 * Honest Accessibility runtime status.
 *
 * On Xiaomi/MIUI, Settings can still list the service as enabled while
 * dumpsys shows Bound services empty and Crashed services populated.
 * That case is [ENABLED_UNBOUND] — never report it as live/ready.
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
}
