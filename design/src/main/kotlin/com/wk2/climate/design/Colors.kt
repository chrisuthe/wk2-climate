package com.wk2.climate.design

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens for one theme.
 *
 * Day and night differ in luminance only — never in layout — so muscle memory
 * holds. Switching follows the vehicle's illumination signal, not a clock.
 *
 * Accent colours are authored in `oklch`, which Android cannot express, so they
 * are converted once here with the source value in the comment. Two of the
 * blues ask for more chroma than sRGB provides at that lightness and are
 * gamut-clamped; they read very slightly less saturated than the browser
 * mockup, which is a display limit rather than a design change.
 *
 * No shadows anywhere. Elevation is carried by surface tint and border only —
 * shadows read as smudges on a glossy panel in sunlight.
 */
data class Palette(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceInset: Color,
    val ink: Color,
    val inkDim: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val divider: Color,
    val borderControl: Color,
    val borderControlLarge: Color,
    val accent: Color,
    val accentInk: Color,
    val cool: Color,
    val coolBright: Color,
    val coolLabel: Color,
    val warm: Color,
    val warmBright: Color,
    val trackStart: Color,
    val trackEnd: Color,
) {
    companion object {
        val NIGHT = Palette(
            surface            = Color(0xFF0B0C0D),
            surfaceRaised      = Color(0x0DFFFFFF),   // rgba(255,255,255,.05)
            surfaceInset       = Color(0x09FFFFFF),   // rgba(255,255,255,.035)
            ink                = Color(0xFFF4F4F3),
            inkDim             = Color(0xCCF4F4F3),   // .8
            inkMuted           = Color(0x73F4F4F3),   // .45
            inkFaint           = Color(0x66F4F4F3),   // .4
            divider            = Color(0x17FFFFFF),   // rgba(255,255,255,.09)
            borderControl      = Color(0x33FFFFFF),   // .2
            borderControlLarge = Color(0x3DFFFFFF),   // .24
            accent             = Color(0xFFEFA831),   // oklch(0.78 0.15 75)
            accentInk          = Color(0xFF141414),
            cool               = Color(0xFF4CB0E5),   // oklch(0.72 0.12 235)
            coolBright         = Color(0xFF6BC3F4),   // oklch(0.78 0.11 235)
            coolLabel          = Color(0xFF89CEF6),   // oklch(0.82 0.09 235)
            warm               = Color(0xFFE96E50),   // oklch(0.68 0.16 35)
            warmBright         = Color(0xFFFA8467),   // oklch(0.74 0.15 35)
            trackStart         = Color(0xFF008BC2),   // oklch(0.60 0.13 235) — gamut-clamped
            trackEnd           = Color(0xFFEE8266),   // oklch(0.72 0.14 35)
        )

        val DAY = Palette(
            surface            = Color(0xFFECEAE6),
            // rgba(0,0,0,.05) -- the *opposite polarity* of night's
            // rgba(255,255,255,.05), not the same value.
            //
            // The handoff's token is an **overlay**, not a colour: a 5% white
            // wash recesses a panel on a dark ground and glares on a light
            // one. This was once opaque `#ffffff`, which inverted the intended
            // depth -- the fan meter and the four seat tiles rendered as the
            // brightest cards on the page instead of as recessed insets. Read
            // the handoff's day column (README "Inset strip" row) as flipping
            // the overlay's polarity, and never as reusing night's literal.
            //
            // `surface-card #ffffff` in README's day token list names a token
            // this UI has no equivalent of -- nothing on 1d or 2a is a card.
            surfaceRaised      = Color(0x0D000000),   // rgba(0,0,0,.05)
            // rgba(0,0,0,.035) -- the darkening equivalent of night's
            // rgba(255,255,255,.035), by the same polarity rule as the row
            // above. The README gives no day value for this token, but it does
            // give night's, and the rule determines the rest: this is a
            // *lighter* overlay than surfaceRaised in both palettes, and
            // collapsing it onto surfaceRaised's literal made it a duplicate
            // of its neighbour rather than the mirror of its own night value.
            surfaceInset       = Color(0x09000000),   // rgba(0,0,0,.035)
            ink                = Color(0xFF16181A),
            inkDim             = Color(0xCC16181A),
            inkMuted           = Color(0x80000000),   // rgba(0,0,0,.5)
            inkFaint           = Color(0x66000000),
            divider            = Color(0x1A000000),   // rgba(0,0,0,.1)
            borderControl      = Color(0x33000000),
            borderControlLarge = Color(0x3D000000),
            accent             = Color(0xFFB37903),   // oklch(0.62 0.13 75)
            accentInk          = Color(0xFFFFFFFF),
            cool               = Color(0xFF0073AD),   // oklch(0.52 0.14 235) — gamut-clamped
            coolBright         = Color(0xFF0073AD),
            coolLabel          = Color(0xFF0073AD),
            warm               = Color(0xFFBC4527),   // oklch(0.55 0.16 35)
            warmBright         = Color(0xFFBC4527),
            trackStart         = Color(0xFF0073AD),
            trackEnd           = Color(0xFFBC4527),
        )

        /**
         * The palette for a reported illumination — and for an unreported one.
         *
         * **An unknown illumination selects [NIGHT].** `null` means the
         * vehicle has never told us: `ILLUMINATION` only changes when the
         * headlights switch, so a cold start at night with the lights already
         * on is exactly the case that is never pushed, and the old
         * `Boolean`-only signature collapsed that into [DAY].
         *
         * The consequences are asymmetric, which is what decides it. Too dark
         * a bar is a nuisance the driver resolves by looking at it. A
         * full-brightness white bar at night in a moving vehicle is a
         * genuine hazard. So the default takes the safer side.
         *
         * This is a *rendering policy applied to an absent reading* — nothing
         * anywhere synthesises an illumination value, and `ClimateState`
         * still reports `null`. Do not "fix" this back to `?: false`.
         */
        fun forNight(isNight: Boolean?): Palette = if (isNight == false) DAY else NIGHT
    }
}

/**
 * A tinted tile's pressed-state fill alpha, layered onto whatever tint colour
 * (e.g. [Palette.cool], [Palette.warm]) that tile was given.
 *
 * Specifically the pressed alpha for a tile whose **resting** tint is `0.18f`
 * -- the temperature steppers. It is not a universal value, and a tile with a
 * different resting alpha must not simply reuse this number.
 *
 * The principle the panel follows is that **pressing roughly doubles the
 * resting tint**, so the perceived jump is the same everywhere even though the
 * absolute values are not: the steppers go 0.18 -> 0.34, and the heated wheel
 * goes 0.12 -> 0.24. Forcing 0.34 on the wheel would be consistent in the
 * number and inconsistent in the thing a driver actually sees.
 *
 * Not a handoff token like the 0.18f resting fill or the 0.5f border alpha --
 * those stay as literals at their call sites.
 */
const val PRESSED_TINT_ALPHA = 0.34f
