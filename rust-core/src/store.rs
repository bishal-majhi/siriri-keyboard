//! On-device learned-word store.
//!
//! No bundled dictionary: every suggestion comes from words the user has
//! actually typed. Data is kept in memory and persisted to ONE file inside the
//! app's private storage (path supplied by the host). `clear()` wipes memory
//! and deletes the file.

use std::collections::BTreeMap;
use std::fs;
use std::ops::Bound;
use std::path::PathBuf;

const MAX_WORDS: usize = 30_000;
const MAX_WORD_CHARS: usize = 40;
const MAX_NEXT_PER_WORD: usize = 6;
const MAX_PREV_WORDS: usize = 20_000;
const MAGIC: &[u8; 4] = b"SRW1";

/// Characters that may appear inside a learnable word: letters (Latin,
/// Devanagari consonants/vowels/matras...), apostrophes, virama, nukta, ZWJ/ZWNJ.
pub fn is_word_char(c: char) -> bool {
    c.is_alphabetic()
        || matches!(c, '\'' | '\u{0900}'..='\u{0903}' | '\u{093C}' | '\u{094D}' | '\u{200C}' | '\u{200D}')
}

/// Canonical form used as a key, or `None` if this is not a learnable word
/// (digits, e-mail/URL fragments, too long, ...).
pub fn normalize(w: &str) -> Option<String> {
    let t: String = w.chars().map(|c| if c == '\u{2019}' { '\'' } else { c }).collect();
    let t = t.trim_matches('\'');
    if t.is_empty() || t.chars().count() > MAX_WORD_CHARS {
        return None;
    }
    if !t.chars().all(is_word_char) || !t.chars().any(|c| c.is_alphabetic()) {
        return None;
    }
    Some(t.to_lowercase())
}

#[derive(Clone, Copy)]
struct Entry {
    count: u32,
    last: u32,
}

pub struct Store {
    words: BTreeMap<String, Entry>,
    next: BTreeMap<String, Vec<(String, u32)>>,
    seq: u32,
    dirty: bool,
    path: Option<PathBuf>,
}

impl Store {
    pub const fn new() -> Store {
        Store { words: BTreeMap::new(), next: BTreeMap::new(), seq: 0, dirty: false, path: None }
    }

    /// Load from `path` (missing or corrupt file => empty store) and remember
    /// the path for later saves.
    pub fn open(path: PathBuf) -> Store {
        let mut s = fs::read(&path).ok().and_then(|b| Store::decode(&b)).unwrap_or_else(Store::new);
        s.path = Some(path);
        s
    }

    pub fn len(&self) -> usize {
        self.words.len()
    }

    pub fn is_empty(&self) -> bool {
        self.words.is_empty()
    }

    /// Record one typed word (and the word before it, for next-word hints).
    /// Returns false if the word was rejected.
    pub fn learn(&mut self, word: &str, prev: &str) -> bool {
        let Some(w) = normalize(word) else { return false };
        // Single ASCII letters are noise, except the real words "i" and "a".
        if w.chars().count() == 1 && w.is_ascii() && w != "i" && w != "a" {
            return false;
        }
        self.seq = self.seq.wrapping_add(1);
        let e = self.words.entry(w.clone()).or_insert(Entry { count: 0, last: 0 });
        e.count = e.count.saturating_add(1);
        e.last = self.seq;

        if let Some(p) = normalize(prev) {
            if !self.next.contains_key(&p) && self.next.len() >= MAX_PREV_WORDS {
                self.next.retain(|_, v| v.iter().any(|(_, c)| *c > 1));
            }
            let v = self.next.entry(p).or_default();
            if let Some(slot) = v.iter_mut().find(|(x, _)| *x == w) {
                slot.1 = slot.1.saturating_add(1);
            } else {
                v.push((w, 1));
                if v.len() > MAX_NEXT_PER_WORD {
                    if let Some(min) = (0..v.len()).min_by_key(|&i| v[i].1) {
                        v.swap_remove(min);
                    }
                }
            }
        }
        self.dirty = true;
        if self.words.len() > MAX_WORDS {
            self.prune();
        }
        true
    }

    fn prune(&mut self) {
        let mut all: Vec<(u32, u32, String)> =
            self.words.iter().map(|(k, e)| (e.count, e.last, k.clone())).collect();
        all.sort_unstable();
        for (_, _, k) in all.into_iter().take(MAX_WORDS / 10) {
            self.words.remove(&k);
        }
        self.next.retain(|k, v| {
            v.retain(|(x, _)| self.words.contains_key(x));
            self.words.contains_key(k) && !v.is_empty()
        });
    }

    /// Remove one word everywhere (suggestion long-press).
    pub fn forget(&mut self, word: &str) {
        let Some(w) = normalize(word) else { return };
        self.words.remove(&w);
        self.next.remove(&w);
        for v in self.next.values_mut() {
            v.retain(|(x, _)| *x != w);
        }
        self.next.retain(|_, v| !v.is_empty());
        self.dirty = true;
    }

    /// Wipe everything, in memory and on disk.
    pub fn clear(&mut self) {
        self.words.clear();
        self.next.clear();
        self.seq = 0;
        self.dirty = false;
        if let Some(p) = &self.path {
            let _ = fs::remove_file(p);
            let _ = fs::remove_file(p.with_extension("tmp"));
        }
    }

    /// Up to `limit` suggestions. With an empty `prefix` the list is next-word
    /// predictions after `prev` topped up with the most used words.
    /// `seed_english` fills any empty slots with i / the / i'm so the very
    /// first launch does not show a blank strip.
    pub fn suggest(&self, prefix: &str, prev: &str, limit: usize, seed_english: bool) -> Vec<String> {
        let p = prefix.replace('\u{2019}', "'").to_lowercase();
        let prev_n = normalize(prev);
        let follow: &[(String, u32)] =
            prev_n.as_ref().and_then(|pv| self.next.get(pv)).map(|v| v.as_slice()).unwrap_or(&[]);
        let boost = |w: &str| -> u64 {
            follow.iter().find(|(x, _)| x == w).map_or(0, |(_, c)| *c as u64 * 8)
        };

        let mut top: Vec<(u64, u32, &str)> = Vec::with_capacity(limit + 1);

        if p.is_empty() {
            for (w, c) in follow {
                if let Some(e) = self.words.get(w) {
                    push_top(&mut top, (c.saturating_mul(8) as u64 + e.count as u64, e.last, w.as_str()), limit);
                }
            }
            if top.len() < limit {
                for (w, e) in &self.words {
                    if follow.iter().any(|(x, _)| x == w) {
                        continue;
                    }
                    push_top(&mut top, (e.count as u64, e.last, w.as_str()), limit);
                }
            }
        } else {
            let range = self.words.range::<str, _>((Bound::Included(p.as_str()), Bound::Unbounded));
            for (w, e) in range {
                if !w.starts_with(&p) {
                    break;
                }
                if *w == p {
                    continue;
                }
                push_top(&mut top, (e.count as u64 + boost(w), e.last, w.as_str()), limit);
            }
        }

        let mut out: Vec<String> = top.into_iter().map(|(_, _, w)| w.to_string()).collect();
        if seed_english && p.is_empty() {
            for s in ["i", "the", "i'm"] {
                if out.len() >= limit {
                    break;
                }
                if !out.iter().any(|x| x == s) {
                    out.push(s.to_string());
                }
            }
        }
        out
    }

    // ---- persistence -----------------------------------------------------

    pub fn save(&mut self) -> std::io::Result<()> {
        if !self.dirty {
            return Ok(());
        }
        let Some(path) = self.path.clone() else { return Ok(()) };
        let buf = self.encode();
        let tmp = path.with_extension("tmp");
        fs::write(&tmp, &buf)?;
        fs::rename(&tmp, &path)?;
        self.dirty = false;
        Ok(())
    }

    fn encode(&self) -> Vec<u8> {
        fn put_str(b: &mut Vec<u8>, s: &str) {
            b.extend_from_slice(&(s.len() as u16).to_le_bytes());
            b.extend_from_slice(s.as_bytes());
        }
        let mut b = Vec::with_capacity(self.words.len() * 24 + 64);
        b.extend_from_slice(MAGIC);
        b.extend_from_slice(&self.seq.to_le_bytes());
        b.extend_from_slice(&(self.words.len() as u32).to_le_bytes());
        for (w, e) in &self.words {
            put_str(&mut b, w);
            b.extend_from_slice(&e.count.to_le_bytes());
            b.extend_from_slice(&e.last.to_le_bytes());
        }
        b.extend_from_slice(&(self.next.len() as u32).to_le_bytes());
        for (p, v) in &self.next {
            put_str(&mut b, p);
            b.push(v.len() as u8);
            for (w, c) in v {
                put_str(&mut b, w);
                b.extend_from_slice(&c.to_le_bytes());
            }
        }
        b
    }

    fn decode(b: &[u8]) -> Option<Store> {
        struct R<'a>(&'a [u8]);
        impl<'a> R<'a> {
            fn take(&mut self, n: usize) -> Option<&'a [u8]> {
                if self.0.len() < n {
                    return None;
                }
                let (h, t) = self.0.split_at(n);
                self.0 = t;
                Some(h)
            }
            fn u8(&mut self) -> Option<u8> {
                Some(self.take(1)?[0])
            }
            fn u16(&mut self) -> Option<u16> {
                Some(u16::from_le_bytes(self.take(2)?.try_into().ok()?))
            }
            fn u32(&mut self) -> Option<u32> {
                Some(u32::from_le_bytes(self.take(4)?.try_into().ok()?))
            }
            fn string(&mut self) -> Option<String> {
                let n = self.u16()? as usize;
                String::from_utf8(self.take(n)?.to_vec()).ok()
            }
        }
        let mut r = R(b);
        if r.take(4)? != MAGIC {
            return None;
        }
        let mut s = Store::new();
        s.seq = r.u32()?;
        for _ in 0..r.u32()? {
            let w = r.string()?;
            let count = r.u32()?;
            let last = r.u32()?;
            s.words.insert(w, Entry { count, last });
        }
        for _ in 0..r.u32()? {
            let p = r.string()?;
            let n = r.u8()? as usize;
            let mut v = Vec::with_capacity(n);
            for _ in 0..n {
                let w = r.string()?;
                v.push((w, r.u32()?));
            }
            s.next.insert(p, v);
        }
        Some(s)
    }
}

/// Insert into a descending top-k list without allocating for losers.
fn push_top<'a>(top: &mut Vec<(u64, u32, &'a str)>, item: (u64, u32, &'a str), k: usize) {
    if k == 0 {
        return;
    }
    if top.len() == k {
        let worst = &top[k - 1];
        if (item.0, item.1) <= (worst.0, worst.1) {
            return;
        }
    }
    let pos = top.iter().position(|t| (item.0, item.1) > (t.0, t.1)).unwrap_or(top.len());
    top.insert(pos, item);
    top.truncate(k);
}

#[cfg(test)]
mod tests {
    use super::*;

    fn learned(words: &[&str]) -> Store {
        let mut s = Store::new();
        let mut prev = "";
        for w in words {
            s.learn(w, prev);
            prev = w;
        }
        s
    }

    #[test]
    fn rejects_non_words() {
        let mut s = Store::new();
        for bad in ["", "123", "a1b", "x", "me@mail.com", "http://x", "😀", "''", &"a".repeat(41)] {
            assert!(!s.learn(bad, ""), "{bad:?} should be rejected");
        }
        assert!(s.is_empty());
        assert!(s.learn("I", ""));
        assert!(s.learn("I’m", ""));
        assert!(s.learn("नमस्ते", ""));
    }

    #[test]
    fn prefix_ranks_by_frequency_then_recency() {
        let mut s = Store::new();
        for _ in 0..3 {
            s.learn("hello", "");
        }
        s.learn("help", "");
        s.learn("helmet", "");
        s.learn("world", "");
        assert_eq!(s.suggest("hel", "", 3, false), vec!["hello", "helmet", "help"]);
        assert_eq!(s.suggest("Hel", "", 1, false), vec!["hello"]); // case-insensitive prefix
        assert!(s.suggest("zzz", "", 3, false).is_empty());
        // the typed word itself is not suggested
        assert!(!s.suggest("hello", "", 3, false).contains(&"hello".to_string()));
    }

    #[test]
    fn devanagari_prefix_and_ordering() {
        let s = learned(&["नमस्ते", "नमस्कार", "नेपाल"]);
        let got = s.suggest("नम", "", 3, false);
        assert_eq!(got.len(), 2);
        assert!(got.contains(&"नमस्ते".to_string()) && got.contains(&"नमस्कार".to_string()));
    }

    #[test]
    fn next_word_prediction_beats_global_frequency() {
        let mut s = Store::new();
        for _ in 0..5 {
            s.learn("coffee", "");
        }
        s.learn("good", "");
        s.learn("morning", "good");
        s.learn("morning", "good");
        let got = s.suggest("", "good", 3, false);
        assert_eq!(got[0], "morning");
        assert!(got.contains(&"coffee".to_string()));
    }

    #[test]
    fn english_seed_only_when_empty_slots_remain() {
        let s = Store::new();
        assert_eq!(s.suggest("", "", 3, true), vec!["i", "the", "i'm"]);
        assert!(s.suggest("", "", 3, false).is_empty());
        assert!(s.suggest("th", "", 3, true).is_empty()); // seeds never answer a prefix
    }

    #[test]
    fn forget_removes_everywhere() {
        let mut s = learned(&["good", "morning", "sunshine"]);
        s.forget("Morning");
        assert!(s.suggest("mor", "", 3, false).is_empty());
        assert!(!s.suggest("", "good", 3, false).contains(&"morning".to_string()));
    }

    #[test]
    fn persistence_roundtrip_and_clear() {
        let dir = std::env::temp_dir().join(format!("siriri-test-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        let path = dir.join("words.bin");
        let mut s = Store::open(path.clone());
        s.learn("hello", "");
        s.learn("world", "hello");
        s.learn("नमस्ते", "");
        s.save().unwrap();
        assert!(path.exists());

        let s2 = Store::open(path.clone());
        assert_eq!(s2.len(), 3);
        assert_eq!(s2.suggest("", "hello", 1, false), vec!["world"]);
        assert_eq!(s2.suggest("नम", "", 1, false), vec!["नमस्ते"]);

        let mut s3 = Store::open(path.clone());
        s3.clear();
        assert!(s3.is_empty());
        assert!(!path.exists());
        assert!(Store::open(path).is_empty());
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn corrupt_file_yields_empty_store() {
        let dir = std::env::temp_dir().join(format!("siriri-bad-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        let path = dir.join("words.bin");
        fs::write(&path, b"SRW1\x01\x00garbage").unwrap();
        assert!(Store::open(path).is_empty());
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn size_cap_prunes_least_used() {
        let mut s = Store::new();
        for i in 0..(MAX_WORDS + 50) {
            // letters-only unique words
            let w: String = format!("{i:06}").chars().map(|d| (b'a' + d as u8 - b'0') as char).collect::<String>() + "zz";
            s.learn(&w, "");
        }
        assert!(s.len() <= MAX_WORDS);
    }
}
