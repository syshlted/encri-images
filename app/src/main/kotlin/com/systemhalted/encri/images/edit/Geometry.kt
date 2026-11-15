/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How big a picture is, in its pixels. */
data class Size(
    val width: Float,
    val height: Float,
) {
    /** This size given a quarter turn. */
    val turned: Size get() = Size(height, width)

    val ratio: Float get() = width / height
}

/** A rectangle on a picture, in the picture's pixels. */
data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x in left..right && y in top..bottom

    /** This box moved by [dx] and [dy], but no further than keeps it inside a picture [bounds] big. */
    fun movedWithin(
        dx: Float,
        dy: Float,
        bounds: Size,
    ): Box {
        val x = dx.within(-left, bounds.width - right)
        val y = dy.within(-top, bounds.height - bottom)
        return Box(left + x, top + y, right + x, bottom + y)
    }

    /** Whether this box and [other] are the same to within [slack] on every side. */
    fun near(
        other: Box,
        slack: Float,
    ): Boolean =
        abs(left - other.left) <= slack &&
            abs(top - other.top) <= slack &&
            abs(right - other.right) <= slack &&
            abs(bottom - other.bottom) <= slack

    companion object {
        /** All of a picture [size] big. */
        fun of(size: Size): Box = Box(0f, 0f, size.width, size.height)
    }
}

/** [this] held between [low] and [high], or halfway between them when there is no room between. */
internal fun Float.within(
    low: Float,
    high: Float,
): Float = if (low > high) (low + high) / 2 else coerceIn(low, high)

/**
 * Where a picture's pixels go when it is given quarter turns, mirrored, or both: (x, y) goes to
 * (`xx`·x + `xy`·y + `x0`, `yx`·x + `yy`·y + `y0`), each factor -1, 0 or 1.
 *
 * On the plane the pixels cover rather than on pixel indices, so a mirror of a picture `w` wide takes x to `w` - x,
 * and a pixel's left edge to where its right edge goes. A picture's own EXIF orientation ([ofExif]) and the user's
 * turns ([turn]) are both one of these, so a crop chosen on the picture as shown maps back to its pixels exactly.
 */
data class Placement(
    val xx: Int,
    val xy: Int,
    val x0: Float,
    val yx: Int,
    val yy: Int,
    val y0: Float,
) {
    /** Whether this swaps a picture's width and its height. */
    val swapsSides: Boolean get() = xy != 0

    fun mapX(
        x: Float,
        y: Float,
    ): Float = xx * x + xy * y + x0

    fun mapY(
        x: Float,
        y: Float,
    ): Float = yx * x + yy * y + y0

    /** Where [box] goes. */
    fun map(box: Box): Box {
        val ax = mapX(box.left, box.top)
        val ay = mapY(box.left, box.top)
        val bx = mapX(box.right, box.bottom)
        val by = mapY(box.right, box.bottom)
        return Box(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
    }

    /** This, and then [next]. */
    fun then(next: Placement): Placement =
        Placement(
            xx = next.xx * xx + next.xy * yx,
            xy = next.xx * xy + next.xy * yy,
            x0 = next.xx * x0 + next.xy * y0 + next.x0,
            yx = next.yx * xx + next.yy * yx,
            yy = next.yx * xy + next.yy * yy,
            y0 = next.yx * x0 + next.yy * y0 + next.y0,
        )

    /** What puts every pixel back. A turn or a mirror is undone by its transpose. */
    val inverse: Placement
        get() = Placement(xx, yx, -(xx * x0 + yx * y0), xy, yy, -(xy * x0 + yy * y0))

    /** This as `android.graphics.Matrix` values. */
    fun values(): FloatArray = floatArrayOf(xx.toFloat(), xy.toFloat(), x0, yx.toFloat(), yy.toFloat(), y0, 0f, 0f, 1f)

    companion object {
        val NONE = Placement(1, 0, 0f, 0, 1, 0f)

        // EXIF's orientation tag, as ExifInterface names its values.
        private const val FLIP_HORIZONTAL = 2
        private const val ROTATE_180 = 3
        private const val FLIP_VERTICAL = 4
        private const val TRANSPOSE = 5
        private const val ROTATE_90 = 6
        private const val TRANSVERSE = 7
        private const val ROTATE_270 = 8

        /** A quarter turn clockwise, of a picture [size] big: (x, y) goes to (height - y, x). */
        fun turn(size: Size): Placement = Placement(0, -1, size.height, 1, 0, 0f)

        /** [quarters] quarter turns clockwise of a picture [size] big. */
        fun turns(
            quarters: Int,
            size: Size,
        ): Placement {
            var placed = NONE
            var turned = size
            repeat(Math.floorMod(quarters, QUARTERS)) {
                placed = placed.then(turn(turned))
                turned = turned.turned
            }
            return placed
        }

        /** How EXIF's [orientation] places a picture whose pixels are [size] big, to show it upright. */
        fun ofExif(
            orientation: Int,
            size: Size,
        ): Placement {
            val w = size.width
            val h = size.height
            return when (orientation) {
                FLIP_HORIZONTAL -> Placement(-1, 0, w, 0, 1, 0f)
                ROTATE_180 -> Placement(-1, 0, w, 0, -1, h)
                FLIP_VERTICAL -> Placement(1, 0, 0f, 0, -1, h)
                TRANSPOSE -> Placement(0, 1, 0f, 1, 0, 0f)
                ROTATE_90 -> turn(size)
                TRANSVERSE -> Placement(0, -1, h, -1, 0, w)
                ROTATE_270 -> Placement(0, 1, 0f, -1, 0, w)
                else -> NONE
            }
        }

        const val QUARTERS = 4
    }
}
