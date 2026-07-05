# Phase 1: VoIP Audio Source Auto-Switch

## Problem

ShizuCallRecorder currently uses a single audio source (configured in Settings) for all recordings. When the user has third-party call recording enabled (`isRecordThirdPartyCallsEnabled`), VoIP calls from apps like Teams, Google Meet, WhatsApp, etc. are detected by `InCallService` but produce **silent recordings** because the default carrier-oriented sources (`VOICE_CALL`, `VOICE_COMMUNICATION`) don't capture audio from VoIP call paths.

## Goal

Automatically switch to a VoIP-compatible audio source (`OUTPUT` or `PLAYBACK`) when the detected call is a VoIP/self-managed call, while keeping the existing carrier source for normal phone calls. The user can override the VoIP source in Settings.

## Scope

- **In scope**: Auto-switch audio source, new preference for VoIP source, propagating VoIP flag through the call data pipeline.
- **Out of scope**: Microphone mixing (capturing both sides for VoIP), dual-stream recording. These are deferred to a future Phase 2.

## Constraints

- VoIP detection is only available with the `InCallService` call detection mode (Android 12+). The `PhoneState` receiver only handles carrier telephony broadcasts and cannot detect VoIP calls.
- `PLAYBACK` (AudioPlaybackCapture API) respects app-level opt-out (`allowAudioPlaybackCapture=false`). Apps like Teams/Meet may opt out, making this source silent. However, since scrcpy-server runs as shell (UID 2000) with `CAPTURE_AUDIO_OUTPUT`, it **may** bypass app opt-out. This needs testing.
- `OUTPUT` (REMOTE_SUBMIX) captures all system audio output, including notifications and media — not just the VoIP call audio.
- Default VoIP source is **TBD** until real-device testing is done with both `OUTPUT` and `PLAYBACK`. The implementation will use `OUTPUT` as an interim default (most reliable).

---

## Proposed Changes

### 1. Data Model — Propagate VoIP flag

#### [MODIFY] [RawCallData.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/data/call/RawCallData.kt)

Add a new `isVoipCall` field:

```kotlin
@Parcelize
data class RawCallData(
    val rawPhoneNumber: String,
    val direction: CallDirection,
    val osProvidedCallerName: String? = null,
    val packageName: String? = null,
    val isVoipCall: Boolean = false  // NEW
) : Parcelable
```

- Default `false` ensures backward compatibility (PhoneState detector never sets it).

#### [MODIFY] [EnrichedCallData.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/data/call/EnrichedCallData.kt)

Add matching `isVoipCall` field, propagated from `RawCallData` during enrichment:

```kotlin
@Parcelize
data class EnrichedCallData(
    val normalisedPhoneNumber: String,
    val formattedE164Number: String? = null,
    val direction: CallDirection,
    val isCrossCountry: Boolean = false,
    val callerName: String? = null,
    val packageName: String? = null,
    val isVoipCall: Boolean = false  // NEW
) : Parcelable
```

In `enrichMetadata()`, propagate `base.isVoipCall` to all `EnrichedCallData` constructor calls.

---

### 2. Call Detection — Set VoIP flag

#### [MODIFY] [InCallService.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/services/callDetection/incall/InCallService.kt)

In `handleCallStateChanged()`, at lines 183-184, the flags `PROPERTY_SELF_MANAGED` and `PROPERTY_VOIP_AUDIO_MODE` are already read but only logged. Use them to set the VoIP flag in `RawCallData`:

```kotlin
val isSelfManaged = details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)
val isVoip = details.hasProperty(Call.Details.PROPERTY_VOIP_AUDIO_MODE)

val rawCallData = RawCallData(
    rawPhoneNumber = PhoneNumberManager.normalisePhoneNumber(rawNumber),
    direction = direction,
    osProvidedCallerName = oscallerName,
    packageName = packageName,
    isVoipCall = isSelfManaged || isVoip  // NEW: flag VoIP calls
)
```

**Why `isSelfManaged || isVoip`?** Self-managed calls are the Android framework's designation for VoIP/third-party calls that handle their own audio routing. `PROPERTY_VOIP_AUDIO_MODE` is a more explicit VoIP flag added later. Either flag indicates the call is not using the carrier audio path.

---

### 3. Recording Pipeline — Auto-switch audio source

#### [MODIFY] [AudioRecordingEngine.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/services/recording/AudioRecordingEngine.kt)

In `startPipeline()`, replace the current audio source resolution (line 132):

```kotlin
// BEFORE:
val audioSourceEnum = ScrcpyAudioSource.fromKey(preferences.getAudioSource())

// AFTER:
val audioSourceEnum = if (metadata.isVoipCall) {
    ScrcpyAudioSource.fromKey(preferences.getVoipAudioSource())
} else {
    ScrcpyAudioSource.fromKey(preferences.getAudioSource())
}
```

This is the only change needed in the recording pipeline — the rest of the scrcpy machinery (`ScrcpyConfig.buildServerArgs`, `ScrcpyClient`, `ScrcpyAudioMuxer`) already accepts any `ScrcpyAudioSource` value.

---

### 4. Preferences — New VoIP audio source setting

#### [MODIFY] [AppPreferences.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/data/AppPreferences.kt)

Add a new key and default:

```kotlin
// In Key enum:
VOIP_AUDIO_SOURCE("voip_audio_source"),

// In DefaultsValue:
val VOIP_AUDIO_SOURCE = ScrcpyAudioSource.OUTPUT.cliKey  // Interim default, pending testing

// Getter/setter:
fun getVoipAudioSource() = getString(Key.VOIP_AUDIO_SOURCE, DefaultsValue.VOIP_AUDIO_SOURCE)
    ?: DefaultsValue.VOIP_AUDIO_SOURCE
fun setVoipAudioSource(source: String) = setString(Key.VOIP_AUDIO_SOURCE, source)
```

---

### 5. Audio Sources — Mark VoIP-compatible sources

#### [MODIFY] [ScrcpyAudioSource.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/integrations/scrcpy/ScrcpyAudioSource.kt)

Add a `isVoipCompatible` property to the enum to distinguish which sources can capture VoIP audio. This is used by the Settings UI to filter the VoIP source dropdown:

```kotlin
enum class ScrcpyAudioSource(
    val cliKey: String,
    val titleResId: Int,
    val descriptionResId: Int,
    val minApi: Int,
    val maxApi: Int?,
    val isDebugOnly: Boolean,
    val isVoipCompatible: Boolean = false  // NEW
)
```

Set `isVoipCompatible = true` on:
- `OUTPUT` — captures all system audio output via REMOTE_SUBMIX
- `PLAYBACK` — captures app audio via AudioPlaybackCapture API
- `MIC` — captures microphone only (no remote audio, but useful for edge cases)
- `VOICE_COMMUNICATION` — mic tuned for VoIP (captures local mic, may capture remote on some devices)

Set `isVoipCompatible = false` on:
- `VOICE_CALL`, `VOICE_CALL_UPLINK`, `VOICE_CALL_DOWNLINK` — carrier-only sources

---

### 6. Settings UI — VoIP audio source dropdown

#### [MODIFY] [SettingsScreen.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/ui/screens/SettingsScreen.kt)

In the `AudioSection` composable, add a new `M3DropdownField` for the VoIP audio source. This dropdown:
- Is only visible when `isRecordThirdPartyCallsEnabled` is `true` **and** the detection mode is `InCallService`
- Shows only sources where `isVoipCompatible == true` (plus `isDebugOnly` filtering)
- Uses the same `OptionItem` pattern as the existing audio source dropdown

#### [MODIFY] [SettingsViewModel.kt](file:///c:/Users/danil/Documents/GitHub/Projects/ShizuCallRecorder/app/src/main/java/com/kitsumed/shizucallrecorder/ui/viewmodels/SettingsViewModel.kt)

Add `setVoipAudioSource` action forwarding to the ViewModel/actions interface.

---

### 7. String Resources

#### [MODIFY] strings.xml

Add new string resources for the VoIP audio source UI:
- `settings_voip_audio_source` — label for the dropdown (e.g., "VoIP Audio Source" / "Sorgente audio VoIP")
- `settings_voip_audio_source_description` — brief explanation shown below the dropdown

---

## Files Changed Summary

| File | Change |
|---|---|
| `RawCallData.kt` | Add `isVoipCall: Boolean = false` field |
| `EnrichedCallData.kt` | Add `isVoipCall: Boolean = false` field, propagate in `enrichMetadata()` |
| `InCallService.kt` | Set `isVoipCall` from `PROPERTY_SELF_MANAGED \|\| PROPERTY_VOIP_AUDIO_MODE` |
| `AudioRecordingEngine.kt` | Auto-switch source based on `metadata.isVoipCall` |
| `AppPreferences.kt` | Add `VOIP_AUDIO_SOURCE` key, default, getter/setter |
| `ScrcpyAudioSource.kt` | Add `isVoipCompatible` property to enum entries |
| `SettingsScreen.kt` | Add conditional VoIP audio source dropdown |
| `SettingsViewModel.kt` | Add `setVoipAudioSource` action |
| `strings.xml` | Add new UI string resources |

## Verification Plan

### Manual Testing (required)

1. **Carrier call**: Verify that normal phone calls still use the regular audio source and record correctly.
2. **VoIP call with OUTPUT**: Enable third-party recording, set VoIP source to `OUTPUT`, make a Teams/Meet/WhatsApp call → verify the recording contains audible audio from the remote party.
3. **VoIP call with PLAYBACK**: Same test with `PLAYBACK` source → verify if remote audio is captured or silenced by app opt-out.
4. **Auto-switch**: Make a carrier call followed by a VoIP call → verify logs show the correct source was selected for each.
5. **Settings visibility**: Verify VoIP dropdown only appears when third-party recording is enabled AND detection mode is InCallService.

### Automated Checks

- Build the project (`./gradlew assembleDebug`) to verify no compilation errors from the new fields and properties.
