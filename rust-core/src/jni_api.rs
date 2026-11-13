//! JNI bridge: `np.com.siriri.keyboard.Native` (all methods are @JvmStatic).
//!
//! Every entry point is wrapped in `catch_unwind` so a bug can never unwind
//! across the FFI boundary and kill the keyboard process.

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::PathBuf;
use std::sync::{Mutex, MutexGuard};

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::layout::{self, Page};
use crate::store::Store;
use crate::translit::{self, Lang};
use crate::Mode;

static STORE: Mutex<Store> = Mutex::new(Store::new());

fn store() -> MutexGuard<'static, Store> {
    STORE.lock().unwrap_or_else(|e| e.into_inner())
}

fn read(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(String::from).unwrap_or_default()
}

fn write(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn guard<T>(default: T, f: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or(default)
}

/// Open (or create) the learned-word file inside the app's private dir.
#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_init(mut env: JNIEnv, _: JClass, dir: JString) {
    let dir = read(&mut env, &dir);
    guard((), || {
        let mut s = store();
        // Only (re)open if we are not already bound to a file.
        if s.is_empty() {
            *s = Store::open(PathBuf::from(dir).join("words.bin"));
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_layout(
    mut env: JNIEnv,
    _: JClass,
    mode: jint,
    page: jint,
) -> jstring {
    let out = guard(String::new(), || layout::layout(Mode::from_i32(mode), Page::from_i32(page)));
    write(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_transliterate(
    mut env: JNIEnv,
    _: JClass,
    mode: jint,
    roman: JString,
) -> jstring {
    let roman = read(&mut env, &roman);
    let out = guard(roman.clone(), || match Mode::from_i32(mode) {
        Mode::NepaliRoman => translit::transliterate(&roman, Lang::Nepali),
        Mode::HindiRoman => translit::transliterate(&roman, Lang::Hindi),
        _ => roman.clone(),
    });
    write(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_transliterateVariants(
    mut env: JNIEnv,
    _: JClass,
    mode: jint,
    roman: JString,
) -> jstring {
    let roman = read(&mut env, &roman);
    let out = guard(String::new(), || match Mode::from_i32(mode) {
        Mode::NepaliRoman => translit::variants(&roman, Lang::Nepali).join("\n"),
        Mode::HindiRoman => translit::variants(&roman, Lang::Hindi).join("\n"),
        _ => String::new(),
    });
    write(&mut env, &out)
}

/// Returns suggestions joined with '\n'.
#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_suggest(
    mut env: JNIEnv,
    _: JClass,
    mode: jint,
    prefix: JString,
    prev: JString,
    limit: jint,
) -> jstring {
    let prefix = read(&mut env, &prefix);
    let prev = read(&mut env, &prev);
    let out = guard(String::new(), || {
        let seed = Mode::from_i32(mode) == Mode::English;
        store().suggest(&prefix, &prev, limit.max(0) as usize, seed).join("\n")
    });
    write(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_learn(
    mut env: JNIEnv,
    _: JClass,
    word: JString,
    prev: JString,
) -> jboolean {
    let word = read(&mut env, &word);
    let prev = read(&mut env, &prev);
    if guard(false, || store().learn(&word, &prev)) { JNI_TRUE } else { JNI_FALSE }
}

#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_forget(mut env: JNIEnv, _: JClass, word: JString) {
    let word = read(&mut env, &word);
    guard((), || {
        let mut s = store();
        s.forget(&word);
        let _ = s.save();
    });
}

/// Wipe every learned word, in memory and on disk.
#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_clearAll(_: JNIEnv, _: JClass) {
    guard((), || store().clear());
}

#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_wordCount(_: JNIEnv, _: JClass) -> jint {
    guard(0, || store().len() as jint)
}

/// Persist to disk if anything changed.
#[no_mangle]
pub extern "system" fn Java_np_com_siriri_keyboard_Native_save(_: JNIEnv, _: JClass) {
    guard((), || {
        let _ = store().save();
    });
}
