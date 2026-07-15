# Changelog

All notable changes to this project will be documented in this file.

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
