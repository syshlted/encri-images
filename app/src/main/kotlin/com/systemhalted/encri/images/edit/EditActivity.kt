/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.ext.getParcelable
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.vault.Copies
import com.systemhalted.encri.images.vault.LinkState
import com.systemhalted.encri.images.vault.VaultHost
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Crop and rotate (I22): a photo or video, from the viewer, cropped to a frame the user drags and zooms under, turned
 * a quarter at a time, and kept as a copy beside it. The original is never changed.
 *
 * Not exported: it takes the photo or video from this app's own extras, as the viewer does (I3). Like the viewer it
 * closes when the vault locks or the session goes, since what it has open goes on serving across a lock (encri D103).
 */
class EditActivity : AppCompatActivity(R.layout.activity_edit) {
    private val host by lazy { application as VaultHost }
    private val media by lazy { intent.extras?.getParcelable(EXTRA_MEDIA, Media::class) }
    private val making by lazy { Making(this, host.link, requireNotNull(media)) }

    private val cropView by lazy { findViewById<CropView>(R.id.cropView) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val aspects by lazy { findViewById<ChipGroup>(R.id.aspects) }
    private val resetButton by lazy { findViewById<MaterialButton>(R.id.resetButton) }
    private val saveItem: MenuItem by lazy { toolbar.menu.findItem(R.id.save) }

    private var stage: VideoStage? = null
    private var preview: Bitmap? = null

    private val saveFlow by lazy {
        SaveFlow(
            this,
            requireNotNull(media),
            Copies(host.link, host.library),
            lifecycleScope,
            ::askEncri,
            { onProgress ->
                stage?.pause()
                making.make(cropView.edit ?: throw IOException("nothing to save"), onProgress)
            },
            { saved ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_SAVED, saved))
                finish()
            },
        )
    }

    // encri learns who asked from getCallingPackage, which only a start for a result sets (encri D120).
    private val askEncriForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            saveFlow.answered(it.resultCode, it.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val media = media?.takeIf(::canEdit)
        if (media == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editRoot)) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.save) saveFlow.offer()
            it.itemId == R.id.save
        }
        aspects.setOnCheckedStateChangeListener { _, checked ->
            val chosen = checked.firstOrNull()?.let(ASPECTS::get) ?: return@setOnCheckedStateChangeListener
            cropView.edit?.takeIf { it.aspect != chosen }?.let { change(it.heldTo(chosen)) }
        }
        findViewById<View>(R.id.rotateButton).setOnClickListener { cropView.edit?.let { change(it.turnedClockwise()) } }
        resetButton.setOnClickListener { cropView.edit?.let { change(it.reset()) } }
        cropView.onEditChanged = { changed() }
        changed()

        lifecycleScope.launch { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { leaveWhenTheVaultCloses() } }
        show(media)
    }

    override fun onPause() {
        stage?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        stage?.release()
        stage = null
        preview?.recycle()
        preview = null
        super.onDestroy()
    }

    /** [edit], asked for by a button: the frame is shown changed, and the shape chosen is the one it is held to. */
    private fun change(edit: Edit) {
        cropView.change(edit)
        ASPECTS.entries.firstOrNull { it.value == edit.aspect }?.let { aspects.check(it.key) }
    }

    /** Nothing to save, or to put back, until something is asked. */
    private fun changed() {
        val changed = cropView.edit?.changed == true
        saveItem.isEnabled = changed
        resetButton.isEnabled = changed
    }

    /** A photo drawn by the frame's view from a preview; a video drawn behind it, where the frame says. */
    private fun show(media: Media) {
        val loading = findViewById<View>(R.id.loading)
        val cannotOpen = {
            Toast.makeText(this, R.string.edit_cannot_open, Toast.LENGTH_SHORT).show()
            finish()
        }
        if (media.mediaType == MediaType.VIDEO) {
            val surface = findViewById<TextureView>(R.id.videoView).apply { isVisible = true }
            val button = findViewById<MaterialButton>(R.id.playButton).apply { isVisible = true }
            val stage = VideoStage(this, host.link, surface, button).also { stage = it }
            cropView.onPlacementChanged = { placement -> cropView.edit?.let { stage.place(placement, it.source) } }
            stage.show(media.uri, { size ->
                if (cropView.edit == null) {
                    cropView.show(Edit(size), null)
                    loading.isVisible = false
                    changed()
                }
            }, cannotOpen)
            return
        }
        lifecycleScope.launch {
            val shown =
                try {
                    making.preview()
                } catch (_: IOException) {
                    cannotOpen()
                    return@launch
                }
            preview = shown.bitmap
            cropView.show(Edit(shown.size), shown.bitmap)
            loading.isVisible = false
            changed()
        }
    }

    private fun askEncri(intent: Intent) {
        try {
            askEncriForResult.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // encri was uninstalled while this screen showed; the link says so once it asks again.
            host.link.open()
        }
    }

    private suspend fun leaveWhenTheVaultCloses() {
        host.link.open()
        host.link.state.collect { state ->
            if (state is LinkState.Refused || (state as? LinkState.Open)?.locked == true) finish()
        }
    }

    companion object {
        private val EXTRA_MEDIA = "${EditActivity::class.qualifiedName}.media"
        private val EXTRA_SAVED = "${EditActivity::class.qualifiedName}.saved"
        private const val GIF = "image/gif"

        private val ASPECTS =
            mapOf(
                R.id.aspectFree to Aspect.FREE,
                R.id.aspectOriginal to Aspect.ORIGINAL,
                R.id.aspectSquare to Aspect.SQUARE,
                R.id.aspect4by3 to Aspect.FOUR_BY_THREE,
                R.id.aspect3by4 to Aspect.THREE_BY_FOUR,
                R.id.aspect16by9 to Aspect.SIXTEEN_BY_NINE,
                R.id.aspect9by16 to Aspect.NINE_BY_SIXTEEN,
            )

        /**
         * Whether [media] can be cropped and turned here: a photo, but for a GIF, whose copy would keep one frame of
         * it, or a video.
         */
        fun canEdit(media: Media): Boolean =
            when (media.mediaType) {
                MediaType.IMAGE -> !media.mimeType.equals(GIF, ignoreCase = true)
                MediaType.VIDEO -> true
                else -> false
            }

        /** The editor for [media], to be started for a result. */
        fun intentFor(
            context: Context,
            media: Media,
        ): Intent = Intent(context, EditActivity::class.java).putExtra(EXTRA_MEDIA, media)

        /** The copy the editor kept, from its [result], or null when it kept none. */
        fun savedIn(result: ActivityResult): Uri? =
            result.data
                ?.takeIf { result.resultCode == RESULT_OK }
                ?.extras
                ?.getParcelable(EXTRA_SAVED, Uri::class)
    }
}
