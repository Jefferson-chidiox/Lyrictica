# Implementation Plan

## Goal
Deliver a Kotlin Android MVP that plays a local audio file and renders fixed, band-isolated blue wave visualizations at the bottom of a dark screen, with each wave reacting only to its own frequency range.

## Tasks
1. **Task 1: Bootstrap Android project and baseline app shell**
   - File: `settings.gradle.kts`
   - Changes: Define project name and include `:app` module.
   - File: `build.gradle.kts`
   - Changes: Add Android/Kotlin plugin versions and shared repositories.
   - File: `gradle.properties`
   - Changes: Add AndroidX/Kotlin Gradle flags for stable build defaults.
   - File: `app/build.gradle.kts`
   - Changes: Configure Android app module (min/target SDK), enable Compose, add dependencies for Compose UI, Media3 ExoPlayer, and lifecycle/runtime tooling.
   - File: `app/src/main/AndroidManifest.xml`
   - Changes: Declare app entry activity. Use SAF for file import so no broad storage/microphone permissions are required.
   - Acceptance: Project sync/build works and launches a blank Compose screen on emulator/device.

2. **Task 2: Implement audio selection and playback pipeline**
   - File: `app/src/main/java/com/lyrictica/MainActivity.kt`
   - Changes: Create Compose host, register file picker (`ActivityResultContracts.OpenDocument`), and wire UI actions to playback.
   - File: `app/src/main/java/com/lyrictica/audio/PlayerController.kt`
   - Changes: Wrap Media3 ExoPlayer lifecycle (prepare/play/pause/release), expose playback state, and current media URI handling.
   - File: `app/src/main/java/com/lyrictica/audio/PlaybackUiState.kt`
   - Changes: Add state model for selected track/play status/error/loading.
   - Acceptance: User can select a local audio file and reliably play/pause it.

3. **Task 3: Add real-time band analysis (four fixed lanes)**
   - File: `app/src/main/java/com/lyrictica/audio/AudioAnalyzer.kt`
   - Changes: Precompute and sample four frequency bands (bass/mid/presence/treble) from the selected file during playback.
   - File: `app/src/main/java/com/lyrictica/audio/AudioFeatures.kt`
   - Changes: Define an immutable data model for normalized band energies only.
   - File: `app/src/main/java/com/lyrictica/audio/SpectrumBandsPrecomputer.kt`
   - Changes: Decode PCM and cache fixed band envelopes for reuse.
   - Acceptance: While audio plays, analyzer emits stable per-band values that stay at zero until their band becomes active.

4. **Task 4: Build fixed band-isolated wave renderer**
   - File: `app/src/main/java/com/lyrictica/visualizer/WaveLayer.kt`
   - Changes: Define per-band config (band id, base height, amplitude, threshold, alpha, colors).
   - File: `app/src/main/java/com/lyrictica/visualizer/WaveVisualizer.kt`
   - Changes: Create Compose `Canvas` visualizer with dark backdrop and four fixed wave layers that only wake when their band is active.
   - Acceptance: Visual output shows a clear hierarchy: louder bands are more visible, subtler bands remain faint or flat until activated.

5. **Task 5: Connect analyzer output to visualizer behavior**
   - File: `app/src/main/java/com/lyrictica/visualizer/VisualizerViewModel.kt`
   - Changes: Smooth the four band values with attack/release filters and expose stable render state flow.
   - File: `app/src/main/java/com/lyrictica/ui/VisualizerScreen.kt`
   - Changes: Bind player controls + visualizer; collect state with lifecycle awareness.
   - File: `app/src/main/java/com/lyrictica/MainActivity.kt`
   - Changes: Hook screen, controller, and viewmodel lifecycle.
   - Acceptance: Each wave remains gated to its own band and becomes more visible only when that band is loud enough.

6. **Task 6: MVP polish, lifecycle hardening, and verification**
   - File: `app/src/main/java/com/lyrictica/audio/PlayerController.kt`
   - Changes: Ensure proper release on stop/destroy and recover from invalid URI/errors.
   - File: `app/src/main/java/com/lyrictica/visualizer/WaveVisualizer.kt`
   - Changes: Add frame throttling and low-overdraw defaults to protect FPS on mid-range devices.
   - File: `app/src/androidTest/java/com/lyrictica/SmokeLaunchTest.kt`
   - Changes: Add basic launch test and playback-control visibility assertion.
   - File: `README.md`
   - Changes: Document setup, supported Android versions, known limitations, and next MVP extension points.
   - Acceptance: App runs without crashes through select→play→pause→resume→stop, maintains smooth animation, and passes smoke tests.

## Files to Modify
- `.gitignore` - extend for Android/Gradle/IDE artifacts.

## New Files
- `settings.gradle.kts` - root Gradle project/module wiring.
- `build.gradle.kts` - root build plugins/repositories.
- `gradle.properties` - project-wide Gradle/Android flags.
- `app/build.gradle.kts` - app module config + dependencies.
- `app/src/main/AndroidManifest.xml` - app declaration and permissions.
- `app/src/main/java/com/lyrictica/MainActivity.kt` - app entry and picker wiring.
- `app/src/main/java/com/lyrictica/ui/VisualizerScreen.kt` - main screen layout.
- `app/src/main/java/com/lyrictica/audio/PlaybackUiState.kt` - playback UI state model.
- `app/src/main/java/com/lyrictica/audio/PlayerController.kt` - ExoPlayer wrapper/lifecycle.
- `app/src/main/java/com/lyrictica/audio/AudioAnalyzer.kt` - Stages audio, caches band envelopes, and samples fixed-band energy during playback.
- `app/src/main/java/com/lyrictica/audio/AudioFeatures.kt` - analyzer output model.
- `app/src/main/java/com/lyrictica/visualizer/WaveLayer.kt` - wave layer configuration model.
- `app/src/main/java/com/lyrictica/visualizer/WaveVisualizer.kt` - Compose canvas renderer.
- `app/src/main/java/com/lyrictica/visualizer/VisualizerViewModel.kt` - state mapping/smoothing.
- `app/src/main/res/values/colors.xml` - dark/blue palette.
- `app/src/main/res/values/themes.xml` - base app theme.
- `app/src/androidTest/java/com/lyrictica/SmokeLaunchTest.kt` - smoke test.
- `README.md` - run/use notes and MVP scope.

## Dependencies
- Task 1 is required before all other tasks.
- Task 2 depends on Task 1.
- Task 3 depends on Task 2 (needs the selected audio source).
- Task 4 can start after Task 1, but final tuning depends on Task 3 outputs.
- Task 5 depends on Tasks 2, 3, and 4.
- Task 6 depends on Task 5 completion.

## Risks
- **PCM decode / cache edge cases:** Some codecs/files may fail to decode or cache cleanly; mitigation: guard exceptions, keep visuals in idle mode, and invalidate stale band caches when the format changes.
- **Permission/storage variance:** Audio picking and URI persistence differ by API level; mitigation: use SAF (`OpenDocument`) and persist URI read permission.
- **Performance/jank risk:** Layered gradients and overdraw can drop FPS; mitigation: keep the layer count fixed, precompute path points, and throttle update rate.
- **Band threshold tuning:** Fixed frequency cutoffs and thresholds may need iteration across genres; mitigation: keep cutoffs centralized and easy to adjust.
- **Underspecified product details:** No explicit min SDK or exact band boundaries were provided; mitigation: default to practical MVP values and document assumptions in README for quick confirmation.
