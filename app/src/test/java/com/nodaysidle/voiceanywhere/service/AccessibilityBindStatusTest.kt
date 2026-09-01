package com.nodaysidle.voiceanywhere.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun settingsOnButUnboundIsDeadNotFakeEnabled() {
        // Xiaomi: ENABLED_ACCESSIBILITY_SERVICES still lists the service while Bound is empty.
        assertEquals(
            AccessibilityBindStatus.ENABLED_UNBOUND,
            AccessibilityBindStatusResolver.resolve(settingsEnabled = true, serviceBound = false)
        )
    }

    @Test
    fun accessTileNeverShowsEnabledWhenBindingIsDead() {
        val detail = AccessibilityBindStatusResolver.tileDetail(AccessibilityBindStatus.ENABLED_UNBOUND)
        assertEquals("DEAD", detail)
        assertFalse(detail.contains("ENABLED"))
        assertFalse(AccessibilityBindStatusResolver.tileOk(AccessibilityBindStatus.ENABLED_UNBOUND))
    }

    @Test
    fun accessTileShowsBoundOnlyWhenLive() {
        assertEquals("BOUND", AccessibilityBindStatusResolver.tileDetail(AccessibilityBindStatus.BOUND))
        assertTrue(AccessibilityBindStatusResolver.tileOk(AccessibilityBindStatus.BOUND))
        assertEquals("ENABLE", AccessibilityBindStatusResolver.tileDetail(AccessibilityBindStatus.DISABLED))
        assertFalse(AccessibilityBindStatusResolver.tileOk(AccessibilityBindStatus.DISABLED))
    }
}
