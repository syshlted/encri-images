/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The shapes a crop can be held to (I22), or none. */
@Suppress("MagicNumber") // A shape's sides are its name.
enum class Aspect(
    private val across: Int,
    private val down: Int,
) {
    /** Not held: each side is dragged on its own. */
    FREE(0, 0),

    /** The picture's own shape, as it is turned now. */
    ORIGINAL(0, 0),
    SQUARE(1, 1),
    FOUR_BY_THREE(4, 3),
    THREE_BY_FOUR(3, 4),
    SIXTEEN_BY_NINE(16, 9),
    NINE_BY_SIXTEEN(9, 16),
    ;

    /** The width over the height this holds a crop to, on a picture [picture] big; null when it holds it to none. */
    fun ratioIn(picture: Size): Float? =
        when (this) {
            FREE -> null
            ORIGINAL -> picture.ratio
            else -> across.toFloat() / down
        }

    /** The same shape on the picture given a quarter turn: 4:3 becomes 3:4, and the rest are as they were. */
    val turned: Aspect
        get() =
            when (this) {
                FOUR_BY_THREE -> THREE_BY_FOUR
                THREE_BY_FOUR -> FOUR_BY_THREE
                SIXTEEN_BY_NINE -> NINE_BY_SIXTEEN
                NINE_BY_SIXTEEN -> SIXTEEN_BY_NINE
                else -> this
            }
}

/** What a finger on a crop's frame drags: a corner, or a side. */
enum class Handle(
    val movesLeft: Boolean,
    val movesTop: Boolean,
    val movesRight: Boolean,
    val movesBottom: Boolean,
) {
    TOP_LEFT(true, true, false, false),
    TOP(false, true, false, false),
    TOP_RIGHT(false, true, true, false),
    RIGHT(false, false, true, false),
    BOTTOM_RIGHT(false, false, true, true),
    BOTTOM(false, false, false, true),
    BOTTOM_LEFT(true, false, false, true),
    LEFT(true, false, false, false),
    ;

    val isCorner: Boolean get() = (movesLeft || movesRight) && (movesTop || movesBottom)
}

/** How a crop's frame is fitted, dragged and turned, on a picture as it is shown (I22). */
object Frames {
    /**
     * The largest box of [ratio] on a picture [picture] big, centred on ([centerX], [centerY]) as nearly as it fits;
     * all of the picture when [ratio] is null.
     */
    fun largest(
        picture: Size,
        ratio: Float?,
        centerX: Float = picture.width / 2,
        centerY: Float = picture.height / 2,
    ): Box {
        if (ratio == null) return Box.of(picture)
        val width = min(picture.width, picture.height * ratio)
        val height = min(picture.height, width / ratio)
        val left = (centerX - width / 2).within(0f, picture.width - width)
        val top = (centerY - height / 2).within(0f, picture.height - height)
        return Box(left, top, left + width, top + height)
    }

    /**
     * What a finger at ([x], [y]) takes hold of on [frame], each on the screen: a corner within [reach] of it, else a
     * side within [reach] of it, else nothing.
     */
    fun handleAt(
        frame: Box,
        x: Float,
        y: Float,
        reach: Float,
    ): Handle? {
        val across =
            if (y in
                frame.top - reach..frame.bottom + reach
            ) {
                sideNear(x, frame.left, frame.right, reach)
            } else {
                0
            }
        val down = if (x in frame.left - reach..frame.right + reach) sideNear(y, frame.top, frame.bottom, reach) else 0
        if (across == 0 && down == 0) return null
        return Handle.entries.first {
            it.movesLeft == (across < 0) && it.movesRight == (across > 0) &&
                it.movesTop == (down < 0) && it.movesBottom == (down > 0)
        }
    }

    /**
     * -1 when [at] is within [reach] of [low], 1 when within [reach] of [high], and 0 when it is near neither; on a
     * frame smaller than the reach, the nearer.
     */
    private fun sideNear(
        at: Float,
        low: Float,
        high: Float,
        reach: Float,
    ): Int {
        val toLow = abs(at - low)
        val toHigh = abs(at - high)
        return when {
            toLow <= reach && toLow <= toHigh -> -1
            toHigh <= reach -> 1
            else -> 0
        }
    }

    /** [box], on a picture [picture] big, once the picture is given a quarter turn clockwise. */
    fun turned(
        box: Box,
        picture: Size,
    ): Box = Placement.turn(picture).map(box)

    /**
     * [box] with [handle] dragged to ([x], [y]), held inside a picture [picture] big and no smaller than [smallest] on
     * either side. Held to [ratio] when it is not null: a corner is dragged against the corner opposite, which stays
     * where it is, and a side against the side opposite, the box keeping its middle.
     */
    @Suppress("LongParameterList") // One drag: the box, what is dragged, where to, and the three things that hold it.
    fun resized(
        box: Box,
        handle: Handle,
        x: Float,
        y: Float,
        picture: Size,
        ratio: Float?,
        smallest: Float,
    ): Box {
        val least = min(smallest, min(picture.width, picture.height))
        return when {
            ratio == null -> freely(box, handle, x, y, picture, least)
            handle.isCorner -> byCorner(box, handle, x, y, picture, ratio, least)
            else -> bySide(box, handle, x, y, picture, ratio, least)
        }
    }

    @Suppress("LongParameterList") // resized's, less the ratio.
    private fun freely(
        box: Box,
        handle: Handle,
        x: Float,
        y: Float,
        picture: Size,
        least: Float,
    ) = Box(
        left = if (handle.movesLeft) x.within(0f, box.right - least) else box.left,
        top = if (handle.movesTop) y.within(0f, box.bottom - least) else box.top,
        right = if (handle.movesRight) x.within(box.left + least, picture.width) else box.right,
        bottom = if (handle.movesBottom) y.within(box.top + least, picture.height) else box.bottom,
    )

    @Suppress("LongParameterList") // resized's.
    private fun byCorner(
        box: Box,
        handle: Handle,
        x: Float,
        y: Float,
        picture: Size,
        ratio: Float,
        least: Float,
    ): Box {
        val anchorX = if (handle.movesLeft) box.right else box.left
        val anchorY = if (handle.movesTop) box.bottom else box.top
        val roomX = if (handle.movesLeft) anchorX else picture.width - anchorX
        val roomY = if (handle.movesTop) anchorY else picture.height - anchorY
        val reachX = if (handle.movesLeft) anchorX - x else x - anchorX
        val reachY = if (handle.movesTop) anchorY - y else y - anchorY
        // The box follows whichever way the finger went further.
        val width = max(reachX, reachY * ratio).within(max(least, least * ratio), min(roomX, roomY * ratio))
        val height = width / ratio
        val left = if (handle.movesLeft) anchorX - width else anchorX
        val top = if (handle.movesTop) anchorY - height else anchorY
        return Box(left, top, left + width, top + height)
    }

    @Suppress("LongParameterList") // resized's.
    private fun bySide(
        box: Box,
        handle: Handle,
        x: Float,
        y: Float,
        picture: Size,
        ratio: Float,
        least: Float,
    ): Box =
        if (handle.movesLeft || handle.movesRight) {
            val anchor = if (handle.movesLeft) box.right else box.left
            val room = if (handle.movesLeft) anchor else picture.width - anchor
            val reach = if (handle.movesLeft) anchor - x else x - anchor
            val width = reach.within(max(least, least * ratio), min(room, picture.height * ratio))
            val height = width / ratio
            val left = if (handle.movesLeft) anchor - width else anchor
            val top = (box.centerY - height / 2).within(0f, picture.height - height)
            Box(left, top, left + width, top + height)
        } else {
            val anchor = if (handle.movesTop) box.bottom else box.top
            val room = if (handle.movesTop) anchor else picture.height - anchor
            val reach = if (handle.movesTop) anchor - y else y - anchor
            val height = reach.within(max(least, least / ratio), min(room, picture.width / ratio))
            val width = height * ratio
            val top = if (handle.movesTop) anchor - height else anchor
            val left = (box.centerX - width / 2).within(0f, picture.width - width)
            Box(left, top, left + width, top + height)
        }
}
