package com.kitsumed.shizucallrecorder.services.recording

import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class RecordingServiceStateTest {

    @Test
    fun recoveringStateKeepsCallMetadataWithoutBeingActive() {
        val metadata = EnrichedCallData(
            normalisedPhoneNumber = "+393331234567",
            direction = CallDirection.INCOMING
        )

        val state = RecordingServiceState.Recovering(metadata)

        assertSame(metadata, state.metadata)
        assertFalse(state.isRecordingActive)
        assertFalse(state.isStarting)
        assert(state.isRecovering)
    }
}
