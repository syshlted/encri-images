/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * The picture being edited, with the frame of its crop over it (I22).
 *
 * One finger on a corner or a side of the frame drags it, inside the frame moves it, and outside it moves the
 * picture; two fingers zoom the picture under the frame, so it can be placed on a detail, and a double tap fits the
 * picture again. A photo is drawn here, from a preview; a video is drawn by a `TextureView` behind this view, which
 * [onPlacementChanged] tells where to put its pixels.
 */
class CropView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        /** The edit as it stands, or null before a picture is shown. */
        var edit: Edit? = null
            private set

        /** Told of every change to the edit, a finger's or a button's. */
        var onEditChanged: (Edit) -> Unit = {}

        /** Told where the picture as it is shown, unturned and in its own pixels, is drawn on this view. */
        var onPlacementChanged: (Matrix) -> Unit = {}

        private var picture: Bitmap? = null
        private val viewport = Viewport()
        private val density = resources.displayMetrics.density
        private val painter = FramePainter(density)
        private val placement = Matrix()
        private val drawn = Matrix()
        private val smooth = Paint(Paint.FILTER_BITMAP_FLAG)

        private var grip: Grip? = null
        private var lastX = 0f
        private var lastY = 0f

        private val pinches =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        grip = Grip.Pinching(detector.focusX, detector.focusY)
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val pinching = grip as? Grip.Pinching ?: return false
                        viewport.zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                        viewport.panBy(detector.focusX - pinching.focusX, detector.focusY - pinching.focusY)
                        grip = Grip.Pinching(detector.focusX, detector.focusY)
                        placed()
                        return true
                    }
                },
            )

        private val taps =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        refit()
                        return true
                    }
                },
            )

        /** [edit] of a picture drawn from [preview], upright as shown; null when a video is drawn behind this view. */
        fun show(
            edit: Edit,
            preview: Bitmap?,
        ) {
            picture = preview
            this.edit = edit
            refit()
        }

        /**
         * [changed], asked for by a button rather than a finger. A turn changes the picture's shape, so it is fitted
         * again; anything else keeps the zoom.
         */
        fun change(changed: Edit) {
            val before = edit
            edit = changed
            if (before == null || before.picture != changed.picture || before.turns != changed.turns) {
                refit()
            } else {
                invalidate()
            }
            onEditChanged(changed)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            refit()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val edit = edit ?: return
            picture?.let {
                drawn.setScale(edit.source.width / it.width, edit.source.height / it.height)
                drawn.postConcat(placement)
                canvas.drawBitmap(it, drawn, smooth)
            }
            painter.draw(canvas, viewport.toScreen(edit.crop), width.toFloat(), height.toFloat(), grip != null)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val edit = edit ?: return false
            pinches.onTouchEvent(event)
            taps.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    grip = gripAt(edit, event.x, event.y)
                    lastX = event.x
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }

                // A second finger ends whatever the first held, so the first's way there does not move the frame; a
                // finger lifted leaves the rest moving the picture from where they are.
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    grip = Grip.Pinching(pinches.focusX, pinches.focusY)
                }

                MotionEvent.ACTION_MOVE -> {
                    drag(edit, event.x, event.y)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP && grip is Grip.Panning) performClick()
                    grip = null
                    invalidate()
                }
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()

        /** What a finger put down at ([x], [y]) takes hold of. */
        private fun gripAt(
            edit: Edit,
            x: Float,
            y: Float,
        ): Grip {
            val frame = viewport.toScreen(edit.crop)
            val handle = Frames.handleAt(frame, x, y, REACH_DP * density)
            return when {
                handle != null -> {
                    // Where the finger is from the corner or side it holds, so the frame does not jump to the finger.
                    val heldX = if (handle.movesLeft) frame.left else frame.right
                    val heldY = if (handle.movesTop) frame.top else frame.bottom
                    Grip.Resizing(handle, heldX - x, heldY - y)
                }

                frame.contains(x, y) -> {
                    Grip.Moving
                }

                else -> {
                    Grip.Panning
                }
            }
        }

        /** A finger that holds [grip] moved to ([x], [y]); fingers that hold the picture move it. */
        private fun drag(
            edit: Edit,
            x: Float,
            y: Float,
        ) {
            val by = viewport.scale
            val crop =
                when (val held = grip) {
                    is Grip.Resizing -> {
                        Frames.resized(
                            edit.crop,
                            held.handle,
                            viewport.toPictureX(x + held.offsetX),
                            viewport.toPictureY(y + held.offsetY),
                            edit.picture,
                            edit.aspect.ratioIn(edit.picture),
                            SMALLEST_DP * density / by,
                        )
                    }

                    Grip.Moving -> {
                        edit.crop.movedWithin((x - lastX) / by, (y - lastY) / by, edit.picture)
                    }

                    Grip.Panning -> {
                        viewport.panBy(x - lastX, y - lastY)
                        placed()
                        null
                    }

                    // Two fingers that are not pinching move the picture, by the point between them.
                    is Grip.Pinching -> {
                        if (!pinches.isInProgress) {
                            viewport.panBy(pinches.focusX - held.focusX, pinches.focusY - held.focusY)
                            grip = Grip.Pinching(pinches.focusX, pinches.focusY)
                            placed()
                        }
                        null
                    }

                    null -> {
                        null
                    }
                }
            lastX = x
            lastY = y
            if (crop != null && crop != edit.crop) {
                this.edit = edit.copy(crop = crop).also(onEditChanged)
                invalidate()
            }
        }

        /** The picture fitted into this view, unzoomed, with room around it for a finger on the frame's edge. */
        private fun refit() {
            val edit = edit ?: return
            if (width == 0 || height == 0) return
            val margin = REACH_DP * density
            viewport.fit(edit.picture, Box(margin, margin, width - margin, height - margin))
            placed()
        }

        private fun placed() {
            val edit = edit ?: return
            placement.setValues(edit.turning.values())
            placement.postScale(viewport.scale, viewport.scale)
            placement.postTranslate(viewport.dx, viewport.dy)
            onPlacementChanged(Matrix(placement))
            invalidate()
        }

        /** What a finger on this view holds. */
        private sealed interface Grip {
            /** [handle], with the finger [offsetX] and [offsetY] from it. */
            data class Resizing(
                val handle: Handle,
                val offsetX: Float,
                val offsetY: Float,
            ) : Grip

            data object Moving : Grip

            data object Panning : Grip

            /** Two fingers, last centred on ([focusX], [focusY]). */
            data class Pinching(
                val focusX: Float,
                val focusY: Float,
            ) : Grip
        }

        private companion object {
            /** How near a corner or side a finger must land to take hold of it, and the room left round the picture. */
            const val REACH_DP = 24f

            /** The smallest a frame can be dragged to on the screen; zoomed in, fewer of the picture's pixels. */
            const val SMALLEST_DP = 48f
        }
    }
