package com.kris99.baozi.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaoziAppearanceModeTest {
    @Test
    fun parsesStoredAppearanceModes() {
        assertEquals(BaoziAppearanceMode.SYSTEM, BaoziAppearanceMode.fromStorageValue("system"))
        assertEquals(BaoziAppearanceMode.LIGHT, BaoziAppearanceMode.fromStorageValue("LIGHT"))
        assertEquals(BaoziAppearanceMode.DARK, BaoziAppearanceMode.fromStorageValue("dark"))
    }

    @Test
    fun ignoresUnknownStoredAppearanceMode() {
        assertNull(BaoziAppearanceMode.fromStorageValue("sepia"))
        assertNull(BaoziAppearanceMode.fromStorageValue(null))
    }

    @Test
    fun resolvesDarkThemeFromSystemPreference() {
        assertEquals(false, BaoziAppearanceMode.SYSTEM.resolvesDarkTheme(systemIsDark = false))
        assertEquals(true, BaoziAppearanceMode.SYSTEM.resolvesDarkTheme(systemIsDark = true))
        assertEquals(false, BaoziAppearanceMode.LIGHT.resolvesDarkTheme(systemIsDark = true))
        assertEquals(true, BaoziAppearanceMode.DARK.resolvesDarkTheme(systemIsDark = false))
    }
}
