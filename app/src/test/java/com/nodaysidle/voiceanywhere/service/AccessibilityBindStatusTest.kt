package com.nodaysidle.voiceanywhere.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityBindStatusTest {
    @Test
    fun settingsOffIsDisabledEvenIfSomehowBoundFlagFalse() {
        assertEquals(
            AccessibilityBindStatus.DISABLED,
            AccessibilityBindStatusResolver.resolve(settingsEnabled = false, serviceBound = false)
        )
    }

    @Test
    fun settingsOnAndBoundIsBound() {
        assertEquals(
            AccessibilityBindStatus.BOUND,
            AccessibilityBindStatusResolver.resolve(settingsEnabled = true, serviceBound = true)
        )
    }

    @Test
    fun settingsOnButUnboundIsEnabledDeadNotFakeEnabled() {
        // Xiaomi: ENABLED_ACCESSIBILITY_SERVICES still lists the service while Bound is empty.
        assertEquals(
            AccessibilityBindStatus.ENABLED_UNBOUND,
            AccessibilityBindStatusResolver.resolve(settingsEnabled = true, serviceBound = false)
        )
    }
}
