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

    private fun place(aboveKeyboard: Int?, keyboardTop: Int = 1000) =
        OverlayController.placeY(keyboardTop, 940, 992, buttonSize = 64, gap = 4, density = 1f, aboveKeyboard = aboveKeyboard)

    @Test fun noCustomPositionUsesDock() = assertEquals(872, place(null))

    @Test fun customPositionOverridesDock() = assertEquals(636, place(300))

    @Test fun customPositionFollowsKeyboardHeight() = assertEquals(536, place(300, keyboardTop = 900))

    @Test fun customPositionClampedToScreen() = assertEquals(0, place(5000))

    @Test fun customPositionNeverOnKeyboard() = assertEquals(936, place(-50))
}
