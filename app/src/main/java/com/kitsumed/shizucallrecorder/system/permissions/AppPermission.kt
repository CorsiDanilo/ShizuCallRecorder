/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.system.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a permission that the app needs to function properly.
 */
sealed class AppPermission(
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector
) {

    /** Checks if the permission is granted.
     * @param context The context to use for checking the permission.
     * @return True if the permission is granted, false otherwise.
     */
    abstract fun isGranted(context: Context): Boolean

    /**
     * Checks if the permission/runtime permission is granted using the manifest string.
     * @param manifestString The manifest string of the permission to check. Can also be [android.Manifest.permission.READ_PHONE_STATE] constants.
     */
    class Runtime(
        val manifestString: String,
        titleResId: Int,
        descriptionResId: Int,
        icon: ImageVector
    ) : AppPermission(titleResId, descriptionResId, icon) {

        // Make the = operator compare only the permission itself (manifestString) instead of resource IDs and icon.
        override fun equals(other: Any?): Boolean = other is Runtime && manifestString == other.manifestString

        override fun hashCode(): Int = manifestString.hashCode()

        override fun isGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                manifestString
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the permission is granted using the AppOpsManager operation string.
     * @param opString The AppOpsManager operation string to check. Can also be [AppOpsManager.OPSTR_READ_PHONE_STATE] constants.
     * Note: Some OPSTR_* constants are only available in certain API levels or OEMs, other are hidden, and you may need to use the string directly.
     */
    class AppOp(
        val opString: String,
        titleResId: Int,
        descriptionResId: Int,
        icon: ImageVector
    ) : AppPermission(titleResId, descriptionResId, icon) {

        // Make the = operator compare only the permission itself (opString) instead of resource IDs and icon.
        override fun equals(other: Any?): Boolean = other is AppOp && opString == other.opString

        override fun hashCode(): Int = opString.hashCode()

        override fun isGranted(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                opString,
                Process.myUid(),
                context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}