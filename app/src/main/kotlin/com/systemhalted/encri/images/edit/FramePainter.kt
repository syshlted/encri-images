/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint

/**
 * How a crop's frame is drawn over the picture (I22): what it leaves out dimmed, the frame itself a dotted line over
 * a darker one so it shows on white as well as on black, a mark at each corner and side to take hold of, and while a
 * finger is on it, lines at its thirds.
 */
class FramePainter(
    density: Float,
) {
    private val dim =
        Paint().apply {
            color = DIM
            style = Paint.Style.FILL
        }
    private val under =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UNDER
            style = Paint.Style.STROKE
            strokeWidth = LINE_DP * 2 * density
        }
    private val dotted =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = LINE_DP * density
            pathEffect = DashPathEffect(floatArrayOf(DASH_DP * density, GAP_DP * density), 0f)
        }
    private val thirds =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = THIRDS
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    private val marks =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = MARK_DP * density
            strokeCap = Paint.Cap.SQUARE
        }
    private val mark = MARK_LENGTH_DP * density

    /** [frame] drawn on [canvas], which is [width] by [height]; with its thirds when [held]. */
    fun draw(
        canvas: Canvas,
        frame: Box,
        width: Float,
        height: Float,
        held: Boolean,
    ) {
        canvas.drawRect(0f, 0f, width, frame.top, dim)
        canvas.drawRect(0f, frame.bottom, width, height, dim)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dim)
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, dim)
        if (held) drawThirds(canvas, frame)
        canvas.drawRect(frame.left, frame.top, frame.right, frame.bottom, under)
        canvas.drawRect(frame.left, frame.top, frame.right, frame.bottom, dotted)
        drawMarks(canvas, frame)
    }

    private fun drawThirds(
        canvas: Canvas,
        frame: Box,
    ) {
        for (third in 1..2) {
            val x = frame.left + frame.width * third / THIRD
            val y = frame.top + frame.height * third / THIRD
            canvas.drawLine(x, frame.top, x, frame.bottom, thirds)
            canvas.drawLine(frame.left, y, frame.right, y, thirds)
        }
    }

    /** An L at each corner, and a bar at the middle of each side. */
    private fun drawMarks(
        canvas: Canvas,
        frame: Box,
    ) {
        val across = minOf(mark, frame.width / 2)
        val down = minOf(mark, frame.height / 2)
        for (x in listOf(frame.left, frame.right)) {
            val inward = if (x == frame.left) across else -across
            for (y in listOf(frame.top, frame.bottom)) {
                val downward = if (y == frame.top) down else -down
                canvas.drawLine(x, y, x + inward, y, marks)
                canvas.drawLine(x, y, x, y + downward, marks)
            }
        }
        canvas.drawLine(frame.centerX - across / 2, frame.top, frame.centerX + across / 2, frame.top, marks)
        canvas.drawLine(frame.centerX - across / 2, frame.bottom, frame.centerX + across / 2, frame.bottom, marks)
        canvas.drawLine(frame.left, frame.centerY - down / 2, frame.left, frame.centerY + down / 2, marks)
        canvas.drawLine(frame.right, frame.centerY - down / 2, frame.right, frame.centerY + down / 2, marks)
    }

    private companion object {
        const val DIM = 0x99000000.toInt()
        const val UNDER = 0x66000000
        const val THIRDS = 0x80FFFFFF.toInt()
        const val LINE_DP = 1.5f
        const val DASH_DP = 6f
        const val GAP_DP = 4f
        const val MARK_DP = 3f
        const val MARK_LENGTH_DP = 18f
        const val THIRD = 3
    }
}
