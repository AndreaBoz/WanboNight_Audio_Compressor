# Wanbo Night Audio Compressor

Wanbo Night is a personal Android TV project created to solve one specific problem: movie dialogue, music, and sound effects often have very different loudness levels, making it necessary to constantly adjust the volume during playback.

The app applies dynamic range compression to the decoded audio stream. Loud peaks are reduced while quieter passages remain easier to hear, helping keep the perceived volume more consistent during films and TV shows. It does not isolate dialogue or change individual frequency bands.

## Tested setup

The project was developed and tested with the following setup:

- Wanbo WPC024 / Cube 2 Pro projector
- Android module identified as SkyworthDigital/Banoika FHD Streaming Stick
- Android 14 (API 34)
- Prime Video playback
- JBL Xtreme speaker connected through the projector's 3.5 mm audio jack

The tested audio path was Prime Video → 48 kHz stereo PCM mixer → Android module output → JBL speaker. Compatibility with other devices, Android versions, apps, audio codecs, passthrough, offload, and direct playback modes has not been verified.

## How to use it

1. Install `apk/Wanbo-Night-0.2.apk` on the Android TV device.
2. Open **Wanbo Night** from the app list.
3. Start with these values:
   - Threshold: **−30 dB**
   - Ratio: **4:1**
   - Makeup gain: **0 dB**
4. Select **Apply and enable**.
5. Open Prime Video and resume the film.
6. If sound effects are still too loud, lower the threshold gradually, for example to **−35 dB**.
7. If the overall sound is balanced but too quiet, increase makeup gain gradually.
8. Use **Compare** to switch between processed and original audio, or **Disable** to release the effect completely.

Use the remote control to navigate: up/down moves focus, left/right adjusts sliders, and OK activates buttons. Slider changes take effect after selecting **Apply and enable**.

## Notes and limitations

- The app does not require root and does not modify firmware, system audio settings, partitions, or Dolby configuration.
- It works as a foreground service while enabled. It does not start automatically after a reboot.
- Compression changes the soundtrack's dynamics. Aggressive settings can make the sound less natural, soften transients, or cause audible volume pumping.
- The **Active** state confirms that the audio effect is enabled; it is not a meter of actual gain reduction.

## Building from source

Open `progetto-android` in Android Studio, then create a `local.properties` file with the path to your Android SDK or set `ANDROID_HOME`.

Requirements:

- JDK 17 or 21
- Android SDK 35 and Build Tools 35.0.0

Build from the `progetto-android` directory:

```sh
./gradlew :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.
