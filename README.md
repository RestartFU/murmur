# Murmur

Voice-to-text for any Android app. When a keyboard opens, a small mic button docks just above it. Tap it, speak, and tap again. Murmur transcribes on your phone and types the text at the cursor.

- **On-device.** Speech never leaves the phone. After the one-time model download it works offline.
- **25 languages, detected automatically.** It uses [Parakeet Ultra](https://huggingface.co/moondream/parakeet-ultra), Moondream's post-trained version of NVIDIA's [Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3). It covers Bulgarian, Croatian, Czech, Danish, Dutch, English, Estonian, Finnish, French, German, Greek, Hungarian, Italian, Latvian, Lithuanian, Maltese, Polish, Portuguese, Romanian, Russian, Slovak, Slovenian, Spanish, Swedish and Ukrainian.
- **Works with any keyboard.** Murmur doesn't replace your keyboard. It sits on top of the one you already use.

## How it works

| Piece | What it does |
| --- | --- |
| `MurmurAccessibilityService` | Watches for an input-method window, keeps the button docked above it, records on tap, and inserts the result. |
| `OverlayController` / `MicButtonView` | An accessibility overlay window with the mic button. Drag it sideways to move it; the position is remembered. |
| `Recorder` | Captures 16 kHz mono float PCM, capped at 2 minutes per take. |
| `Transcriber` | Runs [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) with the int8 Parakeet transducer. It loads when a keyboard appears and unloads after 5 idle minutes. |
| `TextInserter` | Splices the text in at the cursor with `ACTION_SET_TEXT`, adding spaces where needed. If a field refuses, it falls back to paste and then to the clipboard. |
| `ModelManager` | Downloads the 487 MB model archive with `DownloadManager`, then verifies its SHA-256 and unpacks it in one pass. |

The accessibility service is what makes this possible. It is the only way for an app to know when another app's keyboard is open, and to type into that app's text field. Android binds accessibility services with `BIND_INCLUDE_CAPABILITIES`, so the service can use the microphone while the screen is on without a foreground service.

## Building

You need JDK 17+ and the Android SDK (compile SDK 37).

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The build downloads the sherpa-onnx AAR (v2.0.1) from its GitHub release on first run and checks it against a pinned SHA-256. See `app/build.gradle.kts`. Release APKs ship `arm64-v8a` native libraries only; debug APKs add `x86_64` for the emulator.

## Setup on the phone

1. Open Murmur and allow the microphone.
2. Download the speech model.
3. Turn on Murmur in **Settings → Accessibility**. On Android 13+ a sideloaded app's switch may be greyed out. If so, open **App info → ⋮ → Allow restricted settings** first.
4. Open any text field and tap the mic.

## Credits

- Speech model: [moondream/parakeet-ultra](https://huggingface.co/moondream/parakeet-ultra), based on [nvidia/parakeet-tdt-0.6b-v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3), both CC BY 4.0. Murmur uses the sherpa-onnx int8 ONNX export.
- Inference: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), Apache 2.0.
