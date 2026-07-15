/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.call

import android.net.Uri

/**
 * Represents a single recorded audio file found in the user's recording folder.
 *
 * @param uri           SAF URI of the file (use with ContentResolver).
 * @param name          Filename without extension.
 * @param extension     File extension without leading dot (e.g. "opus").
 * @param sizeBytes     File size in bytes.
 * @param lastModified  Last-modified timestamp in epoch milliseconds (used for sorting).
 * @param durationMs    Audio duration in milliseconds, or null if unreadable.
 * @param direction     Call direction inferred from the filename ("in" → INCOMING,
 *                      "out" → OUTGOING), or null if the pattern is not found.
 */
data class RecordingFile(
    val uri: Uri,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val durationMs: Long?,
    val direction: CallDirection?
)
