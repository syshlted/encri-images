/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

/**
 * What the user has asked of a photo or video so far (I22): [turns] quarter turns clockwise of the picture as it is
 * shown, [source] big, and then the part of the turned picture [crop] keeps, held to [aspect].
 *
 * The crop is in the turned picture's pixels, so a frame on the screen is the crop scaled, and a turn moves the crop
 * with the picture rather than leaving it where it was.
 */
data class Edit(
    val source: Size,
    val turns: Int = 0,
    val crop: Box = Box.of(source),
    val aspect: Aspect = Aspect.FREE,
) {
    /** The picture as it is turned now. */
    val picture: Size get() = if (turns % 2 == 0) source else source.turned

    /** Whether this edit would make a copy any different from the original. */
    val changed: Boolean get() = turns != 0 || !crop.near(Box.of(picture), SLACK)

    /** Where the turns place the picture as it is shown. */
    val turning: Placement get() = Placement.turns(turns, source)

    /** [crop] on the picture as it is shown, before any turn. */
    val cropOfSource: Box get() = turning.inverse.map(crop)

    /** A quarter turn more, clockwise, with the crop and its shape turned too. */
    fun turnedClockwise(): Edit =
        copy(
            turns = (turns + 1) % Placement.QUARTERS,
            crop = Frames.turned(crop, picture),
            aspect = aspect.turned,
        )

    /**
     * The crop held to [chosen]: the largest box of that shape, centred where the crop was as nearly as it fits.
     * [Aspect.FREE] keeps the crop as it is and lets each side go.
     */
    fun heldTo(chosen: Aspect): Edit {
        val ratio = chosen.ratioIn(picture) ?: return copy(aspect = chosen)
        return copy(aspect = chosen, crop = Frames.largest(picture, ratio, crop.centerX, crop.centerY))
    }

    /** Nothing asked: no turn, all of the picture, no shape. */
    fun reset(): Edit = Edit(source)

    private companion object {
        /** A crop within half a pixel of the whole picture keeps all of it. */
        const val SLACK = 0.5f
    }
}
