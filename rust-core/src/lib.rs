//! Siriri Nepali Keyboard core engine.
//!
//! Everything the keyboard "knows" lives here: key layouts, phonetic
//! Roman -> Devanagari transliteration, and the on-device learned-word store.
//! The Kotlin shell only draws keys and talks to the Android text-input API.
//!
//! There is deliberately no networking code, no logging and no telemetry in
//! this crate. The only I/O is reading/writing one file whose path the host
//! hands us (the app's private storage).

pub mod jni_api;
pub mod layout;
pub mod store;
pub mod translit;

/// Typing mode. The numeric values are shared with Kotlin (`Mode` in `Types.kt`).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Mode {
    /// "En" - English, QWERTY.
    English = 0,
    /// "ने" - Traditional Nepali, 11-column Devanagari.
    Nepali = 1,
    /// "Np" - Roman Nepali (phonetic: type "namaste" -> नमस्ते).
    NepaliRoman = 2,
    /// "Hi" - Hindi Roman (phonetic).
    HindiRoman = 3,
}

impl Mode {
    pub fn from_i32(v: i32) -> Mode {
        match v {
            1 => Mode::Nepali,
            2 => Mode::NepaliRoman,
            3 => Mode::HindiRoman,
            _ => Mode::English,
        }
    }
}
