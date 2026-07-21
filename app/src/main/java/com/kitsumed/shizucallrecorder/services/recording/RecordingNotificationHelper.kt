/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.ui.theme.Green40

class RecordingNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_SERVICE = "recording_channel_service"
        const val CHANNEL_ID_ERROR = "recording_channel_error"
        const val CHANNEL_ID_POST_RECORDING_FILE_ACTIONS = "recording_channel_post_recording_file_actions"

        const val SERVICE_NOTIFICATION_ID = 1
        const val ERROR_NOTIFICATION_ID = 2
        // Post-call notifications use a unique ID per recording so they don't overwrite each other.
        // IDs are derived from the current time, offset above the fixed IDs to avoid collisions.
        private const val POST_RECORDING_BASE_ID = 1000
        private const val REQUEST_CODE_OPEN_RECORDING = 1001
        private const val REQUEST_CODE_SHARE_RECORDING = 1002
        private const val REQUEST_CODE_DELETE_RECORDING = 1003

        /**
         * Generates a stable, unique notification ID for a post-call notification.
         * Uses the current timestamp modulo a safe range to avoid Int overflow.
         */
        fun generatePostCallNotificationId(): Int {
            return POST_RECORDING_BASE_ID + (System.currentTimeMillis() % 100_000).toInt()
        }
    }

    /**
     * Creates the Android notification channel for recording notifications.
     */

    fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Recording Group
        val groupId = "recording_channel_group"
        val group = NotificationChannelGroup(groupId, "Recording Group")
        manager.createNotificationChannelGroup(group)

        // Recording Service Channel
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE, "Foreground Recording Service", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            this.group = groupId
            // Alert channel should be visible but we handle vibration manually
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(serviceChannel)

        // Error Channel
        val errorChannel = NotificationChannel(
            CHANNEL_ID_ERROR, "Recording Errors", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            this.group = groupId
            enableVibration(false) // We handle vibration manually
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(errorChannel)

        // Post Recording File Actions Channel
        val postCallChannel = NotificationChannel(CHANNEL_ID_POST_RECORDING_FILE_ACTIONS,
            "Post-Call Quick Actions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            this.group = groupId
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(postCallChannel)
    }

    /**
     * Creates a notification for the recording service based on the current state.
     * @param state The current state of the recording service.
     * @return A Notification object that can be used to update the foreground service notification.
     */
    fun getServiceNotification(state: RecordingServiceState): Notification {
        val titleRes: Int
        val contentRes: Int
        val actionIcon: Int?
        val actionText: String?
        val actionIntentAction: String?
        
        var discardActionIcon: Int? = null
        var discardActionText: String? = null
        var discardActionIntentAction: String? = null

        // We want to show the cross-country tip if we are unsure about the metadata, as it is better to be safe than sorry.
        val subRes: Int = if (state.metadata == null || state.metadata?.isCrossCountry == true) R.string.recording_notification_cross_country_tip else R.string.recording_notification_current_country_tip

        when (state) {
            is RecordingServiceState.Starting -> {
                titleRes = R.string.recording_standby_notification_title
                contentRes = R.string.recording_notification_waiting_shizuku
                actionIcon = null
                actionText = null
                actionIntentAction = null
            }
            is RecordingServiceState.Active -> {
                if (state.isPaused) {
                    titleRes = R.string.recording_standby_notification_title
                    contentRes = R.string.recording_notification_press_to_resume
                    actionIcon = R.drawable.ic_stop
                    actionText = context.getString(R.string.general_resume)
                    actionIntentAction = RecordingForegroundService.ACTION_RESUME_RECORDING
                } else {
                    titleRes = R.string.recording_notification_title
                    contentRes = R.string.recording_notification_press_to_pause
                    actionIcon = R.drawable.ic_mic
                    actionText = context.getString(R.string.general_pause)
                    actionIntentAction = RecordingForegroundService.ACTION_PAUSE_RECORDING
                }
                discardActionIcon = R.drawable.ic_stop
                discardActionText = context.getString(R.string.general_discard)
                discardActionIntentAction = RecordingForegroundService.ACTION_DISCARD_RECORDING
            }
            else -> {
                titleRes = R.string.recording_standby_notification_title
                contentRes = R.string.recording_notification_press_to_start
                actionIcon = R.drawable.ic_mic
                actionText = context.getString(R.string.general_record)
                actionIntentAction = RecordingForegroundService.ACTION_MANUAL_START
            }
        }

        // The delete intent is triggered when the user dismisses the notification (Thanks Android 14+).
        val deletePendingIntent = PendingIntent.getService(
            context, 99,
            Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_NOTIFICATION_DISMISSED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(contentRes))
            .setSubText(context.getString(subRes))
            .setOngoing(true) // Almost useless starting Android 14+ :)
            .setDeleteIntent(deletePendingIntent) // Android 14+ workaround :)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Nothing sensible here, and we want to show it on lockscreen.
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(Green40.toArgb())
            .setColorized(state is RecordingServiceState.Active && !state.isPaused)
            .setSilent(state is RecordingServiceState.Active || state is RecordingServiceState.Starting) // Don't do a screen-incursion if we are already recording.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (actionText != null && actionIntentAction != null && actionIcon != null) {
            val actionPendingIntent = PendingIntent.getService(
                context, 1,
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = actionIntentAction
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(actionIcon, actionText, actionPendingIntent)
        }

        if (discardActionText != null && discardActionIntentAction != null && discardActionIcon != null) {
            val discardPendingIntent = PendingIntent.getService(
                context, 2,
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = discardActionIntentAction
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(discardActionIcon, discardActionText, discardPendingIntent)
        }

        return builder.build()
    }

    /**
     * Handles showing toasts for state changes.  It determines a toast based on the old and new state.
     * @param oldState The previous state of the recording service.
     * @param newState The new state of the recording service.
     */
    fun handleStateChangeToasts(oldState: RecordingServiceState, newState: RecordingServiceState) {
        if (oldState == newState) return // Ignore duplicates

        when (newState) {
            is RecordingServiceState.Standby -> {
                if (newState.metadata == null) {
                    showToast(context.getString(R.string.recording_toast_ended))
                    vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), intArrayOf(0, 64, 0, 128), -1))
                } else if (oldState !is RecordingServiceState.Standby) {
                    val dirLabel = newState.metadata.direction.labelResId.let { context.getString(it) }
                    showToast(context.getString(R.string.recording_toast_standby, dirLabel))
                }
            }
            is RecordingServiceState.Active -> {
                val wasActive = oldState is RecordingServiceState.Active
                val wasPaused = wasActive && (oldState as RecordingServiceState.Active).isPaused

                if (newState.isPaused && (!wasActive || !wasPaused)) {
                    // Recording was paused
                    showToast(context.getString(R.string.recording_toast_paused))
                    vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), intArrayOf(0, 64, 0, 128), -1))
                } else if (!newState.isPaused && wasPaused) {
                    // Recording was resumed
                    showToast(context.getString(R.string.recording_toast_resumed))
                    vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), intArrayOf(0, 64, 0, 128), -1))
                } else if (!newState.isPaused && !wasActive) {
                    // Recording was started
                    showToast(context.getString(R.string.recording_started))
                    vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), intArrayOf(0, 64, 0, 128), -1))
                }
            }
            else -> {}
        }
    }

    /**
     * Shows a notification after a call recording has been completed, allowing the user to play, share, or delete the recording.
     * @param fileUri The URI of the recorded audio file.
     * @param callMetadata Metadata about the recorded call.
     */
    fun showPostCallNotification(fileUri: Uri, callMetadata: EnrichedCallData) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId = generatePostCallNotificationId()

        // Use per-notification request codes to avoid PendingIntent collisions across simultaneous sessions.
        val playRequestCode = notificationId + 1
        val shareRequestCode = notificationId + 2
        val deleteRequestCode = notificationId + 3

        // Play action
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "audio/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION // Allows the receiving app to read the SAF file
        }
        val playPendingIntent = PendingIntent.getActivity(
            context, playRequestCode, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Share action
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooserIntent = Intent.createChooser(shareIntent, null)
        val sharePendingIntent = PendingIntent.getActivity(
            context, shareRequestCode, chooserIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Delete action (Triggers our DeleteDialogConfirmationActivity)
        val deleteIntent = Intent(context, DeleteDialogConfirmationActivity::class.java).apply {
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(DeleteDialogConfirmationActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deletePendingIntent = PendingIntent.getActivity(
            context, deleteRequestCode, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_POST_RECORDING_FILE_ACTIONS)
            .setSmallIcon(R.drawable.ic_audio_file)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(context.getString(R.string.post_recording_notification_title))
            .setContentText(callMetadata.getBestNumber().takeIf { it.isNotEmpty() } ?: context.getString(R.string.post_recording_notification_unknown_caller))
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, context.getString(R.string.general_play), playPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, context.getString(R.string.general_share), sharePendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, context.getString(R.string.general_delete), deletePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Shows a notification when a recording session ended but no valid file was produced (e.g., Shizuku disconnected mid-call).
     * The notification has no file actions — it is purely informational and can be dismissed.
     * @param callMetadata Metadata about the call that failed to produce a recording (may be null if we never received the call details).
     */
    fun showFailedRecordingNotification(callMetadata: EnrichedCallData?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId = generatePostCallNotificationId()

        val callerText = callMetadata?.getBestNumber()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.post_recording_notification_unknown_caller)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_POST_RECORDING_FILE_ACTIONS)
            .setSmallIcon(R.drawable.ic_outline_error)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(context.getString(R.string.post_recording_failed_notification_title))
            .setContentText(callerText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                context.getString(R.string.post_recording_failed_notification_body, callerText)
            ))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Shows a short Toast message on the UI thread.
     */
    fun showToast(message: String) {
        if (!AppPreferences(context).isShowToastsEnabled()) return

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Posts an error notification visible.
     *
     * @param message Human-readable error description to show in the notification body.
     */
    fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setSmallIcon(R.drawable.ic_outline_error)
            .setContentTitle(context.getString(R.string.recording_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ERROR_NOTIFICATION_ID, notification)
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 800), intArrayOf(0, 46, 184), -1))
    }

    /**
     * Posts an error notification with an action button to resume recording if Shizuku or recording failed.
     *
     * @param message Error description to show in notification body.
     * @param metadata Metadata of the interrupted call session.
     */
    fun showErrorNotificationWithResume(message: String, metadata: EnrichedCallData?) {
        val resumeIntent = Intent(context, RecordingActionReceiver::class.java).apply {
            action = RecordingActionReceiver.ACTION_RESUME_AFTER_ERROR
            if (metadata != null) {
                putExtra(EnrichedCallData.EXTRA_METADATA, metadata)
            }
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setSmallIcon(R.drawable.ic_outline_error)
            .setContentTitle(context.getString(R.string.recording_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_mic,
                context.getString(R.string.recording_action_resume),
                resumePendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(ERROR_NOTIFICATION_ID, notification)
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 800), intArrayOf(0, 46, 184), -1))
    }

    /**
     * Triggers a vibration if enabled in settings.
     */
    fun vibrate(effect: VibrationEffect) {
        if (!AppPreferences(context).isVibrationEnabled()) return

        if (Build.VERSION.SDK_INT >= 31) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            if (manager.defaultVibrator.hasVibrator()) {
                manager.defaultVibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(effect)
            }
        }
    }
}
