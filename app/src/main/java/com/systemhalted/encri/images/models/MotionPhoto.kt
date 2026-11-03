/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.models

import java.nio.ByteBuffer

data class MotionPhoto(
    val metadata: Metadata,
    val videoBuffer: ByteBuffer,
) {
    data class Metadata(
        val version: Int,
        val presentationTimestampUs: Long? = null,
        val videoLength: Int,
        val videoMimeType: String,
    )
}
