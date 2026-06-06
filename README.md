# Audio Level Analyzer

Focused Android audio level analyzer plus a small reference app.

The library is data-only: callers push normalized mono PCM into
`AudioLevelAnalyzer`, and receive raw bar levels through
`AudioLevelAnalyzer.FrameListener`.
Playback, audio capture, animation, and drawing stay outside the library.

## Modules

- `:audiolevelanalyzer` - the reusable core library.
- `:example` - a bundled-file playback demo that decodes PCM, feeds the core,
  and draws animated bars.

## Compatibility

The published library module is configured for broad Android project
compatibility:

- `minSdk 21`
- release AAR `minCompileSdk 35`
- Java bytecode target `1.8`
- no AndroidX runtime dependency
- no Kotlin stdlib runtime dependency
- no core library desugaring requirement

The `:example` app has its own higher requirements because it decodes and plays
a bundled audio file with Android media APIs. It is not published and does not
affect apps that depend on `:audiolevelanalyzer`.

## Core API

```java
AudioLevelAnalyzerConfig config = AudioLevelAnalyzerConfig.defaults();
AudioLevelAnalyzer engine = new AudioLevelAnalyzer(config);

engine.setFrameListener(new AudioLevelAnalyzer.FrameListener() {
    @Override
    public void onFrame(float[] levels, long timestampNanos, boolean active) {
        // levels are raw amplitudes in [0, 1].
        // Draw or animate them in your own UI layer.
    }
});

engine.start();
engine.pushPcm(samples, samples.length, 48_000);
```

`pushPcm` expects normalized mono samples. `frames` must be between `0` and
`mono.length`, and `sampleRate` must be positive. `inputGain` and configuration
values are validated at the API boundary.

`analysisRateHz` is a best-effort target for the Android `HandlerThread`
analysis loop. The engine schedules against absolute target timestamps to avoid
long-term drift, while the platform still determines the exact wake-up time.
The supported range is `1.0..120.0` Hz.

Use `AudioLevelAnalyzerConfig` for initial setup and read-only settings snapshots.
At runtime, `AudioLevelAnalyzer` only exposes the controls that are commonly
adjusted while audio is playing:

```java
engine.setInputGain(2.0f);
engine.setAnalysisRateHz(20.0);

AudioLevelAnalyzerConfig current = engine.getConfig();
int bars = engine.getBarCount();
float[] stops = engine.getBandStops();
```

`getConfig()` returns a snapshot: structural and level-shaping settings come
from the original `AudioLevelAnalyzerConfig`, while `inputGain` and
`analysisRateHz` reflect the latest runtime values.

Parameter groups:

- `amplitudeGain`, `exponentialGain`, `frequencyTiltEnd`, and
  `adjustmentCoefficients` shape the level response. Configure them up front
  with `AudioLevelAnalyzerConfig`.
- `fftSize`, `defaultSampleRate`, and `bandStops` are structural DSP settings.
  Configure them up front with `AudioLevelAnalyzerConfig`.
- `inputGain` and `analysisRateHz` are the normal runtime controls.

`bandStops` defines the frequency boundaries; `barCount = bandStops.length - 1`.
`adjustmentCoefficients` is a global polynomial curve shared by every bar, not a
per-band array. `frequencyTiltEnd` is the high-frequency multiplier endpoint:
the first band uses `1.0`, the last band uses `frequencyTiltEnd`, and
intermediate bands are linearly interpolated.

`pushPcm` may be called from an audio/source worker thread. `FrameListener`
callbacks are invoked on the main thread. Lifecycle calls are synchronized
internally, but apps should still call them from one lifecycle owner for
predictable ordering.

## Design Boundary

The core owns:

- PCM windowing
- Hann windowing
- radix-2 FFT
- frequency-band mapping
- raw level frame delivery

The caller owns:

- media playback or capture
- lifecycle decisions
- smoothing and animation
- mapping levels into UI

This keeps the reusable library small and lets apps choose their own UI.

## Example App

The `:example` app is intentionally narrow. It decodes a bundled audio file,
pushes mono PCM into `AudioLevelAnalyzer`, and draws the returned levels as
animated bars, defaulting to six.

Runtime controls shown in the demo:

- tap the bars to play or pause
- bar count presets: 3 / 6 / 24
- input gain
- analysis rate
- animation response
- UI damping ratio

The compact info rows show playback state, configured band stops, and the latest
raw levels. Playback progress, duration, cover art, color customization, and
other media UI concerns are intentionally outside the demo.

## Performance Notes

The hot path is kept allocation-light:

- `SampleRing` stores incoming PCM in a circular buffer, so small audio batches
  do not shift the whole FFT window on every push.
- `BandMapper` caches the FFT bin ranges for the current sample rate and
  magnitude count, so steady-state analysis does not repeatedly search band
  boundaries.
- `BandLevelAnalyzer` preallocates the window, FFT, and magnitude buffers.

## License

This repository uses a private proprietary license. See `LICENSE`.

## Third Party Notice

The bundled `:example` audio file is a short processed excerpt from LibriVox /
Internet Archive material:

- Source: "The Art of Public Speaking", Chapter 02, "The Sin of Monotony"
- Authors: Dale Carnagey (Dale Carnegie) and Joseph B. Esenwein
- Reader: Joe Mabry
- Source page:
  `https://librivox.org/the-art-of-public-speaking-by-dale-carnegie-and-joseph-b-esenwein/`
- Original file:
  `https://www.archive.org/download/art_public_speaking_1101_librivox/artpublicspeaking_02_carnagey_esenwein.mp3`
- Rights: LibriVox states that its recordings are Public Domain in the USA.
  Verify copyright status in other jurisdictions before redistribution.
- Processing: 90-second excerpt starting at 00:00:18, converted to AAC-LC M4A,
  mono, 48 kHz, with loudness normalization for the Android example.

## Verification

Run the focused core checks:

```bash
./gradlew :audiolevelanalyzer:testDebugUnitTest
./gradlew :audiolevelanalyzer:lintDebug
./gradlew :audiolevelanalyzer:assembleDebug
```

Run the demo build:

```bash
./gradlew :example:assembleDebug
```

The unit tests include DSP primitive checks, circular-buffer ordering checks,
band-range cache checks, a golden 440 Hz analyzer frame for the default six-band
configuration, public API checks, and scheduler regression tests.

## Installation

This library is distributed through JitPack. Add JitPack to the consuming
project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.lixiaolin94")
            }
        }
    }
}
```

Then depend on the library module:

```kotlin
implementation("com.github.lixiaolin94:AudioLevelAnalyzer:v0.1.0")
```

No token is needed when this repository is public.

## Release

Before creating a release, make sure the repository is public and the local
checks pass:

```bash
./gradlew :audiolevelanalyzer:testDebugUnitTest
./gradlew :audiolevelanalyzer:lintDebug
./gradlew :audiolevelanalyzer:publishToMavenLocal
```

Create and push a version tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

JitPack builds the tag on first use. You can also open this URL to trigger or
inspect the build:

```text
https://jitpack.io/#lixiaolin94/AudioLevelAnalyzer/v0.1.0
```

For future releases, bump the version in `audiolevelanalyzer/build.gradle.kts`,
create a matching tag such as `v0.1.1`, and update consuming apps.
