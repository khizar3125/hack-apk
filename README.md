# 🫀 Camera Pulse

Fingertip se dil ki dhadkan naapne wala Android app — **bina kisi AI API, bina internet, 100% offline**.

## Kaam kaise karta hai?

Photoplethysmography (PPG) — pura classical DSP, koi AI nahi:

1. Back camera + flash ON — ungli se cover karo
2. Har heartbeat pe capillaries me blood ka pressure wave aata hai, jo red light ka intensity halka sa badal deta hai
3. App har frame se red-channel ka mean nikaalta hai → raw signal
4. Signal pe: detrend → moving-average smoothing → Hann window → **Discrete Fourier Transform** (0.75–3.3 Hz band)
5. Dominant frequency × 60 = **BPM** ✨

## Features

- Live waveform + live BPM (ring progress ke saath 30s measurement)
- Beating heart animation jo tumhari asli dhadkan se sync hoti hai
- Zone badge: Resting / Normal / Elevated / High
- Last 20 readings ka history (offline, SharedPreferences)
- Dark gradient UI, zero AI, zero network

## APK kaise banega?

GitHub Actions automatically build karta hai:

1. Repo me jao → **Actions** tab → **Build APK** workflow
2. Latest run kholo → **Artifacts** → `CameraPulse-debug-apk` download
3. Phone pe install karo (unknown sources allow karke)

Local build (Android Studio ya CLI):
```bash
./gradlew assembleDebug   # APK: app/build/outputs/apk/debug/
```

## Install requirements

- Android 8.0+ (API 26+)
- Back camera with flash

## Note

Ye fitness/wellness ke liye hai — medical device nahi. दिल se ❤️ banaya gaya.
