# Siriri Nepali Keyboard

Rust core + thin Kotlin shell. English · Traditional Nepali (क) · Nepali Roman (ने) · Hindi Roman (हि).
No internet, no tracking, no bundled dictionary — suggestions come only from words you type, stored on-device.

```
rust-core/   Rust: layouts, Roman→Devanagari transliteration, learned-word store, JNI   (cargo test → 22 tests)
app/         Kotlin: InputMethodService, canvas keyboard view, settings screen            (no 3rd-party deps)
```

## Build

**Easiest — GitHub Actions:** push this repo to GitHub; the `Build APK` workflow runs the Rust tests and uploads `siriri-keyboard-apk`.

**Local:** Android Studio (or SDK + NDK), JDK 17, then
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
export ANDROID_NDK_HOME=/path/to/ndk
./gradlew :app:assembleRelease        # builds the Rust core automatically, APK in app/build/outputs/apk/release/
```
Install the APK → open **Siriri Keyboard** → *1. Enable Siriri Keyboard* → *2. Switch to Siriri Keyboard*.

## Privacy design
* Manifest declares **zero permissions** (no `INTERNET`) — Android blocks all sockets. The settings screen verifies this at runtime.
* `allowBackup=false` + data-extraction rules exclude everything from cloud/device transfer.
* Learned words: one file in the app's private storage (`words.bin`), unreadable by other apps. **Settings → Clear all saved words** deletes it; long-press a suggestion to forget one word.
* Never learns in password / e-mail / URL / number fields, nor when an app requests incognito (`IME_FLAG_NO_PERSONALIZED_LEARNING`). Those fields force the Latin layout.
* The Rust crate has no network, logging or telemetry code; its only dependency is the `jni` glue crate.

## Using it
* **Globe**: tap = next enabled mode (A → क → ने → हि, configurable in Settings), long-press = system keyboard picker.
* **Top-row long-press**: hold any of the first 10 top-row letter keys to type `1–0` (or `१–०` in Traditional Nepali).
* **Spacebar slide**: drag horizontally across the spacebar to move the cursor left/right.
* **Shift**: tap once = next key; double-tap = caps lock. Auto-capitalises in English only.
* **Double-space** → `. ` (English) or `। ` (Devanagari modes).
* **Suggestion strip**: shown in all 4 modes (including phonetic variants in Roman modes); hidden in URL, password and search fields.
* **Roman typing guide**: `aa ii/ee uu/oo ai au Ri` vowels · `kh gh ch chh jh th dh ph bh sh ksh gy` digraphs · capitals `T Th D Dh N Sh` = retroflex · `M` ं · `MM` ँ · `H` ः · `q` explicit halant. Examples: `namaste→नमस्ते`, `prem→प्रेम`, `gyaan→ज्ञान`, `hunchha→हुन्छ`. A consonant followed by `a` takes no matra (`ram→रम`, `raam→राम`); consonant clusters join automatically.

## Verification status
| Part | Status |
|---|---|
| Rust core | 22 unit tests pass (layouts, transliteration + phonetic variants, store, persistence, clear) |
| JNI bridge | `Native.kt` ↔ `jni_api.rs` exports compiled into `libsiriri_core.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`) |
| Kotlin | Compiles cleanly against Android API 36 (`targetSdk = 36`) |
| Release APK & AAB | Built, R8-minified, and signed with the release keystore |
