package com.restartfu.murmur.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/** Positions in pixels at density 1, so dp == px. Button is 64, gap is 4. */
class DockTest {
    private fun dock(fieldTop: Int?, fieldBottom: Int?, keyboardTop: Int = 1000) =
        OverlayController.dockY(keyboardTop, fieldTop, fieldBottom, buttonSize = 64, gap = 4, density = 1f)

    @Test fun noFieldDocksOnKeyboard() = assertEquals(932, dock(null, null))

    @Test fun chatFieldOnKeyboardPushesButtonAboveIt() = assertEquals(872, dock(fieldTop = 940, fieldBottom = 992))

    @Test fun fieldFarAboveKeyboardIsIgnored() = assertEquals(932, dock(fieldTop = 300, fieldBottom = 350))

    @Test fun tallFieldIsIgnored() = assertEquals(932, dock(fieldTop = 600, fieldBottom = 1000))

    @Test fun fieldBehindKeyboardIsIgnored() = assertEquals(932, dock(fieldTop = 1100, fieldBottom = 1150))

    @Test fun neverAboveScreenTop() = assertEquals(0, dock(fieldTop = 20, fieldBottom = 60, keyboardTop = 100))
}
