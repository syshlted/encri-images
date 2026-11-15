/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import kotlin.math.min

/**
 * Where the turned picture is drawn on the editor's screen (I22): fitted into [area] at first, then zoomed by a pinch
 * and moved by a finger, so a crop can be placed on a detail too small to reach unzoomed.
 *
 * A point of the picture at (x, y) is drawn at (x·[scale] + [dx], y·[scale] + [dy]). Zoomed out, the picture is
 * centred; zoomed in, it is held so that it still covers the area on each side it is wider or taller than.
 */
class Viewport {
    private var picture = Size(1f, 1f)
    private var area = Box(0f, 0f, 1f, 1f)
    private var fitted = 1f

    /** How far the user has zoomed, from 1, the picture fitted, to [MOST_ZOOM]. */
    var zoom = 1f
        private set
    var dx = 0f
        private set
    var dy = 0f
        private set

    val scale: Float get() = fitted * zoom

    /** The picture [size] big, fitted into [into], unzoomed. */
    fun fit(
        size: Size,
        into: Box,
    ) {
        picture = size
        area = into
        fitted = min(into.width / size.width, into.height / size.height)
        zoom = 1f
        hold()
    }

    /** Zoomed by [factor] about ([focusX], [focusY]) on the screen, which stays over the same point of the picture. */
    fun zoomBy(
        factor: Float,
        focusX: Float,
        focusY: Float,
    ) {
        val before = scale
        zoom = (zoom * factor).coerceIn(1f, MOST_ZOOM)
        val by = scale / before
        dx = focusX - (focusX - dx) * by
        dy = focusY - (focusY - dy) * by
        hold()
    }

    /** Moved by [byX] and [byY] on the screen. */
    fun panBy(
        byX: Float,
        byY: Float,
    ) {
        dx += byX
        dy += byY
        hold()
    }

    /** [box] on the picture, as drawn on the screen. */
    fun toScreen(box: Box): Box =
        Box(box.left * scale + dx, box.top * scale + dy, box.right * scale + dx, box.bottom * scale + dy)

    fun toPictureX(screenX: Float): Float = (screenX - dx) / scale

    fun toPictureY(screenY: Float): Float = (screenY - dy) / scale

    private fun hold() {
        dx = held(dx, picture.width * scale, area.left, area.right)
        dy = held(dy, picture.height * scale, area.top, area.bottom)
    }

    /** An offset along one axis: centred in [low]..[high] when [drawn] fits there, else covering all of it. */
    private fun held(
        offset: Float,
        drawn: Float,
        low: Float,
        high: Float,
    ): Float = if (drawn <= high - low) (low + high - drawn) / 2 else offset.coerceIn(high - drawn, low)

    companion object {
        /** Eight times the fitted size: far enough to place a frame on a few pixels of a large photo. */
        const val MOST_ZOOM = 8f
    }
}
