package com.kitsumed.shizucallrecorder;

import android.os.ParcelFileDescriptor;
import com.kitsumed.shizucallrecorder.ILogCallback;

interface IShellService {
    /**
     * Starts the audio-capture pipeline.
     *
     * @param audioSource        scrcpy audio_source parameter (e.g. "mic-voice-communication").
     * @param audioCodec         scrcpy audio_codec parameter (e.g. "opus", "aac").
     * @param audioBitRate       scrcpy audio_bit_rate in bps (e.g. 16000 for 16 kbps Opus).
     * @param serverPath      Absolute path to scrcpy-server.jar in shared storage.
     * @param isDebuggingModeEnabled  When true, logs relay throughput every second.
     * @return The read-end [ParcelFileDescriptor] of the audio pipe, or null on failure.
     */
    ParcelFileDescriptor startRecording(
        String audioSource,
        String audioCodec,
        int audioBitRate,
        String serverPath,
        boolean isDebuggingModeEnabled, // For debugging purposes, if true, the service will log additional information and change some logging behavior.
        ILogCallback appLoggerCallback
    ) = 1;

    /**
     * Stops the audio capture pipeline and releases all resources.
     */
    void stopRecording() = 2;

    /**
     * Executes `appops set` command to grant an appop permission to a package.
     * @param packageName The target package to grant the appop to (e.g. "com.kitsumed.shizucallrecorder").
     * @param opName The appop name to grant (e.g. "MANAGE_ONGOING_CALLS").
     * @param userProfileId The user profile ID to grant the appop for (e.g. 0 for the primary user).
     * @return true if the command executed successfully (exit code 0), false otherwise.
     */
    boolean grantAppOps(String packageName, String opName, int userProfileId) = 3;

    /**
     * Called by Shizuku when it wants to shut down this user service.
     * MUST call [kotlin.system.exitProcess] so the entire shell process is terminated.
     * This is the special transaction code used by Shizuku to "destroy" the process.
     */
    void destroy() = 16777114;
}
