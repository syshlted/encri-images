/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "mdpi")
class CropViewTest {
    private val view = CropView(ApplicationProvider.getApplicationContext())
    private val changes = mutableListOf<Edit>()
    private val placements = mutableListOf<Matrix>()

    /**
     * The view 1000 by 1000, with 24 of room on each side, showing a photo 4000 by 3000: drawn at 0.238 a pixel, from
     * (24, 143) to (976, 857).
     */
    private fun shown(edit: Edit = Edit(Size(4000f, 3000f))): CropView =
        view.apply {
            onEditChanged = { changes += it }
            onPlacementChanged = { placements += it }
            measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 1000, 1000)
            show(edit, null)
        }

    private val scale = 952f / 4000

    private var downTime = 0L

    private fun touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    /** One finger from ([fromX], [fromY]) to ([toX], [toY]), in steps. */
    private fun drag(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
    ) {
        touch(MotionEvent.ACTION_DOWN, fromX, fromY)
        for (step in 1..4) {
            touch(
                MotionEvent.ACTION_MOVE,
                fromX + (toX - fromX) * step / 4,
                fromY + (toY - fromY) * step / 4,
            )
        }
        touch(MotionEvent.ACTION_UP, toX, toY)
    }

    /**
     * Two fingers either side of a point, which goes from ([fromX], [fromY]) to ([toX], [toY]) while they go from
     * [fromSpan] apart to [toSpan]. The first finger is put down first, alone.
     */
    @Suppress("LongParameterList") // Where the fingers go, and how far apart.
    private fun twoFingers(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        fromSpan: Float,
        toSpan: Float,
    ) {
        val start = SystemClock.uptimeMillis()

        fun event(
            action: Int,
            step: Int,
            pointers: Int = 2,
        ): MotionEvent {
            val x = fromX + (toX - fromX) * step / STEPS
            val y = fromY + (toY - fromY) * step / STEPS
            val span = fromSpan + (toSpan - fromSpan) * step / STEPS
            val properties = Array(pointers) { MotionEvent.PointerProperties().apply { id = it } }
            val coords =
                Array(pointers) {
                    MotionEvent.PointerCoords().apply {
                        this.x = x + if (it == 0) -span / 2 else span / 2
                        this.y = y
                        pressure = 1f
                        size = 1f
                    }
                }
            val now = SystemClock.uptimeMillis()
            return MotionEvent.obtain(start, now, action, pointers, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        }
        val second = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0, pointers = 1))
        view.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or second, 0))
        for (step in 1..STEPS) view.onTouchEvent(event(MotionEvent.ACTION_MOVE, step))
        view.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP or second, STEPS))
        view.onTouchEvent(event(MotionEvent.ACTION_UP, STEPS, pointers = 1))
    }

    /** Two fingers either side of ([x], [y]), from [fromSpan] apart to [toSpan]. */
    private fun pinch(
        x: Float,
        y: Float,
        fromSpan: Float,
        toSpan: Float,
    ) = twoFingers(x, y, x, y, fromSpan, toSpan)

    private fun Matrix.translation(): Pair<Float, Float> =
        FloatArray(9).also(::getValues).let { it[Matrix.MTRANS_X] to it[Matrix.MTRANS_Y] }

    private fun Matrix.scaleX(): Float = FloatArray(9).also(::getValues)[Matrix.MSCALE_X]

    @Test
    fun `nothing is held before a picture is shown`() {
        assertNull(view.edit)
        assertEquals(false, view.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)))
    }

    @Test
    fun `the picture is fitted with room round it, and its place is told`() {
        shown()
        assertEquals(scale, placements.last().scaleX(), 0.0001f)
    }

    @Test
    fun `a corner dragged changes the crop, in the picture's pixels`() {
        shown()
        drag(24f, 143f, 224f, 343f)
        val crop = view.edit!!.crop
        assertEquals(200 / scale, crop.left, 1f)
        assertEquals(200 / scale, crop.top, 1f)
        assertEquals(4000f, crop.right, 0.01f)
        assertEquals(view.edit, changes.last())
    }

    @Test
    fun `a finger inside the frame moves it, and outside it moves nothing at this zoom`() {
        shown()
        drag(976f, 857f, 476f, 857f)
        val narrowed = view.edit!!.crop
        drag(300f, 500f, 400f, 500f)
        assertEquals(100 / scale, view.edit!!.crop.left, 1f)
        assertEquals(narrowed.width, view.edit!!.crop.width, 0.01f)
        val before = view.edit
        drag(990f, 990f, 900f, 900f)
        assertEquals(before, view.edit)
    }

    @Test
    fun `a frame held to a shape keeps it as a corner is dragged`() {
        shown()
        view.change(view.edit!!.heldTo(Aspect.SQUARE))
        val square = view.edit!!.crop
        val frame = Box(24f + square.left * scale, 143f, 24f + square.right * scale, 857f)
        drag(frame.right, frame.bottom, frame.right - 200f, frame.bottom - 50f)
        val dragged = view.edit!!.crop
        assertEquals(dragged.width, dragged.height, 0.5f)
        assertTrue(dragged.width < square.width)
    }

    @Test
    fun `two fingers zoom the picture under the frame, which is told where it went`() {
        shown()
        val crop = view.edit!!.crop
        pinch(500f, 500f, fromSpan = 200f, toSpan = 600f)
        assertTrue("zoomed in: ${placements.last().scaleX()}", placements.last().scaleX() > scale * 2)
        assertEquals("the crop is where it was on the picture", crop, view.edit!!.crop)
    }

    @Test
    fun `two fingers moving together move the picture and never the frame, even from inside it`() {
        shown()
        drag(976f, 857f, 676f, 657f)
        val crop = view.edit!!.crop
        twoFingers(fromX = 300f, fromY = 400f, toX = 400f, toY = 450f, fromSpan = 100f, toSpan = 100f)
        assertEquals("the finger put down first, inside the frame, does not move it", crop, view.edit!!.crop)

        pinch(500f, 500f, fromSpan = 200f, toSpan = 600f)
        val zoomed = placements.last().translation()
        twoFingers(fromX = 500f, fromY = 500f, toX = 400f, toY = 450f, fromSpan = 300f, toSpan = 300f)
        assertNotEquals("zoomed in, the picture moves", zoomed, placements.last().translation())
        assertEquals(crop, view.edit!!.crop)
    }

    @Test
    fun `a turn asked for fits the picture again`() {
        shown()
        pinch(500f, 500f, fromSpan = 200f, toSpan = 600f)
        view.change(view.edit!!.turnedClockwise())
        // Turned, 3000 by 4000 in 952 by 952, unzoomed: a pixel's x is its y, scaled.
        val values = FloatArray(9).also(placements.last()::getValues)
        assertEquals(-952f / 4000, values[Matrix.MSKEW_X], 0.0001f)
        assertEquals(952f / 4000, values[Matrix.MSKEW_Y], 0.0001f)
    }

    @Test
    fun `it draws the picture and its frame`() {
        val preview = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1000, 1000)
        view.show(Edit(Size(4000f, 3000f)), preview)
        touch(MotionEvent.ACTION_DOWN, 500f, 500f)
        view.draw(Canvas(Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)))
        touch(MotionEvent.ACTION_UP, 500f, 500f)
        view.draw(Canvas(Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)))
        assertEquals("a tap changes nothing", Edit(Size(4000f, 3000f)), view.edit)
    }

    private companion object {
        const val STEPS = 8
    }
}
