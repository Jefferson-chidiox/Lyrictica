
# Lyrictica Wave Visualizer MVP

An elegant, high-fidelity Android Audio Wave Visualizer MVP built with **Kotlin**, **Jetpack Compose**, and **Media3 ExoPlayer**. 

This app renders fixed, glowing wave lanes that stay isolated per frequency band and shift between blue, green, purple, and red screen moods as the music evolves.

---

## 🎨 Visual Design & Mechanics

The visual interface is designed to deliver a gorgeous neon liquid aesthetic, mirroring the dark glowing gradient look of premium modern music applications:

1. **Dark Cinematic Gradient**: A rich deep dark background (`#04070D` to `#091220`) that makes the glowing waves visually pop.
2. **Bottom-Weighted Gradient**: A soft dark gradient adds depth without driving the wave motion.
3. **Band-Isolated Wave Hierarchy**:
   - **Layer 1 (Bass)**: Low-frequency lane with the strongest weight.
   - **Layer 2 (Mid)**: Body/low-mid lane for general instrument energy.
   - **Layer 3 (Presence)**: Upper-mid lane that helps separate voices and leads from the rest.
   - **Layer 4 (Treble)**: Bright detail lane for crisp highs.
4. **Hard Gating**: Each wave stays flat until its own band clears a threshold.
5. **Cinematic Smoothing**: Per-band attack/release smoothing keeps motion stable without beat-driven global pulses.
6. **Adaptive Mood Themes**: The full screen crossfades between green (calm), blue (groove), purple (build), and red (peak) palettes based on repeating musical patterns and energy changes.
7. **Root Backdrop Control**: The mood gradient and glow are drawn at the screen level so the whole visualizer changes, not just the waves.

---

## 🚀 How to Import and Test

Because this is an MVP of a larger music player and gaming app, users can easily select any local audio file and play it to see the visualizer in full action:

1. **Launch the App**: When the app opens, you will see the dark screen and the resting wave lanes at the bottom.
2. **Import Audio**: Tap the **Import** button in the floating player card.
3. **Select File**: Use Android's native file picker to choose any audio file (e.g. `.mp3`, `.wav`, `.m4a`, `.flac`) from your device or downloads.
4. **Play & Control**: Press the large glowing **Play** button! Use the progress slider to scrub through the track, or use the play/pause button. Observe each wave wake up only when its frequency band is present.

---

## 🛠️ Architecture & Package Map

The project is structured following clean development patterns:

```
app/src/main/java/com/lyrictica/
│
├── audio/
│   ├── PlaybackUiState.kt  - Represents player details, duration, seek positions and error logs.
│   ├── PlayerController.kt - Manages Media3 ExoPlayer instantiation, play/pause, SAF import, and releases.
│   ├── AudioFeatures.kt    - Holds normalized band energy for bass/mid/presence/treble.
│   ├── AudioAnalyzer.kt    - Orchestrates staging + offline PCM band analysis and samples the cached envelopes.
│   ├── PcmDecoder.kt       - MediaExtractor/MediaCodec offline decode to mono float PCM.
│   ├── BiquadFilter.kt     - RBJ biquad filters used to split frequency bands.
│   └── SpectrumBandsPrecomputer.kt - Precomputes/caches per-frame four-band envelope arrays (30 fps).
│
├── visualizer/
│   ├── VisualizerPalette.kt - Screen mood presets and palette interpolation helpers.
│   ├── VisualizerMoodEngine.kt - Resolves repeating audio patterns into adaptive theme transitions.
│   ├── WaveLayer.kt        - Configuration parameters for band, thresholds, heights, colors, and alpha.
│   ├── WaveVisualizer.kt   - Renders the custom Compose Canvas with fixed band-isolated waves.
│   └── VisualizerViewModel.kt- Coordinates player/analyzer lifecycles and exposes smoothed features plus theme state.
│
└── ui/
    └── VisualizerScreen.kt - Defines the layouts, responsive grids, and the glassmorphic player card overlay.
```

---

## 🏗️ Getting Started (Developer Setup)

To build, run, or extend the app:

1. **Open in Android Studio**:
   - Open Android Studio.
   - Select **Open** and point it to this project folder `Lyrictica`.
   - Android Studio will automatically synchronize the Gradle configuration, download the necessary SDK platforms, and generate the local Gradle wrappers.
2. **Build and Run**:
   - Connect your Android physical device via USB (with Developer Options & USB Debugging enabled) or start an Android Emulator (API level 26 or higher).
   - Click the green **Run** button at the top of Android Studio, or execute in a terminal:
     ```bash
     ./gradlew installDebug
     ```
3. **Run Composable UI Smoke Tests**:
   - Execute instrumental tests to verify initial view hierarchies:
     ```bash
     ./gradlew connectedAndroidTest
     ```

---

## 🔒 Permissions & Safety

**Lyrictica MVP** avoids microphone permissions entirely:
- Band envelopes are precomputed offline by decoding PCM via Android codecs and splitting it into fixed frequency bands.
- The band cache is stored in app-private files, so each song only needs to be analyzed once unless the cache is cleared.
- Files are imported through Android's standard **Storage Access Framework (SAF)**, so no broad storage permissions are needed.
