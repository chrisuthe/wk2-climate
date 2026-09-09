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
            surfaceRaised      = Color(0xFFFFFFFF),
            surfaceInset       = Color(0x0D000000),   // rgba(0,0,0,.05)
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

        fun forNight(isNight: Boolean): Palette = if (isNight) NIGHT else DAY
    }
}

/**
 * A tinted tile's pressed-state fill alpha, layered onto whatever tint colour
 * (e.g. [Palette.cool], [Palette.warm]) that tile was given.
 *
 * Not a handoff token like the 0.18f resting fill or the 0.5f border alpha --
 * those stay as literals at their call sites. This is this task's own value,
 * and every tinted tile Tasks 4-6 add must match it, so it gets a name.
 */
const val PRESSED_TINT_ALPHA = 0.34f
