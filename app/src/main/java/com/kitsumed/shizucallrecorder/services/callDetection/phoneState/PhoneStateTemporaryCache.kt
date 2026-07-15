/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.callDetection.phoneState

import android.content.Context
import androidx.core.content.edit
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.RawCallData
import com.kitsumed.shizucallrecorder.services.callDetection.phoneState.PhoneStateTemporaryCache.Companion.MAX_AGE_MS
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * Handles the Ephemeral State Persistence (Temporary Cache).
 * Used to save call metadata during the RINGING state and restore it during OFFHOOK.
 * in case the Android system kills our app process while waiting for the user to answer.
 *
 * This workaround could cause some edges cases. The user receving a ringing call, the app die, the user make an outgoing call, but the app restore the ringing state.
 * The "fix" is to only keep a direction valid for a short period of time [MAX_AGE_MS]. It reduces the chances of this happening.
 */
class PhoneStateTemporaryCache(private val context: Context) {

    companion object {
        private const val PREFS_CACHE = "CallSessionManagerTemporaryCache"
        private const val KEY_CACHE_DIRECTION = "cache_direction"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"

        private const val KEY_ACTIVE_PHONE_NUMBER = "active_phone_number"
        private const val KEY_ACTIVE_DIRECTION = "active_direction"
        private const val KEY_ACTIVE_CALLER_NAME = "active_caller_name"
        private const val KEY_ACTIVE_PACKAGE_NAME = "active_package_name"

        // From online research, Phone Carriers allow a limit of up to 30s ringing time. 34s to be safe.
        private const val MAX_AGE_MS = 34000L
    }

    /**
     * Persist the direction asynchronously.
     * Android often kills background processes before the user answers (within 5-10s).
     * This ensures we don't lose the call direction while waiting for the OFFHOOK state.
     */
    fun save(direction: CallDirection?) {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit {
            putString(KEY_CACHE_DIRECTION, direction?.token)
            putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Attempt to recover direction in case the process was killed during the RINGING state.
     * @return The restored [CallDirection] if valid and not stale, or null if no valid cache exists.
     */
    fun restore(): CallDirection? {
        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)

        if (timestamp <= 0) return null

        val savedDirToken = prefs.getString(KEY_CACHE_DIRECTION, "")

        // Restore if data is less than MAX_AGE_MS seconds old
        if (System.currentTimeMillis() - timestamp <= MAX_AGE_MS) {
            val restoredDir = savedDirToken?.let { CallDirection.fromToken(it) }
            if (restoredDir != null) {
                AppLogger.d( "Restored direction (${restoredDir.token}) from TemporaryCache (process was killed during RINGING).")
                return restoredDir
            }
        } else clear() // Clear stale data if it's old
        return null
    }

    /**
     * Persists the active call metadata to handle process death during an ongoing call.
     */
    fun saveActiveSession(metadata: RawCallData) {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit {
            putString(KEY_ACTIVE_PHONE_NUMBER, metadata.rawPhoneNumber)
            putString(KEY_ACTIVE_DIRECTION, metadata.direction.token)
            putString(KEY_ACTIVE_CALLER_NAME, metadata.osProvidedCallerName)
            putString(KEY_ACTIVE_PACKAGE_NAME, metadata.packageName)
        }
    }

    /**
     * Restores the active call metadata in case of process death during a call.
     */
    fun restoreActiveSession(): RawCallData? {
        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val directionToken = prefs.getString(KEY_ACTIVE_DIRECTION, null) ?: return null
        val direction = CallDirection.fromToken(directionToken) ?: return null
        val rawPhoneNumber = prefs.getString(KEY_ACTIVE_PHONE_NUMBER, "") ?: ""
        val callerName = prefs.getString(KEY_ACTIVE_CALLER_NAME, null)
        val packageName = prefs.getString(KEY_ACTIVE_PACKAGE_NAME, null)
        return RawCallData(rawPhoneNumber, direction, callerName, packageName)
    }

    /**
     * Clears the active call session cache.
     */
    fun clearActiveSession() {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit {
            remove(KEY_ACTIVE_PHONE_NUMBER)
            remove(KEY_ACTIVE_DIRECTION)
            remove(KEY_ACTIVE_CALLER_NAME)
            remove(KEY_ACTIVE_PACKAGE_NAME)
        }
    }

    /**
     * Blindly clear the temporary cache storage so no stale data is left behind.
     */
    fun clear() {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit { clear() }
    }
}