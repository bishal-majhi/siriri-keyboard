//! Keyboard layouts, serialised for the Kotlin renderer.
//!
//! Wire format (plain text, no JSON parser needed on the Kotlin side):
//!
//! ```text
//! <columns>\n
//! <key>\t<key>\t...\n          one line per row
//! ```
//! where `<key>` = `kind \x1f base \x1f shift \x1f weight`.
//!
//! * `kind`   : `C` char, `_` spacer, `SHIFT`, `BKSP`, `NUM`, `SYM`, `ABC`,
//!              `EMOJI`, `GLOBE`, `SPACE`, `RET`
//! * `base`   : text committed (for `C`) or the label (for modifier keys)
//! * `shift`  : text committed while shifted (empty = same as `base`)
//! * `weight` : width in grid columns (1.0 = one letter key)

use crate::Mode;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Page {
    Letters = 0,
    Numbers = 1,
    Symbols = 2,
}

impl Page {
    pub fn from_i32(v: i32) -> Page {
        match v {
            1 => Page::Numbers,
            2 => Page::Symbols,
            _ => Page::Letters,
        }
    }
}

struct K {
    kind: &'static str,
    base: String,
    shift: String,
    w: f32,
}

fn ch(base: &str, shift: &str) -> K {
    K { kind: "C", base: base.into(), shift: shift.into(), w: 1.0 }
}
fn plain(base: &str) -> K {
    ch(base, "")
}
fn spacer(w: f32) -> K {
    K { kind: "_", base: String::new(), shift: String::new(), w }
}
fn special(kind: &'static str, label: &str, w: f32) -> K {
    K { kind, base: label.into(), shift: String::new(), w }
}

/// One Latin letter key per char, shifted = uppercase.
fn latin(s: &str) -> Vec<K> {
    s.chars().map(|c| ch(&c.to_string(), &c.to_uppercase().to_string())).collect()
}
/// Zip base/shifted label lists.
fn pairs(base: &[&str], shift: &[&str]) -> Vec<K> {
    assert_eq!(base.len(), shift.len());
    base.iter().zip(shift).map(|(b, s)| ch(b, s)).collect()
}
/// Keys with no shifted form.
fn singles(items: &[&str]) -> Vec<K> {
    items.iter().map(|s| plain(s)).collect()
}
fn weighted(mut keys: Vec<K>, w: f32) -> Vec<K> {
    for k in &mut keys {
        k.w = w;
    }
    keys
}

fn bottom_row(first_kind: &'static str, first_label: &str, wide: bool) -> Vec<K> {
    let (a, e, b, c, d) = if wide {
        (1.3, 1.15, 1.15, 5.2, 2.2)
    } else {
        (1.2, 1.05, 1.05, 4.6, 2.1)
    };
    vec![
        special(first_kind, first_label, a),
        special("EMOJI", "", e),
        special("GLOBE", "", b),
        special("SPACE", "", c),
        special("RET", "", d),
    ]
}

fn latin_letters() -> (u32, Vec<Vec<K>>) {
    let mut r2 = vec![spacer(0.5)];
    r2.extend(latin("asdfghjkl"));
    r2.push(spacer(0.5));
    let mut r3 = vec![special("SHIFT", "", 1.5)];
    r3.extend(latin("zxcvbnm"));
    r3.push(special("BKSP", "", 1.5));
    (10, vec![latin("qwertyuiop"), r2, r3, bottom_row("NUM", "123", false)])
}

fn nepali_letters() -> (u32, Vec<Vec<K>>) {
    let r1 = pairs(
        &["ौ", "ै", "ा", "ी", "ू", "ब", "ह", "ग", "द", "ज", "ड"],
        &["औ", "ऐ", "आ", "ई", "ऊ", "भ", "ऋ", "घ", "ध", "झ", "ढ"],
    );
    let r2 = pairs(
        &["ो", "े", "्", "ि", "ु", "प", "र", "क", "त", "च", "ट"],
        &["ओ", "ए", "अ", "इ", "उ", "फ", "ृ", "ख", "थ", "छ", "ठ"],
    );
    let mut r3 = vec![special("SHIFT", "", 1.5)];
    r3.extend(pairs(
        &["ं", "ँ", "म", "न", "व", "ल", "स", "य"],
        &["ॅ", "ॲ", "ज्ञ", "ण", "क्ष", "ळ", "श", "ष"],
    ));
    r3.push(special("BKSP", "", 1.5));
    (11, vec![r1, r2, r3, bottom_row("NUM", "१२३", true)])
}

fn latin_numbers() -> (u32, Vec<Vec<K>>) {
    let r1 = singles(&["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]);
    let r2 = singles(&["-", "/", ":", ";", "(", ")", "$", "&", "@", "\""]);
    let mut r3 = vec![special("SYM", "#+=", 1.5)];
    r3.extend(weighted(singles(&[".", ",", "?", "!", "'"]), 1.4));
    r3.push(special("BKSP", "", 1.5));
    (10, vec![r1, r2, r3, bottom_row("ABC", "ABC", false)])
}

fn latin_symbols() -> (u32, Vec<Vec<K>>) {
    let r1 = singles(&["[", "]", "{", "}", "#", "%", "^", "*", "+", "="]);
    let r2 = singles(&["_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"]);
    let mut r3 = vec![special("NUM", "123", 1.5)];
    r3.extend(weighted(singles(&[".", ",", "?", "!", "'"]), 1.4));
    r3.push(special("BKSP", "", 1.5));
    (10, vec![r1, r2, r3, bottom_row("ABC", "ABC", false)])
}

fn nepali_numbers() -> (u32, Vec<Vec<K>>) {
    let r1 = singles(&["१", "२", "३", "४", "५", "६", "७", "८", "९", "०"]);
    let r2 = singles(&["-", "/", ":", ";", "(", ")", "रु", "&", "@", "\""]);
    let mut r3 = vec![special("SYM", "#+=", 1.5)];
    r3.extend(weighted(singles(&[".", ",", "।", "?", "!"]), 1.4));
    r3.push(special("BKSP", "", 1.5));
    (10, vec![r1, r2, r3, bottom_row("ABC", "कखग", false)])
}

fn nepali_symbols() -> (u32, Vec<Vec<K>>) {
    let r1 = singles(&["ऽ", "ॐ", "।", "॥", "[", "]", "{", "}", "#", "%"]);
    let r2 = singles(&["^", "*", "+", "=", "_", "\\", "|", "~", "<", ">"]);
    let mut r3 = vec![special("NUM", "१२३", 1.5)];
    r3.extend(weighted(singles(&[".", ",", "?", "!", "'"]), 1.4));
    r3.push(special("BKSP", "", 1.5));
    (10, vec![r1, r2, r3, bottom_row("ABC", "कखग", false)])
}

fn build(mode: Mode, page: Page) -> (u32, Vec<Vec<K>>) {
    match (mode, page) {
        (Mode::Nepali, Page::Letters) => nepali_letters(),
        (Mode::Nepali, Page::Numbers) => nepali_numbers(),
        (Mode::Nepali, Page::Symbols) => nepali_symbols(),
        // English, Nepali Roman and Hindi Roman all type on the Latin grid.
        (_, Page::Letters) => latin_letters(),
        (_, Page::Numbers) => latin_numbers(),
        (_, Page::Symbols) => latin_symbols(),
    }
}

pub fn layout(mode: Mode, page: Page) -> String {
    let (cols, rows) = build(mode, page);
    let mut out = cols.to_string();
    for row in rows {
        out.push('\n');
        let keys: Vec<String> = row
            .iter()
            .map(|k| format!("{}\u{1f}{}\u{1f}{}\u{1f}{}", k.kind, k.base, k.shift, k.w))
            .collect();
        out.push_str(&keys.join("\t"));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(s: &str) -> (u32, Vec<Vec<(String, String, String, f32)>>) {
        let mut lines = s.split('\n');
        let cols: u32 = lines.next().unwrap().parse().unwrap();
        let rows = lines
            .map(|l| {
                l.split('\t')
                    .map(|k| {
                        let f: Vec<&str> = k.split('\u{1f}').collect();
                        (f[0].to_string(), f[1].to_string(), f[2].to_string(), f[3].parse().unwrap())
                    })
                    .collect()
            })
            .collect();
        (cols, rows)
    }

    #[test]
    fn every_row_fills_the_grid() {
        for mode in [Mode::English, Mode::Nepali, Mode::NepaliRoman, Mode::HindiRoman] {
            for page in [Page::Letters, Page::Numbers, Page::Symbols] {
                let (cols, rows) = parse(&layout(mode, page));
                assert_eq!(rows.len(), 4, "{mode:?} {page:?}");
                for (i, r) in rows.iter().enumerate() {
                    let sum: f32 = r.iter().map(|k| k.3).sum();
                    assert!((sum - cols as f32).abs() < 0.001, "{mode:?} {page:?} row {i}: {sum} != {cols}");
                }
            }
        }
    }

    #[test]
    fn english_shape_is_10_9_7() {
        let (cols, rows) = parse(&layout(Mode::English, Page::Letters));
        assert_eq!(cols, 10);
        let n = |r: usize| rows[r].iter().filter(|k| k.0 == "C").count();
        assert_eq!((n(0), n(1), n(2)), (10, 9, 7));
        assert_eq!(rows[0][0].1, "q");
        assert_eq!(rows[0][0].2, "Q");
    }

    #[test]
    fn nepali_shape_is_11_11_8() {
        let (cols, rows) = parse(&layout(Mode::Nepali, Page::Letters));
        assert_eq!(cols, 11);
        let n = |r: usize| rows[r].iter().filter(|k| k.0 == "C").count();
        assert_eq!((n(0), n(1), n(2)), (11, 11, 8));
    }

    #[test]
    fn nepali_shift_map_matches_spec() {
        let (_, rows) = parse(&layout(Mode::Nepali, Page::Letters));
        let map = |r: usize| -> Vec<(String, String)> {
            rows[r].iter().filter(|k| k.0 == "C").map(|k| (k.1.clone(), k.2.clone())).collect()
        };
        let joined = |r: usize| {
            let m = map(r);
            (
                m.iter().map(|p| p.0.clone()).collect::<Vec<_>>().join(" "),
                m.iter().map(|p| p.1.clone()).collect::<Vec<_>>().join(" "),
            )
        };
        assert_eq!(joined(0), ("ौ ै ा ी ू ब ह ग द ज ड".into(), "औ ऐ आ ई ऊ भ ऋ घ ध झ ढ".into()));
        assert_eq!(joined(1), ("ो े ् ि ु प र क त च ट".into(), "ओ ए अ इ उ फ ृ ख थ छ ठ".into()));
        assert_eq!(joined(2), ("ं ँ म न व ल स य".into(), "ॅ ॲ ज्ञ ण क्ष ळ श ष".into()));
    }

    #[test]
    fn nepali_symbols_have_the_devanagari_extras() {
        let all = format!("{}{}", layout(Mode::Nepali, Page::Numbers), layout(Mode::Nepali, Page::Symbols));
        for s in ["१", "०", "ऽ", "ॐ", "।", "॥"] {
            assert!(all.contains(s), "missing {s}");
        }
    }

    #[test]
    fn bottom_row_places_emoji_between_first_and_globe() {
        for mode in [Mode::English, Mode::Nepali, Mode::NepaliRoman, Mode::HindiRoman] {
            for page in [Page::Letters, Page::Numbers, Page::Symbols] {
                let (_, rows) = parse(&layout(mode, page));
                let kinds: Vec<&str> = rows[3].iter().map(|k| k.0.as_str()).collect();
                let expected_first = if page == Page::Letters { "NUM" } else { "ABC" };
                assert_eq!(kinds, vec![expected_first, "EMOJI", "GLOBE", "SPACE", "RET"]);
            }
        }
    }
}
