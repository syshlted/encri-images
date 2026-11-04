/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.vault.Gate
import com.systemhalted.encri.images.vault.GateAction
import com.systemhalted.encri.images.vault.PrivacySettings
import com.systemhalted.encri.images.vault.VaultGlide
import com.systemhalted.encri.images.vault.gateOf
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private val images by lazy { application as ImagesApplication }

    // Views
    private val gateButton by lazy { findViewById<Button>(R.id.gateButton) }
    private val gateMessage by lazy { findViewById<TextView>(R.id.gateMessage) }
    private val gateView by lazy { findViewById<View>(R.id.gateView) }
    private val navHost by lazy { findViewById<View>(R.id.navHostFragment) }

    // What the grids were drawn with (I13): a cell whose preview was not asked for does not ask again by itself.
    private var previewsShown = true

    // encri learns who asked from getCallingPackage, which only a start for a result sets (encri D120).
    private val askEncri =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            images.link.open()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()

        previewsShown = PrivacySettings.of(this).previewsShown
        // The screens drawn before previews went off have let theirs go into Glide's memory by now (I13).
        if (!previewsShown) {
            VaultGlide.forget(this)
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                images.link.state.collect { render(gateOf(it)) }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Previews turned on or off in settings: every grid is drawn again, asking for them or not.
        if (PrivacySettings.of(this).previewsShown != previewsShown) {
            recreate()
            return
        }

        // encri's notice that a folder changed is best effort, so a screen coming back asks.
        images.link.open()
        images.library.reload()
    }

    private fun render(gate: Gate?) {
        // Nothing of the vault is on screen behind the gate, so a lock takes it all away at once.
        navHost.isVisible = gate == null
        gateView.isVisible = gate != null

        gate ?: return
        gateMessage.setText(gate.message)
        gateButton.isVisible = gate.action != null
        gate.action?.let { action ->
            gateButton.setText(action.label)
            gateButton.setOnClickListener { act(action) }
        }
    }

    private fun act(action: GateAction) {
        when (action) {
            // Reading alone: this version shows the vault and changes nothing (I3).
            GateAction.ALLOW -> ask(Encri.authorizationIntent(setOf(Right.READ)))
            GateAction.UNLOCK -> ask(Encri.unlockIntent())
            GateAction.RETRY -> images.link.open()
        }
    }

    private fun ask(intent: Intent) {
        try {
            askEncri.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // encri was uninstalled between the refusal and the tap; asking again says so.
            images.link.open()
        }
    }
}
