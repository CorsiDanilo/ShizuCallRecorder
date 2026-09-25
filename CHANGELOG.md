# Changelog

All notable changes to this project will be documented in this file.

## [1.4.2] - 2026-09-25
### Changed
- **Upstream Sync**: Synchronized upstream commits and features from `kitsumed/ShizuCallRecorder:main` (release 1.3.3).
- **Translations**: Updated localization strings from Hosted Weblate for Arabic, Bulgarian, Czech, Spanish, French, Slovak, and Simplified Chinese.
- **Dependencies**: Bumped `libphonenumber` to version `9.0.36`.
- **CI / Workflows**: Updated CodeQL security scanning workflows and added `--no-daemon` option to build CI.

## [1.4.1] - 2026-08-10
### Added
- **Automatic Shizuku Recovery**: Recording now waits for Shizuku to reconnect and automatically starts a new recording segment after a temporary binder disconnection.
- **Recovery Status Notification**: The foreground notification now clearly indicates when the service is waiting to reconnect to Shizuku.

### Changed
- **Smoother Interruption Handling**: Recoverable Shizuku disconnections no longer stop the recording service or require a manual "Resume" action.
- **Notification Management**: Transient recovery errors no longer create an additional error notification while the service is retrying.
- **Recording Lifecycle State**: Added an explicit recovery state to keep call metadata and service visibility consistent during reconnection.

### Fixed
- **Screen Lock/Unlock Interruptions**: Recording can resume automatically when Shizuku becomes available again after the device screen is locked or unlocked.
- **Partial Session Cleanup**: Interrupted audio sessions are finalized before recovery so the resumed recording starts cleanly in a separate segment.

## [1.4.0] - 2026-07-21
### Added
- **Interactive Resume Action**: Added a "Resume" button to error notifications when Shizuku disconnects mid-call, saving Part 1 and resuming in a separate file (Part 2).
- **Keep Screen On Option**: Added setting to keep display awake during active call recording sessions (`keep_screen_on_during_calls`).
- **Pull-To-Refresh**: Added pull-to-refresh swipe gesture support on the Recordings Screen.
- **Localization**: Full internationalization with English base strings and Italian translations (`values-it`).

## [1.3.0] - 2026-07-15
### Added
- **Recordings Management Screen**: A new dedicated screen to browse, play, rename, share, and delete audio recordings directly from the app.
- **Swipe Gestures**: Swipe left to delete, swipe right to rename recordings.
- **Multi-Selection Mode**: Long-press a recording to enter bulk selection for deleting or sharing multiple files at once.
- **In-App Media Player**: Minimalist audio player sliding up from the bottom to preview your recordings.
- **Search**: Real-time filtering of recordings by file name.

## [1.2.0] - 2026-07-05
### Added
- **Multi-language support**: Expanded localizations for strings across Arabic, German, Spanish, French, Hungarian, Italian, Japanese, Polish, Portuguese, Russian, Turkish, Vietnamese, and Chinese.
- **Background Recording Services & Workers**: Added robust background recording handling via foreground service and `RecordingActionReceiver` to manage active sessions.
- **Enhanced Settings Screen**: Redesigned layout with updated controls, sliders, and options.
- **Debug Build Customizations**: Distinct application name "ShizuCallRecorder Debug" to avoid conflicts with production releases.
- **Gradle and Dependency Upgrades**: Updated AGP and Kotlin compiler configurations, and added `androidx.work.runtime.ktx`.
