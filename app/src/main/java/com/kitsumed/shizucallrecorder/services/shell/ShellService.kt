/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.shell

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import com.kitsumed.shizucallrecorder.ILogCallback
import com.kitsumed.shizucallrecorder.IShellService
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioSource
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyConfig
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ServerExtractor
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * ShellService runs inside the privileged shell process (UID 2000 or 0) managed by Shizuku.
 *
 * By running under the app shell as ADB or root via Shizuku, we can
 * launch scrcpy-server with app_process and capture audio that a normal app cannot access.
 *
 * AI generated Overview:
 *
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  Shell Process (UID 2000 or 0)                              │
 *   │                                                             │
 *   │  ShellService (this class, AIDL stub)                       │
 *   │    │                                                        │
 *   │    ├── launches  scrcpy-server (app_process)                │
 *   │    │     └── connects to  LocalServerSocket                 │
 *   │    │                          │                             │
 *   │    ├── AudioRelayCoroutine ◄──┘  (socket → pipe)            │
 *   │    │         │                                              │
 *   │    │     Pipe[1] write-end (kept in Shell)                  │
 *   │    │     Pipe[0] read-end  ───────────────► App Process     │
 *   │    │                                          ScrcpyClient  │
 *   │    ├── LogConsumerCoroutine   (drain stdout)                │
 *   │    └── ProcessMonitorCoroutine (wait for exit)              │
 *   └─────────────────────────────────────────────────────────────┘
 *
 * Shizuku requirements:
 *  • Must have a no-arg constructor AND a single-Context constructor (Shizuku v13+).
 *  • Must be annotated with [@Keep] so ProGuard/R8 does not remove/rename the class.
 *  • [destroy] must call [kotlin.system.exitProcess] to terminate the shell process when Shizuku asks.
 */
@Keep
class ShellService : IShellService.Stub {
    private companion object {
        const val TAG = "SCR:ShellService"
    }

    private val audioPipeline by lazy { ShellAudioPipeline() }

    // ---- Shizuku-required constructors

    /**
     * No-arg constructor required by older versions of Shizuku.
     */
    @Keep
    constructor() : this(null)

    /**
     * Context constructor required by Shizuku v13+ for user-service instantiation.
     *
     * @param context The fake [android.content.Context] provided by Shizuku, or null on older versions.
     */
    @Keep
    constructor(context: Context?) {
        Log.i(TAG,"===============================\n" +
             "ShellService process started!\n" +
             "Running as UID=(${android.os.Process.myUid()})\n" +
             "===============================")
    }

    // -------- IShellService AIDL implementation

    override fun startRecording(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String,
        isDebuggingModeEnabled: Boolean,
        listener: ILogCallback
    ): ParcelFileDescriptor? {
        AppLogger.initAsRemote(listener, isDebuggingModeEnabled)
        return audioPipeline.startCapture(audioSource, audioCodec, audioBitRate, serverPath, isDebuggingModeEnabled)
    }

    override fun stopRecording() {
        audioPipeline.stopCapture()
    }

    override fun grantAppOps(packageName: String, opName: String, userProfileId: Int): Boolean {
        try {
            AppLogger.i(TAG, "Executing AppOps set --user $userProfileId $packageName $opName allow")
            val process = ProcessBuilder("appops", "set", "--user", userProfileId.toString(), packageName, opName, "allow").start()
            val errorOutput = process.errorStream.bufferedReader().readText().trim()
            val inputOutput = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            AppLogger.i(TAG, "grantAppOps completed with exit code $exitCode. Output: ${inputOutput.ifBlank { "Empty" }}, Error: ${errorOutput.ifBlank { "Empty" }}")
            // We return false if the exit code is non-zero or if there was any error output, indicating that the operation failed.
            return (exitCode == 0 && errorOutput.isBlank())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error granting AppOps $opName to $packageName: ${e.message}", e)
        }
        return false
    }

    /**
     * Called by Shizuku when it wants to shut down this user service.
     * @see IShellService.destroy
     */
    override fun destroy() {
        AppLogger.i(TAG,"ShellService.destroy() – terminating shell process")
        stopRecording()
        exitProcess(0)
    }
}