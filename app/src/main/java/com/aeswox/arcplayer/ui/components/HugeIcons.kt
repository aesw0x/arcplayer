package com.aeswox.arcplayer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The navigation glyphs, each in an outlined and a filled cut, plus the transport and tool cuts the
 * player draws.
 *
 * Ported from Arc Music's own icon set so both apps speak the same line weight: one 2dp stroke on a
 * 24dp grid, rounded joins, and a filled sibling that keeps a negative-space hole exactly the size of
 * the outlined version's opening. That last rule is what lets the two cuts crossfade without the icon
 * appearing to swell or shrink as it fills.
 */
object HugeIcons {

    private var _Home: ImageVector? = null

    val Home: ImageVector
        get() {
            if (_Home != null) {
                return _Home!!
            }
            _Home = ImageVector.Builder(
                name = "Home",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(3f, 11.9896f)
                    lineTo(3f, 14.5f)
                    curveTo(3f, 17.7998f, 3f, 19.4497f, 4.02513f, 20.4749f)
                    curveTo(5.05025f, 21.5f, 6.70017f, 21.5f, 10f, 21.5f)
                    lineTo(14f, 21.5f)
                    curveTo(17.2998f, 21.5f, 18.9497f, 21.5f, 19.9749f, 20.4749f)
                    curveTo(21f, 19.4497f, 21f, 17.7998f, 21f, 14.5f)
                    lineTo(21f, 11.9896f)
                    curveTo(21f, 10.3083f, 21f, 9.46773f, 20.6441f, 8.74005f)
                    curveTo(20.2882f, 8.01237f, 19.6247f, 7.49628f, 18.2976f, 6.46411f)
                    lineTo(16.2976f, 4.90855f)
                    curveTo(14.2331f, 3.30285f, 13.2009f, 2.5f, 12f, 2.5f)
                    curveTo(10.7991f, 2.5f, 9.76689f, 3.30285f, 7.70242f, 4.90855f)
                    lineTo(5.70241f, 6.46411f)
                    curveTo(4.37533f, 7.49628f, 3.71179f, 8.01237f, 3.3559f, 8.74005f)
                    curveTo(3f, 9.46773f, 3f, 10.3083f, 3f, 11.9896f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(17f, 17.5f)
                    lineTo(17f, 13.5f)
                }
            }.build()
            return _Home!!
        }

    private var _HomeFilled: ImageVector? = null

    val HomeFilled: ImageVector
        get() {
            if (_HomeFilled != null) {
                return _HomeFilled!!
            }
            _HomeFilled = ImageVector.Builder(
                name = "HomeFilled",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(3f, 11.9896f)
                    lineTo(3f, 14.5f)
                    curveTo(3f, 17.7998f, 3f, 19.4497f, 4.02513f, 20.4749f)
                    curveTo(5.05025f, 21.5f, 6.70017f, 21.5f, 10f, 21.5f)
                    lineTo(14f, 21.5f)
                    curveTo(17.2998f, 21.5f, 18.9497f, 21.5f, 19.9749f, 20.4749f)
                    curveTo(21f, 19.4497f, 21f, 17.7998f, 21f, 14.5f)
                    lineTo(21f, 11.9896f)
                    curveTo(21f, 10.3083f, 21f, 9.46773f, 20.6441f, 8.74005f)
                    curveTo(20.2882f, 8.01237f, 19.6247f, 7.49628f, 18.2976f, 6.46411f)
                    lineTo(16.2976f, 4.90855f)
                    curveTo(14.2331f, 3.30285f, 13.2009f, 2.5f, 12f, 2.5f)
                    curveTo(10.7991f, 2.5f, 9.76689f, 3.30285f, 7.70242f, 4.90855f)
                    lineTo(5.70241f, 6.46411f)
                    curveTo(4.37533f, 7.49628f, 3.71179f, 8.01237f, 3.3559f, 8.74005f)
                    curveTo(3f, 9.46773f, 3f, 10.3083f, 3f, 11.9896f)
                    close()

                    // The doorway, cut out, standing where Home's 2dp stroke sat at x=17.
                    moveTo(16f, 13.5f)
                    curveTo(16f, 12.9477f, 16.4477f, 12.5f, 17f, 12.5f)
                    curveTo(17.5523f, 12.5f, 18f, 12.9477f, 18f, 13.5f)
                    lineTo(18f, 17.5f)
                    curveTo(18f, 18.0523f, 17.5523f, 18.5f, 17f, 18.5f)
                    curveTo(16.4477f, 18.5f, 16f, 18.0523f, 16f, 17.5f)
                    close()
                }
            }.build()
            return _HomeFilled!!
        }

    private var _Search: ImageVector? = null

    val Search: ImageVector
        get() {
            if (_Search != null) {
                return _Search!!
            }
            _Search = ImageVector.Builder(
                name = "Search",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4f,
                    fill = null
                ) {
                    moveTo(17f, 10f)
                    curveTo(17f, 13.866f, 13.866f, 17f, 10f, 17f)
                    curveTo(6.13401f, 17f, 3f, 13.866f, 3f, 10f)
                    curveTo(3f, 6.13401f, 6.13401f, 3f, 10f, 3f)
                    curveTo(13.866f, 3f, 17f, 6.13401f, 17f, 10f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null,
                    strokeLineWidth = 0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4f
                ) {
                    moveTo(20.4697f, 21.5303f)
                    curveTo(20.7626f, 21.8232f, 21.2374f, 21.8232f, 21.5303f, 21.5303f)
                    curveTo(21.8232f, 21.2374f, 21.8232f, 20.7626f, 21.5303f, 20.4697f)
                    lineTo(21f, 21f)
                    lineTo(20.4697f, 21.5303f)
                    close()
                    moveTo(15f, 15f)
                    lineTo(14.4697f, 15.5303f)
                    lineTo(20.4697f, 21.5303f)
                    lineTo(21f, 21f)
                    lineTo(21.5303f, 20.4697f)
                    lineTo(15.5303f, 14.4697f)
                    lineTo(15f, 15f)
                    close()
                }
            }.build()
            return _Search!!
        }

    private var _SearchFilled: ImageVector? = null

    val SearchFilled: ImageVector
        get() {
            if (_SearchFilled != null) {
                return _SearchFilled!!
            }
            _SearchFilled = ImageVector.Builder(
                name = "SearchFilled",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    // Solid lens.
                    moveTo(17.5f, 10f)
                    curveTo(17.5f, 14.1421f, 14.1421f, 17.5f, 10f, 17.5f)
                    curveTo(5.85786f, 17.5f, 2.5f, 14.1421f, 2.5f, 10f)
                    curveTo(2.5f, 5.85786f, 5.85786f, 2.5f, 10f, 2.5f)
                    curveTo(14.1421f, 2.5f, 17.5f, 5.85786f, 17.5f, 10f)
                    close()

                    // Glint cut out of the upper-left of the glass.
                    moveTo(6.2f, 10f)
                    curveTo(6.2f, 7.9f, 7.9f, 6.2f, 10f, 6.2f)
                    curveTo(10.55f, 6.2f, 11f, 5.75f, 11f, 5.2f)
                    curveTo(11f, 4.65f, 10.55f, 4.2f, 10f, 4.2f)
                    curveTo(6.8f, 4.2f, 4.2f, 6.8f, 4.2f, 10f)
                    curveTo(4.2f, 10.55f, 4.65f, 11f, 5.2f, 11f)
                    curveTo(5.75f, 11f, 6.2f, 10.55f, 6.2f, 10f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null
                ) {
                    moveTo(15.2f, 13.8f)
                    lineTo(13.8f, 15.2f)
                    lineTo(19.8f, 21.2f)
                    curveTo(20.2f, 21.6f, 20.8f, 21.6f, 21.2f, 21.2f)
                    curveTo(21.6f, 20.8f, 21.6f, 20.2f, 21.2f, 19.8f)
                    close()
                }
            }.build()
            return _SearchFilled!!
        }

    private var _Library: ImageVector? = null

    /**
     * The shelf: a front card with two more stacked behind it.
     *
     * Arc Music carries a music note struck through the front card. This is the same three strokes
     * with the note left out, because a film shelf has nothing to put in that hole.
     */
    val Library: ImageVector
        get() {
            if (_Library != null) {
                return _Library!!
            }
            _Library = ImageVector.Builder(
                name = "Library",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(3f, 15f)
                    curveTo(3f, 12.1911f, 3f, 10.7866f, 3.67412f, 9.77772f)
                    curveTo(3.96596f, 9.34096f, 4.34096f, 8.96596f, 4.77772f, 8.67412f)
                    curveTo(5.78661f, 8f, 7.19108f, 8f, 10f, 8f)
                    lineTo(14f, 8f)
                    curveTo(16.8089f, 8f, 18.2134f, 8f, 19.2223f, 8.67412f)
                    curveTo(19.659f, 8.96596f, 20.034f, 9.34096f, 20.3259f, 9.77772f)
                    curveTo(21f, 10.7866f, 21f, 12.1911f, 21f, 15f)
                    curveTo(21f, 17.8089f, 21f, 19.2134f, 20.3259f, 20.2223f)
                    curveTo(20.034f, 20.659f, 19.659f, 21.034f, 19.2223f, 21.3259f)
                    curveTo(18.2134f, 22f, 16.8089f, 22f, 14f, 22f)
                    lineTo(10f, 22f)
                    curveTo(7.19108f, 22f, 5.78661f, 22f, 4.77772f, 21.3259f)
                    curveTo(4.34096f, 21.034f, 3.96596f, 20.659f, 3.67412f, 20.2223f)
                    curveTo(3f, 19.2134f, 3f, 17.8089f, 3f, 15f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(19f, 8f)
                    curveTo(18.9821f, 6.76022f, 18.89f, 6.05733f, 18.4182f, 5.58579f)
                    curveTo(17.8321f, 5f, 16.8888f, 5f, 15.0022f, 5f)
                    lineTo(8.99783f, 5f)
                    curveTo(7.11118f, 5f, 6.16786f, 5f, 5.58176f, 5.58579f)
                    curveTo(5.10996f, 6.05733f, 5.01794f, 6.76022f, 5f, 8f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(17f, 5f)
                    curveTo(17f, 4.06812f, 17f, 3.60218f, 16.8478f, 3.23463f)
                    curveTo(16.6448f, 2.74458f, 16.2554f, 2.35523f, 15.7654f, 2.15224f)
                    curveTo(15.3978f, 2f, 14.9319f, 2f, 14f, 2f)
                    lineTo(10f, 2f)
                    curveTo(9.06812f, 2f, 8.60218f, 2f, 8.23463f, 2.15224f)
                    curveTo(7.74458f, 2.35523f, 7.35523f, 2.74458f, 7.15224f, 3.23463f)
                    curveTo(7f, 3.60218f, 7f, 4.06812f, 7f, 5f)
                }
            }.build()
            return _Library!!
        }

    private var _LibraryFilled: ImageVector? = null

    /** The two back cards stay struck through, as in [Library], so only the front one fills. */
    val LibraryFilled: ImageVector
        get() {
            if (_LibraryFilled != null) {
                return _LibraryFilled!!
            }
            _LibraryFilled = ImageVector.Builder(
                name = "LibraryFilled",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(19f, 8f)
                    curveTo(18.9821f, 6.76022f, 18.89f, 6.05733f, 18.4182f, 5.58579f)
                    curveTo(17.8321f, 5f, 16.8888f, 5f, 15.0022f, 5f)
                    lineTo(8.99783f, 5f)
                    curveTo(7.11118f, 5f, 6.16786f, 5f, 5.58176f, 5.58579f)
                    curveTo(5.10996f, 6.05733f, 5.01794f, 6.76022f, 5f, 8f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(17f, 5f)
                    curveTo(17f, 4.06812f, 17f, 3.60218f, 16.8478f, 3.23463f)
                    curveTo(16.6448f, 2.74458f, 16.2554f, 2.35523f, 15.7654f, 2.15224f)
                    curveTo(15.3978f, 2f, 14.9319f, 2f, 14f, 2f)
                    lineTo(10f, 2f)
                    curveTo(9.06812f, 2f, 8.60218f, 2f, 8.23463f, 2.15224f)
                    curveTo(7.74458f, 2.35523f, 7.35523f, 2.74458f, 7.15224f, 3.23463f)
                    curveTo(7f, 3.60218f, 7f, 4.06812f, 7f, 5f)
                }
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null
                ) {
                    moveTo(3f, 15f)
                    curveTo(3f, 12.1911f, 3f, 10.7866f, 3.67412f, 9.77772f)
                    curveTo(3.96596f, 9.34096f, 4.34096f, 8.96596f, 4.77772f, 8.67412f)
                    curveTo(5.78661f, 8f, 7.19108f, 8f, 10f, 8f)
                    lineTo(14f, 8f)
                    curveTo(16.8089f, 8f, 18.2134f, 8f, 19.2223f, 8.67412f)
                    curveTo(19.659f, 8.96596f, 20.034f, 9.34096f, 20.3259f, 9.77772f)
                    curveTo(21f, 10.7866f, 21f, 12.1911f, 21f, 15f)
                    curveTo(21f, 17.8089f, 21f, 19.2134f, 20.3259f, 20.2223f)
                    curveTo(20.034f, 20.659f, 19.659f, 21.034f, 19.2223f, 21.3259f)
                    curveTo(18.2134f, 22f, 16.8089f, 22f, 14f, 22f)
                    lineTo(10f, 22f)
                    curveTo(7.19108f, 22f, 5.78661f, 22f, 4.77772f, 21.3259f)
                    curveTo(4.34096f, 21.034f, 3.96596f, 20.659f, 3.67412f, 20.2223f)
                    curveTo(3f, 19.2134f, 3f, 17.8089f, 3f, 15f)
                    close()
                }
            }.build()
            return _LibraryFilled!!
        }

    private var _Settings: ImageVector? = null

    val Settings: ImageVector
        get() {
            if (_Settings != null) {
                return _Settings!!
            }
            _Settings = ImageVector.Builder(
                name = "Settings",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    fill = null
                ) {
                    moveTo(15.5f, 12f)
                    curveTo(15.5f, 13.933f, 13.933f, 15.5f, 12f, 15.5f)
                    curveTo(10.067f, 15.5f, 8.5f, 13.933f, 8.5f, 12f)
                    curveTo(8.5f, 10.067f, 10.067f, 8.5f, 12f, 8.5f)
                    curveTo(13.933f, 8.5f, 15.5f, 10.067f, 15.5f, 12f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    fill = null
                ) {
                    moveTo(21.011f, 14.0965f)
                    curveTo(21.5329f, 13.9558f, 21.7939f, 13.8854f, 21.8969f, 13.7508f)
                    curveTo(22f, 13.6163f, 22f, 13.3998f, 22f, 12.9669f)
                    lineTo(22f, 11.0332f)
                    curveTo(22f, 10.6003f, 22f, 10.3838f, 21.8969f, 10.2493f)
                    curveTo(21.7938f, 10.1147f, 21.5329f, 10.0443f, 21.011f, 9.90358f)
                    curveTo(19.0606f, 9.37759f, 17.8399f, 7.33851f, 18.3433f, 5.40087f)
                    curveTo(18.4817f, 4.86799f, 18.5509f, 4.60156f, 18.4848f, 4.44529f)
                    curveTo(18.4187f, 4.28902f, 18.2291f, 4.18134f, 17.8497f, 3.96596f)
                    lineTo(16.125f, 2.98673f)
                    curveTo(15.7528f, 2.77539f, 15.5667f, 2.66972f, 15.3997f, 2.69222f)
                    curveTo(15.2326f, 2.71472f, 15.0442f, 2.90273f, 14.6672f, 3.27873f)
                    curveTo(13.208f, 4.73448f, 10.7936f, 4.73442f, 9.33434f, 3.27864f)
                    curveTo(8.95743f, 2.90263f, 8.76898f, 2.71463f, 8.60193f, 2.69212f)
                    curveTo(8.43489f, 2.66962f, 8.24877f, 2.77529f, 7.87653f, 2.98663f)
                    lineTo(6.15184f, 3.96587f)
                    curveTo(5.77253f, 4.18123f, 5.58287f, 4.28891f, 5.51678f, 4.44515f)
                    curveTo(5.45068f, 4.6014f, 5.51987f, 4.86787f, 5.65825f, 5.4008f)
                    curveTo(6.16137f, 7.3385f, 4.93972f, 9.37763f, 2.98902f, 9.9036f)
                    curveTo(2.46712f, 10.0443f, 2.20617f, 10.1147f, 2.10308f, 10.2492f)
                    curveTo(2f, 10.3838f, 2f, 10.6003f, 2f, 11.0332f)
                    lineTo(2f, 12.9669f)
                    curveTo(2f, 13.3998f, 2f, 13.6163f, 2.10308f, 13.7508f)
                    curveTo(2.20615f, 13.8854f, 2.46711f, 13.9558f, 2.98902f, 14.0965f)
                    curveTo(4.9394f, 14.6225f, 6.16008f, 16.6616f, 5.65672f, 18.5992f)
                    curveTo(5.51829f, 19.1321f, 5.44907f, 19.3985f, 5.51516f, 19.5548f)
                    curveTo(5.58126f, 19.7111f, 5.77092f, 19.8188f, 6.15025f, 20.0341f)
                    lineTo(7.87495f, 21.0134f)
                    curveTo(8.24721f, 21.2247f, 8.43334f, 21.3304f, 8.6004f, 21.3079f)
                    curveTo(8.76746f, 21.2854f, 8.95588f, 21.0973f, 9.33271f, 20.7213f)
                    curveTo(10.7927f, 19.2644f, 13.2088f, 19.2643f, 14.6689f, 20.7212f)
                    curveTo(15.0457f, 21.0973f, 15.2341f, 21.2853f, 15.4012f, 21.3078f)
                    curveTo(15.5682f, 21.3303f, 15.7544f, 21.2246f, 16.1266f, 21.0133f)
                    lineTo(17.8513f, 20.034f)
                    curveTo(18.2307f, 19.8187f, 18.4204f, 19.711f, 18.4864f, 19.5547f)
                    curveTo(18.5525f, 19.3984f, 18.4833f, 19.132f, 18.3448f, 18.5991f)
                    curveTo(17.8412f, 16.6616f, 19.0609f, 14.6226f, 21.011f, 14.0965f)
                    close()
                }
            }.build()
            return _Settings!!
        }

    private var _SettingsFilled: ImageVector? = null

    /**
     * The cog filled, with its hub punched out.
     *
     * Arc Music never needed this cut — its settings live behind a menu, not a tab — so this is the
     * outlined wheel's own body contour, filled, and a hole at the exact radius the 2dp hub stroke
     * leaves open. Same rule as [HomeFilled]: fill around the hole the outlined version already shows,
     * so a crossfade between the two only changes how much of the gear is solid.
     */
    val SettingsFilled: ImageVector
        get() {
            if (_SettingsFilled != null) {
                return _SettingsFilled!!
            }
            _SettingsFilled = ImageVector.Builder(
                name = "SettingsFilled",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = null,
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(21.011f, 14.0965f)
                    curveTo(21.5329f, 13.9558f, 21.7939f, 13.8854f, 21.8969f, 13.7508f)
                    curveTo(22f, 13.6163f, 22f, 13.3998f, 22f, 12.9669f)
                    lineTo(22f, 11.0332f)
                    curveTo(22f, 10.6003f, 22f, 10.3838f, 21.8969f, 10.2493f)
                    curveTo(21.7938f, 10.1147f, 21.5329f, 10.0443f, 21.011f, 9.90358f)
                    curveTo(19.0606f, 9.37759f, 17.8399f, 7.33851f, 18.3433f, 5.40087f)
                    curveTo(18.4817f, 4.86799f, 18.5509f, 4.60156f, 18.4848f, 4.44529f)
                    curveTo(18.4187f, 4.28902f, 18.2291f, 4.18134f, 17.8497f, 3.96596f)
                    lineTo(16.125f, 2.98673f)
                    curveTo(15.7528f, 2.77539f, 15.5667f, 2.66972f, 15.3997f, 2.69222f)
                    curveTo(15.2326f, 2.71472f, 15.0442f, 2.90273f, 14.6672f, 3.27873f)
                    curveTo(13.208f, 4.73448f, 10.7936f, 4.73442f, 9.33434f, 3.27864f)
                    curveTo(8.95743f, 2.90263f, 8.76898f, 2.71463f, 8.60193f, 2.69212f)
                    curveTo(8.43489f, 2.66962f, 8.24877f, 2.77529f, 7.87653f, 2.98663f)
                    lineTo(6.15184f, 3.96587f)
                    curveTo(5.77253f, 4.18123f, 5.58287f, 4.28891f, 5.51678f, 4.44515f)
                    curveTo(5.45068f, 4.6014f, 5.51987f, 4.86787f, 5.65825f, 5.4008f)
                    curveTo(6.16137f, 7.3385f, 4.93972f, 9.37763f, 2.98902f, 9.9036f)
                    curveTo(2.46712f, 10.0443f, 2.20617f, 10.1147f, 2.10308f, 10.2492f)
                    curveTo(2f, 10.3838f, 2f, 10.6003f, 2f, 11.0332f)
                    lineTo(2f, 12.9669f)
                    curveTo(2f, 13.3998f, 2f, 13.6163f, 2.10308f, 13.7508f)
                    curveTo(2.20615f, 13.8854f, 2.46711f, 13.9558f, 2.98902f, 14.0965f)
                    curveTo(4.9394f, 14.6225f, 6.16008f, 16.6616f, 5.65672f, 18.5992f)
                    curveTo(5.51829f, 19.1321f, 5.44907f, 19.3985f, 5.51516f, 19.5548f)
                    curveTo(5.58126f, 19.7111f, 5.77092f, 19.8188f, 6.15025f, 20.0341f)
                    lineTo(7.87495f, 21.0134f)
                    curveTo(8.24721f, 21.2247f, 8.43334f, 21.3304f, 8.6004f, 21.3079f)
                    curveTo(8.76746f, 21.2854f, 8.95588f, 21.0973f, 9.33271f, 20.7213f)
                    curveTo(10.7927f, 19.2644f, 13.2088f, 19.2643f, 14.6689f, 20.7212f)
                    curveTo(15.0457f, 21.0973f, 15.2341f, 21.2853f, 15.4012f, 21.3078f)
                    curveTo(15.5682f, 21.3303f, 15.7544f, 21.2246f, 16.1266f, 21.0133f)
                    lineTo(17.8513f, 20.034f)
                    curveTo(18.2307f, 19.8187f, 18.4204f, 19.711f, 18.4864f, 19.5547f)
                    curveTo(18.5525f, 19.3984f, 18.4833f, 19.132f, 18.3448f, 18.5991f)
                    curveTo(17.8412f, 16.6616f, 19.0609f, 14.6226f, 21.011f, 14.0965f)
                    close()

                    moveTo(12f, 9.5f)
                    curveTo(13.3807f, 9.5f, 14.5f, 10.6193f, 14.5f, 12f)
                    curveTo(14.5f, 13.3807f, 13.3807f, 14.5f, 12f, 14.5f)
                    curveTo(10.6193f, 14.5f, 9.5f, 13.3807f, 9.5f, 12f)
                    curveTo(9.5f, 10.6193f, 10.6193f, 9.5f, 12f, 9.5f)
                    close()
                }
            }.build()
            return _SettingsFilled!!
        }

    private var _Play: ImageVector? = null

    val Play: ImageVector
        get() {
            if (_Play != null) {
                return _Play!!
            }
            _Play = ImageVector.Builder(
                name = "Play",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(18.8906f, 12.846f)
                    curveTo(18.5371f, 14.189f, 16.8667f, 15.138f, 13.5257f, 17.0361f)
                    curveTo(10.296f, 18.8709f, 8.6812f, 19.7884f, 7.37983f, 19.4196f)
                    curveTo(6.8418f, 19.2671f, 6.35159f, 18.9776f, 5.95624f, 18.5787f)
                    curveTo(5f, 17.6139f, 5f, 15.7426f, 5f, 12f)
                    curveTo(5f, 8.2574f, 5f, 6.3861f, 5.95624f, 5.42132f)
                    curveTo(6.35159f, 5.02245f, 6.8418f, 4.73288f, 7.37983f, 4.58042f)
                    curveTo(8.6812f, 4.21165f, 10.296f, 5.12907f, 13.5257f, 6.96393f)
                    curveTo(16.8667f, 8.86197f, 18.5371f, 9.811f, 18.8906f, 11.154f)
                    curveTo(19.0365f, 11.7084f, 19.0365f, 12.2916f, 18.8906f, 12.846f)
                    close()
                }
            }.build()
            return _Play!!
        }

    private var _Pause: ImageVector? = null

    val Pause: ImageVector
        get() {
            if (_Pause != null) {
                return _Pause!!
            }
            _Pause = ImageVector.Builder(
                name = "Pause",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4f,
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(4f, 7f)
                    curveTo(4f, 5.58579f, 4f, 4.87868f, 4.43934f, 4.43934f)
                    curveTo(4.87868f, 4f, 5.58579f, 4f, 7f, 4f)
                    curveTo(8.41421f, 4f, 9.12132f, 4f, 9.56066f, 4.43934f)
                    curveTo(10f, 4.87868f, 10f, 5.58579f, 10f, 7f)
                    lineTo(10f, 17f)
                    curveTo(10f, 18.4142f, 10f, 19.1213f, 9.56066f, 19.5607f)
                    curveTo(9.12132f, 20f, 8.41421f, 20f, 7f, 20f)
                    curveTo(5.58579f, 20f, 4.87868f, 20f, 4.43934f, 19.5607f)
                    curveTo(4f, 19.1213f, 4f, 18.4142f, 4f, 17f)
                    lineTo(4f, 7f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4f,
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(14f, 7f)
                    curveTo(14f, 5.58579f, 14f, 4.87868f, 14.4393f, 4.43934f)
                    curveTo(14.8787f, 4f, 15.5858f, 4f, 17f, 4f)
                    curveTo(18.4142f, 4f, 19.1213f, 4f, 19.5607f, 4.43934f)
                    curveTo(20f, 4.87868f, 20f, 5.58579f, 20f, 7f)
                    lineTo(20f, 17f)
                    curveTo(20f, 18.4142f, 20f, 19.1213f, 19.5607f, 19.5607f)
                    curveTo(19.1213f, 20f, 18.4142f, 20f, 17f, 20f)
                    curveTo(15.5858f, 20f, 14.8787f, 20f, 14.4393f, 19.5607f)
                    curveTo(14f, 19.1213f, 14f, 18.4142f, 14f, 17f)
                    lineTo(14f, 7f)
                    close()
                }
            }.build()
            return _Pause!!
        }

    private var _Previous: ImageVector? = null

    val Previous: ImageVector
        get() {
            if (_Previous != null) {
                return _Previous!!
            }
            _Previous = ImageVector.Builder(
                name = "Previous",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(8.06492f, 12.6258f)
                    curveTo(8.31931f, 13.8374f, 9.67295f, 14.7077f, 12.3802f, 16.4481f)
                    curveTo(15.3247f, 18.3411f, 16.797f, 19.2876f, 17.9895f, 18.9229f)
                    curveTo(18.3934f, 18.7994f, 18.7654f, 18.5823f, 19.0777f, 18.2876f)
                    curveTo(20f, 17.4178f, 20f, 15.6118f, 20f, 12f)
                    curveTo(20f, 8.38816f, 20f, 6.58224f, 19.0777f, 5.71235f)
                    curveTo(18.7654f, 5.41773f, 18.3934f, 5.20057f, 17.9895f, 5.07707f)
                    curveTo(16.797f, 4.71243f, 15.3247f, 5.6589f, 12.3802f, 7.55186f)
                    curveTo(9.67295f, 9.29233f, 8.31931f, 10.1626f, 8.06492f, 11.3742f)
                    curveTo(7.97836f, 11.7865f, 7.97836f, 12.2135f, 8.06492f, 12.6258f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4f,
                    fill = null
                ) {
                    moveTo(4f, 4f)
                    lineTo(4f, 20f)
                }
            }.build()
            return _Previous!!
        }

    private var _Next: ImageVector? = null

    val Next: ImageVector
        get() {
            if (_Next != null) {
                return _Next!!
            }
            _Next = ImageVector.Builder(
                name = "Next",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(15.9351f, 12.6258f)
                    curveTo(15.6807f, 13.8374f, 14.327f, 14.7077f, 11.6198f, 16.4481f)
                    curveTo(8.67528f, 18.3411f, 7.20303f, 19.2876f, 6.01052f, 18.9229f)
                    curveTo(5.60662f, 18.7994f, 5.23463f, 18.5823f, 4.92227f, 18.2876f)
                    curveTo(4f, 17.4178f, 4f, 15.6118f, 4f, 12f)
                    curveTo(4f, 8.38816f, 4f, 6.58224f, 4.92227f, 5.71235f)
                    curveTo(5.23463f, 5.41773f, 5.60662f, 5.20057f, 6.01052f, 5.07707f)
                    curveTo(7.20304f, 4.71243f, 8.67528f, 5.6589f, 11.6198f, 7.55186f)
                    curveTo(14.327f, 9.29233f, 15.6807f, 10.1626f, 15.9351f, 11.3742f)
                    curveTo(16.0216f, 11.7865f, 16.0216f, 12.2135f, 15.9351f, 12.6258f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4f,
                    fill = null
                ) {
                    moveTo(20f, 5f)
                    lineTo(20f, 19f)
                }
            }.build()
            return _Next!!
        }

    private var _PictureInPicture: ImageVector? = null

    val PictureInPicture: ImageVector
        get() {
            if (_PictureInPicture != null) {
                return _PictureInPicture!!
            }
            _PictureInPicture = ImageVector.Builder(
                name = "PictureInPicture",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(6.99219f, 19.965f)
                    curveTo(5.11989f, 19.8873f, 3.97194f, 19.6366f, 3.16376f, 18.8284f)
                    curveTo(1.99219f, 17.6569f, 1.99219f, 15.7712f, 1.99219f, 12f)
                    curveTo(1.99219f, 8.22876f, 1.99219f, 6.34315f, 3.16376f, 5.17157f)
                    curveTo(4.33534f, 4f, 6.22095f, 4f, 9.99219f, 4f)
                    horizontalLineTo(13.9922f)
                    curveTo(17.7634f, 4f, 19.6491f, 4f, 20.8206f, 5.17157f)
                    curveTo(21.4738f, 5.82475f, 21.7628f, 6.69989f, 21.8907f, 8f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(19.9297f, 12f)
                    horizontalLineTo(13.0547f)
                    curveTo(11.9156f, 12f, 10.9922f, 12.8954f, 10.9922f, 14f)
                    verticalLineTo(18f)
                    curveTo(10.9922f, 19.1046f, 11.9156f, 20f, 13.0547f, 20f)
                    horizontalLineTo(19.9297f)
                    curveTo(21.0688f, 20f, 21.9922f, 19.1046f, 21.9922f, 18f)
                    verticalLineTo(14f)
                    curveTo(21.9922f, 12.8954f, 21.0688f, 12f, 19.9297f, 12f)
                    close()
                }
            }.build()
            return _PictureInPicture!!
        }

    private var _Rotate: ImageVector? = null

    val Rotate: ImageVector
        get() {
            if (_Rotate != null) {
                return _Rotate!!
            }
            _Rotate = ImageVector.Builder(
                name = "Rotate",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(15f, 17f)
                    curveTo(18.2998f, 17f, 19.9497f, 17f, 20.9749f, 15.9749f)
                    curveTo(22f, 14.9497f, 22f, 13.2998f, 22f, 10f)
                    curveTo(22f, 6.70017f, 22f, 5.05025f, 20.9749f, 4.02513f)
                    curveTo(19.9497f, 3f, 18.2998f, 3f, 15f, 3f)
                    curveTo(11.7002f, 3f, 10.0503f, 3f, 9.02513f, 4.02513f)
                    curveTo(8f, 5.05025f, 8f, 6.70017f, 8f, 10f)
                    curveTo(8f, 13.2998f, 8f, 14.9497f, 9.02513f, 15.9749f)
                    curveTo(10.0503f, 17f, 11.7002f, 17f, 15f, 17f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(2f, 14.4201f)
                    lineTo(3.11013f, 13.0744f)
                    curveTo(3.70104f, 12.3581f, 3.99649f, 12f, 4.36364f, 12f)
                    lineTo(4.36364f, 13f)
                    curveTo(4.36364f, 16.7712f, 4.36364f, 18.6569f, 5.53521f, 19.8284f)
                    curveTo(6.70678f, 21f, 8.5924f, 21f, 12.3636f, 21f)
                    lineTo(13f, 21f)
                }
            }.build()
            return _Rotate!!
        }

    private var _ChevronRight: ImageVector? = null

    val ChevronRight: ImageVector
        get() {
            if (_ChevronRight != null) {
                return _ChevronRight!!
            }
            _ChevronRight = ImageVector.Builder(
                name = "ChevronRight",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(9.00005f, 6f)
                    curveTo(9.00005f, 6f, 15f, 10.4189f, 15f, 12f)
                    curveTo(15f, 13.5812f, 9f, 18f, 9f, 18f)
                }
            }.build()
            return _ChevronRight!!
        }

    private var _Stretch: ImageVector? = null

    val Stretch: ImageVector
        get() {
            if (_Stretch != null) {
                return _Stretch!!
            }
            _Stretch = ImageVector.Builder(
                name = "Stretch",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(16.5148f, 16.5149f)
                    curveTo(17.0896f, 15.9401f, 16.9941f, 13.4982f, 16.9941f, 13.4982f)
                    moveTo(16.5148f, 16.5149f)
                    curveTo(15.94f, 17.0897f, 13.4981f, 16.994f, 13.4981f, 16.994f)
                    moveTo(16.5148f, 16.5149f)
                    lineTo(13f, 13f)
                    moveTo(7.48506f, 7.48512f)
                    curveTo(8.05987f, 6.91031f, 10.5018f, 7.00596f, 10.5018f, 7.00596f)
                    moveTo(7.48506f, 7.48512f)
                    curveTo(6.91025f, 8.05993f, 7.00598f, 10.5018f, 7.00598f, 10.5018f)
                    moveTo(7.48506f, 7.48512f)
                    lineTo(11f, 11f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(2.5f, 12f)
                    curveTo(2.5f, 7.52166f, 2.5f, 5.28249f, 3.89124f, 3.89124f)
                    curveTo(5.28249f, 2.5f, 7.52166f, 2.5f, 12f, 2.5f)
                    curveTo(16.4783f, 2.5f, 18.7175f, 2.5f, 20.1088f, 3.89124f)
                    curveTo(21.5f, 5.28249f, 21.5f, 7.52166f, 21.5f, 12f)
                    curveTo(21.5f, 16.4783f, 21.5f, 18.7175f, 20.1088f, 20.1088f)
                    curveTo(18.7175f, 21.5f, 16.4783f, 21.5f, 12f, 21.5f)
                    curveTo(7.52166f, 21.5f, 5.28249f, 21.5f, 3.89124f, 20.1088f)
                    curveTo(2.5f, 18.7175f, 2.5f, 16.4783f, 2.5f, 12f)
                    close()
                }
            }.build()
            return _Stretch!!
        }

    private var _Crop: ImageVector? = null

    val Crop: ImageVector
        get() {
            if (_Crop != null) {
                return _Crop!!
            }
            _Crop = ImageVector.Builder(
                name = "Crop",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    fill = null
                ) {
                    moveTo(10.4922f, 3.00391f)
                    curveTo(7.22965f, 3.02207f, 5.46278f, 3.16674f, 4.31021f, 4.31932f)
                    curveTo(2.99219f, 5.63734f, 2.99219f, 7.75866f, 2.99219f, 12.0013f)
                    curveTo(2.99219f, 16.2439f, 2.99219f, 18.3653f, 4.31021f, 19.6833f)
                    curveTo(5.62823f, 21.0013f, 7.74955f, 21.0013f, 11.9922f, 21.0013f)
                    curveTo(16.2348f, 21.0013f, 18.3561f, 21.0013f, 19.6742f, 19.6833f)
                    curveTo(20.8267f, 18.5307f, 20.9714f, 16.7638f, 20.9896f, 13.5013f)
                }
                // The source ships the arrow as a filled outline; this is the same 1.5-wide,
                // round-capped stroke it was generated from, so the two land on the same pixels.
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(10.9922f, 13.0002f)
                    lineTo(20.3421f, 3.64962f)
                    moveTo(20.8861f, 9.00098f)
                    lineTo(20.5795f, 3.41364f)
                    lineTo(14.9922f, 3.10712f)
                }
            }.build()
            return _Crop!!
        }

    private var _Fit: ImageVector? = null

    val Fit: ImageVector
        get() {
            if (_Fit != null) {
                return _Fit!!
            }
            _Fit = ImageVector.Builder(
                name = "Fit",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(2.5f, 12f)
                    curveTo(2.5f, 7.52166f, 2.5f, 5.28249f, 3.89124f, 3.89124f)
                    curveTo(5.28249f, 2.5f, 7.52166f, 2.5f, 12f, 2.5f)
                    curveTo(16.4783f, 2.5f, 18.7175f, 2.5f, 20.1088f, 3.89124f)
                    curveTo(21.5f, 5.28249f, 21.5f, 7.52166f, 21.5f, 12f)
                    curveTo(21.5f, 16.4783f, 21.5f, 18.7175f, 20.1088f, 20.1088f)
                    curveTo(18.7175f, 21.5f, 16.4783f, 21.5f, 12f, 21.5f)
                    curveTo(7.52166f, 21.5f, 5.28249f, 21.5f, 3.89124f, 20.1088f)
                    curveTo(2.5f, 18.7175f, 2.5f, 16.4783f, 2.5f, 12f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(12.9833f, 11.0167f)
                    curveTo(12.4085f, 10.4419f, 12.5041f, 8f, 12.5041f, 8f)
                    moveTo(12.9833f, 11.0167f)
                    curveTo(13.5581f, 11.5915f, 16f, 11.4958f, 16f, 11.4958f)
                    moveTo(12.9833f, 11.0167f)
                    lineTo(17f, 7f)
                    moveTo(11.0131f, 12.9868f)
                    curveTo(10.4383f, 12.412f, 7.99641f, 12.5077f, 7.99641f, 12.5077f)
                    moveTo(11.0131f, 12.9868f)
                    curveTo(11.5879f, 13.5616f, 11.4922f, 16.0035f, 11.4922f, 16.0035f)
                    moveTo(11.0131f, 12.9868f)
                    lineTo(7f, 16.9999f)
                }
            }.build()
            return _Fit!!
        }

    private var _Subtitles: ImageVector? = null

    val Subtitles: ImageVector
        get() {
            if (_Subtitles != null) {
                return _Subtitles!!
            }
            _Subtitles = ImageVector.Builder(
                name = "Subtitles",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(14.1706f, 20.8905f)
                    curveTo(18.3536f, 20.6125f, 21.6856f, 17.2332f, 21.9598f, 12.9909f)
                    curveTo(22.0134f, 12.1607f, 22.0134f, 11.3009f, 21.9598f, 10.4707f)
                    curveTo(21.6856f, 6.22838f, 18.3536f, 2.84913f, 14.1706f, 2.57107f)
                    curveTo(12.7435f, 2.47621f, 11.2536f, 2.47641f, 9.8294f, 2.57107f)
                    curveTo(5.64639f, 2.84913f, 2.31441f, 6.22838f, 2.04024f, 10.4707f)
                    curveTo(1.98659f, 11.3009f, 1.98659f, 12.1607f, 2.04024f, 12.9909f)
                    curveTo(2.1401f, 14.536f, 2.82343f, 15.9666f, 3.62791f, 17.1746f)
                    curveTo(4.09501f, 18.0203f, 3.78674f, 19.0758f, 3.30021f, 19.9978f)
                    curveTo(2.94941f, 20.6626f, 2.77401f, 20.995f, 2.91484f, 21.2351f)
                    curveTo(3.05568f, 21.4752f, 3.37026f, 21.4829f, 3.99943f, 21.4982f)
                    curveTo(5.24367f, 21.5285f, 6.08268f, 21.1757f, 6.74868f, 20.6846f)
                    curveTo(7.1264f, 20.4061f, 7.31527f, 20.2668f, 7.44544f, 20.2508f)
                    curveTo(7.5756f, 20.2348f, 7.83177f, 20.3403f, 8.34401f, 20.5513f)
                    curveTo(8.8044f, 20.7409f, 9.33896f, 20.8579f, 9.8294f, 20.8905f)
                    curveTo(11.2536f, 20.9852f, 12.7435f, 20.9854f, 14.1706f, 20.8905f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    fill = null
                ) {
                    moveTo(15f, 16f)
                    horizontalLineTo(17f)
                    moveTo(7f, 16f)
                    horizontalLineTo(12f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    fill = null
                ) {
                    moveTo(16f, 13f)
                    horizontalLineTo(17f)
                    moveTo(11f, 13f)
                    horizontalLineTo(13f)
                    moveTo(7f, 13f)
                    horizontalLineTo(8f)
                }
            }.build()
            return _Subtitles!!
        }

    private var _AudioTracks: ImageVector? = null

    val AudioTracks: ImageVector
        get() {
            if (_AudioTracks != null) {
                return _AudioTracks!!
            }
            _AudioTracks = ImageVector.Builder(
                name = "AudioTracks",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    fill = null
                ) {
                    moveTo(20.5f, 13.5f)
                    verticalLineTo(10.5f)
                    curveTo(20.5f, 6.74142f, 20.5f, 4.86213f, 19.4472f, 3.60746f)
                    curveTo(19.2788f, 3.40678f, 19.0932f, 3.22119f, 18.8925f, 3.0528f)
                    curveTo(17.6379f, 2f, 15.7586f, 2f, 12f, 2f)
                    curveTo(8.24142f, 2f, 6.36213f, 2f, 5.10746f, 3.0528f)
                    curveTo(4.90678f, 3.22119f, 4.72119f, 3.40678f, 4.5528f, 3.60746f)
                    curveTo(3.5f, 4.86213f, 3.5f, 6.74142f, 3.5f, 10.5f)
                    verticalLineTo(13.5f)
                    curveTo(3.5f, 17.2586f, 3.5f, 19.1379f, 4.5528f, 20.3925f)
                    curveTo(4.72119f, 20.5932f, 4.90678f, 20.7788f, 5.10746f, 20.9472f)
                    curveTo(6.36213f, 22f, 8.24142f, 22f, 12f, 22f)
                    curveTo(15.7586f, 22f, 17.6379f, 22f, 18.8925f, 20.9472f)
                    curveTo(19.0932f, 20.7788f, 19.2788f, 20.5932f, 19.4472f, 20.3925f)
                    curveTo(20.5f, 19.1379f, 20.5f, 17.2586f, 20.5f, 13.5f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    fill = null
                ) {
                    moveTo(15.5f, 15f)
                    curveTo(15.5f, 16.933f, 13.933f, 18.5f, 12f, 18.5f)
                    curveTo(10.067f, 18.5f, 8.5f, 16.933f, 8.5f, 15f)
                    curveTo(8.5f, 13.067f, 10.067f, 11.5f, 12f, 11.5f)
                    curveTo(13.933f, 11.5f, 15.5f, 13.067f, 15.5f, 15f)
                    close()
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    fill = null
                ) {
                    moveTo(13.5f, 7f)
                    curveTo(13.5f, 7.82843f, 12.8284f, 8.5f, 12f, 8.5f)
                    curveTo(11.1716f, 8.5f, 10.5f, 7.82843f, 10.5f, 7f)
                    curveTo(10.5f, 6.17157f, 11.1716f, 5.5f, 12f, 5.5f)
                    curveTo(12.8284f, 5.5f, 13.5f, 6.17157f, 13.5f, 7f)
                    close()
                }
            }.build()
            return _AudioTracks!!
        }

    private var _Download: ImageVector? = null

    val Download: ImageVector
        get() {
            if (_Download != null) {
                return _Download!!
            }
            _Download = ImageVector.Builder(
                name = "Download",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(2.99969f, 17.0002f)
                    curveTo(2.99969f, 17.9302f, 2.99969f, 18.3952f, 3.10192f, 18.7767f)
                    curveTo(3.37932f, 19.8119f, 4.18796f, 20.6206f, 5.22324f, 20.898f)
                    curveTo(5.60474f, 21.0002f, 6.06972f, 21.0002f, 6.99969f, 21.0002f)
                    lineTo(16.9997f, 21.0002f)
                    curveTo(17.9297f, 21.0002f, 18.3947f, 21.0002f, 18.7762f, 20.898f)
                    curveTo(19.8114f, 20.6206f, 20.6201f, 19.8119f, 20.8975f, 18.7767f)
                    curveTo(20.9997f, 18.3952f, 20.9997f, 17.9302f, 20.9997f, 17.0002f)
                }
                path(
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = null
                ) {
                    moveTo(16.4998f, 11.5002f)
                    curveTo(16.4998f, 11.5002f, 13.1856f, 16.0002f, 11.9997f, 16.0002f)
                    curveTo(10.8139f, 16.0002f, 7.49976f, 11.5002f, 7.49976f, 11.5002f)
                    moveTo(11.9997f, 15.0002f)
                    lineTo(11.9997f, 3.00016f)
                }
            }.build()
            return _Download!!
        }
}
