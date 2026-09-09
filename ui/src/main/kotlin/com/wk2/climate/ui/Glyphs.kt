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

/** The passenger's heated seat. `material-symbols:seat-heat-right-sharp`, filled. */
@Composable
fun SeatHeatRightGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = SEAT_HEAT_RIGHT, tint = tint, size = size, modifier = modifier)

/** The driver's ventilated seat. `material-symbols:seat-cool-left-sharp`, filled. */
@Composable
fun SeatCoolLeftGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = SEAT_COOL_LEFT, tint = tint, size = size, modifier = modifier)

/** The passenger's ventilated seat. `material-symbols:seat-cool-right-sharp`, filled. */
@Composable
fun SeatCoolRightGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = SEAT_COOL_RIGHT, tint = tint, size = size, modifier = modifier)

/**
 * Rear defrost. `material-symbols-light:windshield-defrost-rear`, filled.
 *
 * Replaces a hand-drawn rounded rect with three wave strokes, which existed
 * only because the asset pack had no rear-defrost glyph. The handoff invited
 * this directly -- README:220, "The supplied icon set has no rear-defrost
 * glyph; if you have one, use it."
 */
@Composable
fun RearDefrostGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = REAR_DEFROST, tint = tint, size = size, modifier = modifier)

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

private const val SEAT_HEAT_RIGHT =
    "m14.025 12.55l-1.65-1.1l.325-.525q.15-.225.225-.462T13 9.95q0-.3" +
        "5-.138-.675t-.387-.575q-.525-.525-.8-1.212T11.4 6.05q0-.575.163-" +
        "1.1t.462-1l.325-.5l1.675 1.1l-.35.525q-.15.225-.225.463t-.075.51" +
        "2q0 .35.138.675t.387.575q.525.525.8 1.213t.275 1.437q0 .575-.15 " +
        "1.1t-.45 1zm-4.2 0l-1.65-1.1l.325-.525q.15-.225.225-.462T8.8 9.9" +
        "5q0-.35-.138-.675T8.275 8.7q-.525-.525-.8-1.212T7.2 6.05q0-.575." +
        "163-1.1t.462-1l.325-.5l1.675 1.1l-.35.525q-.15.225-.225.463t-.07" +
        "5.512q0 .35.138.675T9.7 7.3q.525.525.8 1.213t.275 1.437q0 .575-." +
        "15 1.1t-.45 1zm-4.2 0l-1.65-1.1l.35-.525q.15-.225.212-.462T4.6 9" +
        ".95q0-.35-.125-.675T4.1 8.7q-.525-.525-.812-1.212T3 6.05q0-.575." +
        "15-1.1t.475-1l.35-.5l1.65 1.1l-.35.525q-.15.2-.225.45t-.075.525q" +
        "0 .35.138.675T5.5 7.3q.525.525.813 1.213T6.6 9.95q0 .575-.162 1." +
        "1t-.463 1zM6.975 21v-1q0-.825.575-1.412T8.975 18h5.1q.525 0 1 .2" +
        "t.9.475v-1.1q0-1.75.475-3.425t1.2-3.25l.475-1.05q.275-.625.2-1.2" +
        "88t-.55-1.162q-.2-.2-.3-.45t-.1-.525q0-.575.413-1T18.775 5q.5 0 " +
        ".925.263t.65.712q.325.625.488 1.3T21 8.65q0 .4-.05.788t-.175.762" +
        "L18 21z"

private const val SEAT_COOL_LEFT =
    "M17.025 21H6L3.225 10.2q-.1-.375-.162-.762T3 8.65q0-.7.163-1.375" +
        "t.487-1.3q.225-.45.638-.712T5.2 5q.575 0 1 .425t.425 1q0 .275-.1" +
        ".525t-.3.45q-.475.5-.562 1.163T5.85 9.85l.5 1.05q.725 1.575 1.18" +
        "8 3.25T8 17.575v1.1q.425-.275.9-.475t1-.2H15q.85 0 1.438.588T17." +
        "025 20zm-2.775-6v-2.2l-1.725 1.725l-1.05-1.05L14.25 10.7v-.95h-." +
        "95l-2.775 2.775l-1.05-1.05L11.2 9.75H9v-1.5h2.2L9.475 6.525l1.05" +
        "-1.05L13.3 8.25h.95V7.3l-2.775-2.775l1.05-1.05L14.25 5.2V3h1.5v2" +
        ".2l1.725-1.725l1.05 1.05L15.75 7.3v.95h.95l2.775-2.775l1.05 1.05" +
        "L18.8 8.25H21v1.5h-2.2l1.725 1.725l-1.05 1.05L16.7 9.75h-.95v.95" +
        "l2.775 2.775l-1.05 1.05L15.75 12.8V15z"

private const val SEAT_COOL_RIGHT =
    "M6.975 21v-1q0-.825.588-1.412T9 18h5.075q.525 0 1.013.2t.912.475" +
        "v-1.1q0-1.75.462-3.425t1.188-3.25l.5-1.05q.275-.625.188-1.287T17" +
        ".775 7.4q-.2-.2-.3-.45t-.1-.525q0-.575.413-1T18.775 5q.5 0 .925." +
        "263t.65.712q.325.625.488 1.3T21 8.65q0 .4-.05.788t-.175.762L18 2" +
        "1zm1.275-6v-2.2l-1.725 1.725l-1.075-1.05l2.8-2.775v-.95H7.3l-2.7" +
        "75 2.775l-1.075-1.05L5.175 9.75H3v-1.5h2.175L3.45 6.525l1.075-1." +
        "05L7.3 8.25h.95V7.3l-2.8-2.775l1.075-1.05L8.25 5.2V3h1.5v2.2l1.7" +
        "-1.725l1.075 1.05L9.75 7.3v.95h.925l2.775-2.775l1.075 1.05L12.8 " +
        "8.25H15v1.5h-2.2l1.725 1.725l-1.075 1.05l-2.775-2.775H9.75v.95l2" +
        ".775 2.775l-1.075 1.05l-1.7-1.725V15z"

private const val REAR_DEFROST =
    "M18.385 17v-1h1q.23 0 .423-.192t.192-.423v-8.77q0-.23-.192-.423T" +
        "19.385 6H4.615q-.23 0-.423.192T4 6.616v8.769q0 .23.192.423t.423." +
        "192h.827v1h-.827q-.69 0-1.153-.462T3 15.385v-8.77q0-.69.463-1.15" +
        "2T4.615 5h14.77q.69 0 1.152.463T21 6.616v8.769q0 .69-.463 1.153T" +
        "19.385 17zm-1.899 2.53l-.861-.465l.152-.313q.112-.263.167-.53q.0" +
        "56-.266.056-.541q0-.427-.125-.829t-.394-.729q-.39-.506-.63-1.097" +
        "q-.24-.592-.24-1.226q0-.402.105-.783q.105-.38.309-.74l.152-.27l." +
        "867.466l-.177.294q-.13.244-.203.52q-.072.276-.072.551q0 .427.154" +
        ".82t.423.719q.41.467.62 1.049T17 17.623q0 .421-.095.812q-.096.39" +
        "-.26.769zm-3.892 0l-.861-.464l.151-.314q.112-.263.168-.53q.056-." +
        "266.056-.541q0-.427-.125-.829t-.395-.729q-.39-.506-.63-1.097q-.2" +
        "39-.592-.239-1.226q0-.402.105-.783t.309-.74l.152-.27l.867.466l-." +
        "177.294q-.13.244-.203.511q-.072.266-.072.541q0 .427.154.829t.423" +
        ".729q.41.467.62 1.049t.21 1.197q0 .421-.094.811q-.096.391-.261.7" +
        "7zm-3.886 0l-.868-.464l.158-.314q.111-.263.167-.54q.056-.276.056" +
        "-.55q0-.427-.128-.82t-.397-.719q-.41-.487-.64-1.078q-.229-.591-." +
        "229-1.226q0-.402.102-.792t.311-.75l.158-.27l.867.466l-.177.294q-" +
        ".13.239-.205.508t-.075.544q0 .427.153.829q.154.402.423.729q.41.4" +
        "67.62 1.049t.211 1.197q0 .421-.095.811t-.26.77z"
