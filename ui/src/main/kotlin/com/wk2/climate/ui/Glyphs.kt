package com.wk2.climate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The drawn glyphs, each an icon-set path used verbatim through [VectorGlyph].
 *
 * Bitmaps live in [Glyph] instead — the airflow and defrost set came from the
 * vendor-style asset pack and has no vector source. Anything added since is
 * drawn, for the reasons in [VectorGlyph].
 *
 * Licences: `akar-icons` is MIT, `material-symbols` is Apache 2.0.
 */

/** HOME. `akar-icons:home`, a stroked outline. */
@Composable
fun HomeGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(
        pathData = "M21 19v-6.733a4 4 0 0 0-1.245-2.9L13.378 3.31a2 2 0 0 0-2.755 " +
            "0L4.245 9.367A4 4 0 0 0 3 12.267V19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2",
        tint = tint,
        size = size,
        modifier = modifier,
        strokeWidth = 2f,
    )

/**
 * The heated steering wheel. `material-symbols:steering-wheel-heat`, filled.
 *
 * Replaced a plain circle that carried its meaning through three fill states.
 * A filled glyph has no ring to fall back on, so the three states became the
 * tint alone: `warm` when it is heating, neutral `ink` when we know it is off,
 * `inkFaint` when we have been told nothing. That keeps off and unknown
 * distinguishable, which was the point of the original faint-versus-warm ring.
 */
@Composable
fun WheelHeatGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = WHEEL_HEAT, tint = tint, size = size, modifier = modifier)

/** The driver's heated seat. `material-symbols:seat-heat-left-sharp`, filled. */
@Composable
fun SeatHeatLeftGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = SEAT_HEAT_LEFT, tint = tint, size = size, modifier = modifier)

private const val WHEEL_HEAT =
    "m22.025 9.55l-1.65-1.1l.325-.525q.15-.225.225-.462T21 6.95q0-.35-.125-" +
        ".675T20.5 5.7q-.525-.525-.812-1.213T19.4 3.05q0-.575.163-1.1t.462-1l" +
        ".325-.5l1.675 1.1l-.35.525q-.15.225-.213.463t-.062.512q0 .35.125.675" +
        "t.375.575q.525.525.813 1.213T23 6.95q0 .575-.162 1.1t-.463 1zm-4.2 0" +
        "l-1.65-1.1l.325-.525q.15-.225.225-.462t.075-.513q0-.35-.125-.675T16.3" +
        " 5.7q-.525-.525-.812-1.213T15.2 3.05q0-.575.163-1.1t.462-1l.325-.5l1." +
        "675 1.1l-.35.525q-.15.225-.213.463t-.062.512q0 .35.125.675t.375.575q." +
        "525.525.813 1.213T18.8 6.95q0 .575-.162 1.1t-.463 1zM13 19.925q2.725-" +
        ".35 4.65-2.275T19.925 13H16l-3 3.75zm.65-10.375l-1.675-1.1l.35-.525q." +
        "15-.225.225-.462t.075-.513q0-.35-.138-.675T12.1 5.7q-.525-.525-.812-1" +
        ".213T11 3.05q0-.575.15-1.1t.475-1l.35-.5l1.675 1.1l-.35.525q-.15.2-." +
        "225.45T13 3.05q0 .35.125.675t.375.575q.525.525.813 1.213T14.6 6.95q0 " +
        ".575-.162 1.1t-.463 1zM4.075 13q.35 2.725 2.275 4.65T11 19.925V16.75L" +
        "8 13zm0-2H21.95q.025.25.038.5T22 12q0 2.075-.787 3.9t-2.138 3.175t-3." +
        "175 2.138T12 22t-3.9-.788t-3.175-2.137T2.788 15.9T2 12q0-3.375 1.975-" +
        "5.988T9 2.45V4.6q-1.975.8-3.312 2.488T4.075 11"

private const val SEAT_HEAT_LEFT =
    "m20.025 12.55l-1.65-1.1l.325-.525q.15-.225.225-.462T19 9.95q0-.35-.125-" +
        ".675T18.5 8.7q-.525-.525-.812-1.212T17.4 6.05q0-.575.163-1.1t.462-1l" +
        ".325-.5l1.675 1.1l-.35.525q-.15.225-.213.463t-.062.512q0 .35.125.675" +
        "t.375.575q.525.525.813 1.213T21 9.95q0 .575-.162 1.1t-.463 1zm-4.2 0" +
        "l-1.65-1.1l.325-.525q.15-.225.225-.462t.075-.513q0-.35-.125-.675T14.3" +
        " 8.7q-.525-.525-.812-1.212T13.2 6.05q0-.575.163-1.1t.462-1l.325-.5l1." +
        "675 1.1l-.35.525q-.15.225-.212.463t-.063.512q0 .35.125.675t.375.575q." +
        "525.525.813 1.213T16.8 9.95q0 .575-.162 1.1t-.463 1zm-4.175 0l-1.675-" +
        "1.1l.35-.525q.15-.225.225-.462t.075-.513q0-.35-.137-.675T10.1 8.7q-." +
        "525-.525-.812-1.212T9 6.05q0-.575.15-1.1t.475-1l.35-.5l1.675 1.1l-.35" +
        ".525q-.15.2-.225.45T11 6.05q0 .35.125.675t.375.575q.525.525.813 1.213" +
        "T12.6 9.95q0 .575-.162 1.1t-.463 1zM17.025 21H6L3.225 10.2q-.1-.375-." +
        "162-.762T3 8.65q0-.7.163-1.375t.487-1.3q.225-.45.638-.712T5.2 5q.575 " +
        "0 1 .425t.425 1q0 .275-.1.525t-.3.45q-.475.5-.562 1.163T5.85 9.85l.5 " +
        "1.05q.725 1.575 1.188 3.25T8 17.575v1.1q.425-.275.9-.475t1-.2H15q.85 " +
        "0 1.438.588T17.025 20z"
