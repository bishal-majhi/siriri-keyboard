# Privacy Policy — Siriri Keyboard

**Effective Date:** November 13, 2026  
**App Name:** Siriri Keyboard  
**Package ID:** `np.com.siriri.keyboard`  
**Source Code:** https://github.com/bishal-majhi/siriri-keyboard

## Overview

Siriri Nepali Keyboard ("Siriri Keyboard") is an open-source, offline keyboard application designed with privacy by construction. **We do not collect, transmit, store, sell, or share any personal data or typing data.**

## 1. Zero Network Access

* The app's [AndroidManifest.xml](app/src/main/AndroidManifest.xml) declares **zero permissions** — including no `INTERNET` or `ACCESS_NETWORK_STATE` permission.
* Android's operating system blocks the app from opening any network sockets.
* The app contains no analytics SDKs, crash reporters, advertising libraries, or telemetry code.
* You can verify this in the app itself on the Settings screen (`✓ Verified: this app requests 0 permissions (no internet).`).

## 2. On-Device Data Only

* **Preferences:** Your selected keyboard mode, haptic feedback, key click sound, word learning toggle, and enabled languages are stored locally in Android private `SharedPreferences` (`siriri_keyboard`).
* **Learned Words (Optional, On-Device):** When word learning is enabled, words you type are stored solely in a single private file (`words.bin`) inside the app's sandboxed private storage on your device. No other app can access this file.
* **No Cloud Backup:** `android:allowBackup="false"`, `android:fullBackupContent="false"`, and strict [data extraction rules](app/src/main/res/xml/data_extraction_rules.xml) prevent your learned words and settings from being copied to cloud backups or transferred to other devices.

## 3. Sensitive Fields & Incognito Mode

Siriri Keyboard **never learns or saves words** when typing in:
* Password fields
* E-mail address fields
* URL / URI fields
* Numeric, phone, or date/time fields
* Any text field where the host app requests no personalized learning (`IME_FLAG_NO_PERSONALIZED_LEARNING` / incognito mode)

## 4. User Control & Data Deletion

* Toggle **Learn words as I type** off at any time in the Siriri Keyboard settings.
* Long-press any suggestion on the keyboard suggestion strip to immediately forget that individual word.
* Tap **Clear all saved words** in Settings to permanently erase all learned words from memory and delete `words.bin` from your device.
* Uninstalling the app permanently deletes all locally stored preferences and learned words.

## 5. Third-Party Services

Siriri Keyboard uses **zero third-party dependencies** in its Android shell and only the `jni` glue crate in its offline Rust core. It does not integrate with any third-party APIs, servers, or services.

## 6. Children's Privacy

Because Siriri Keyboard collects no personal data whatsoever and operates 100% offline, it complies with children's privacy standards globally and collects no information from users of any age.

## 7. Changes & Contact

Because this project is open source, any future changes to this policy or the code can be inspected publicly in the repository:
https://github.com/bishal-majhi/siriri-keyboard
