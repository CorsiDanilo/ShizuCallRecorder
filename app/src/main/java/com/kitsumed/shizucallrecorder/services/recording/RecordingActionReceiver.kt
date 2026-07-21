package com.kitsumed.shizucallrecorder.services.recording

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.services.callDetection.phoneState.PhoneStateTemporaryCache
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecordingActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SCR:RecordingActionReceiver"
        const val ACTION_DELETE_SAVED_RECORDING = "com.kitsumed.shizucallrecorder.DELETE_SAVED_RECORDING"
        const val ACTION_RESUME_AFTER_ERROR = "com.kitsumed.shizucallrecorder.RESUME_AFTER_ERROR"
        const val EXTRA_RECORDING_URI = "recording_uri"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DELETE_SAVED_RECORDING -> {
                val uriString = intent.getStringExtra(EXTRA_RECORDING_URI) ?: return
                val uri = Uri.parse(uriString)

                // Dismiss notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(uri.hashCode())

                // Delete the file using DocumentFile
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val documentFile = DocumentFile.fromSingleUri(context, uri)
                        if (documentFile != null && documentFile.exists()) {
                            val success = documentFile.delete()
                            if (success) {
                                AppLogger.d(TAG, "Successfully deleted recording from notification: $uri")
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, R.string.recording_toast_deleted, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                AppLogger.e(TAG, "Failed to delete recording from notification: $uri")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error while deleting recording", e)
                    }
                }
            }
            ACTION_RESUME_AFTER_ERROR -> {
                AppLogger.d(TAG, "Resume action triggered from error notification")

                val metadata = IntentCompat.getParcelableExtra(intent, EnrichedCallData.EXTRA_METADATA, EnrichedCallData::class.java)
                    ?: PhoneStateTemporaryCache(context).restoreActiveSession()?.let { raw ->
                        EnrichedCallData(
                            normalisedPhoneNumber = raw.rawPhoneNumber,
                            formattedE164Number = raw.rawPhoneNumber,
                            direction = raw.direction,
                            isCrossCountry = false,
                            callerName = raw.osProvidedCallerName,
                            packageName = raw.packageName
                        )
                    }

                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (telephonyManager.callState == TelephonyManager.CALL_STATE_OFFHOOK && metadata != null) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(RecordingNotificationHelper.ERROR_NOTIFICATION_ID)

                    val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
                        action = RecordingForegroundService.ACTION_START_RECORDING
                        putExtra(EnrichedCallData.EXTRA_METADATA, metadata)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                    AppLogger.i(TAG, "Resuming call recording foreground service with metadata: ${metadata.getBestNumber()}")
                } else {
                    AppLogger.w(TAG, "Cannot resume call recording: phone call is no longer active or metadata is missing")
                }
            }
        }
    }
}
