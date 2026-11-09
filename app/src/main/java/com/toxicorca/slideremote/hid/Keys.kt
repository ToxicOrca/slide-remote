package com.toxicorca.slideremote.hid

/** USB HID keyboard usage codes (HID Usage Tables, page 0x07). */
object Keys {
    const val MOD_LSHIFT = 0x02

    const val B = 0x05
    const val W = 0x1A
    const val ENTER = 0x28
    const val ESC = 0x29
    const val F5 = 0x3E
    const val HOME = 0x4A
    const val END = 0x4D
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val KP_ENTER = 0x58

    /**
     * Top-row digits: '1'..'9' = 0x1E..0x26, '0' = 0x27.
     * Keypad digits:  KP1..KP9 = 0x59..0x61, KP0 = 0x62 (these depend on the PC's NumLock).
     */
    fun digit(d: Int, numpad: Boolean): Int {
        require(d in 0..9)
        return if (numpad) {
            if (d == 0) 0x62 else 0x58 + d
        } else {
            if (d == 0) 0x27 else 0x1D + d
        }
    }
}

/** One step in a keystroke sequence. */
sealed interface HidAction {
    /** Press and release [code] while holding [modifiers]. */
    data class Key(val code: Int, val modifiers: Int = 0) : HidAction

    /** Wait before the next action (e.g. while PowerPoint opens the slideshow). */
    data class Pause(val ms: Long) : HidAction
}

/** PowerPoint slideshow keyboard commands. */
object Commands {
    /** Typing a slide number then Enter jumps to that slide during a slideshow. */
    fun jumpTo(slide: Int, numpad: Boolean): List<HidAction> {
        require(slide >= 1)
        val digits = slide.toString().map { HidAction.Key(Keys.digit(it.digitToInt(), numpad)) }
        return digits + HidAction.Key(if (numpad) Keys.KP_ENTER else Keys.ENTER)
    }

    val nextArrow: List<HidAction> = listOf(HidAction.Key(Keys.RIGHT))
    val prevArrow: List<HidAction> = listOf(HidAction.Key(Keys.LEFT))

    /** F5: start the slideshow from the first slide. */
    val startShow: List<HidAction> = listOf(HidAction.Key(Keys.F5))

    /** Start the slideshow, wait for it to open, then jump to [slide]. */
    fun startAt(slide: Int, numpad: Boolean, openDelayMs: Long): List<HidAction> =
        if (slide <= 1) startShow
        else startShow + HidAction.Pause(openDelayMs) + jumpTo(slide, numpad)

    /** B toggles a black screen, W a white screen. */
    val black: List<HidAction> = listOf(HidAction.Key(Keys.B))
    val white: List<HidAction> = listOf(HidAction.Key(Keys.W))

    /** Esc ends the slideshow. */
    val endShow: List<HidAction> = listOf(HidAction.Key(Keys.ESC))
}
