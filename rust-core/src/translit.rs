//! Phonetic Roman -> Devanagari transliteration for Nepali and Hindi.
//!
//! Stateless: the keyboard re-runs `transliterate` on the whole Roman buffer
//! after every key, so backspace and live editing are trivially correct.
//!
//! Typing guide
//! * vowels      a  aa  i  ii/ee  u  uu/oo  e  ai  o  au   Ri (ऋ / ृ)
//! * consonants  k kh g gh | ch chh j jh | T Th D Dh N | t th d dh n |
//!               p ph b bh m | y r l v/w | sh (श) Sh (ष) s h | ksh x (क्ष) gy/jn (ज्ञ) L (ळ)
//!   (capital T D N Sh give the retroflex letters - hold Shift)
//! * M  anusvara ं     MM  chandrabindu ँ     H  visarga ः
//! * q  explicit halant (e.g. "sat" + "q" -> सत्)
//! * Two consonants in a row join into a conjunct: "prem" -> प्रेम
//! * A consonant followed by `a` takes no matra: "ram" -> रम, "raam" -> राम

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Lang {
    Nepali,
    Hindi,
}

#[derive(Clone, Copy)]
enum Tok {
    Cons(&'static str),
    Vowel { indep: &'static str, matra: &'static str },
    Mark(&'static str),
    Halant,
}

const VIRAMA: char = '\u{094D}';

use Tok::{Cons, Halant, Mark, Vowel};

/// Case-sensitive entries (checked against the text exactly as typed).
const EXACT: &[(&str, Tok)] = &[
    ("Th", Cons("ठ")),
    ("Dh", Cons("ढ")),
    ("Sh", Cons("ष")),
    ("T", Cons("ट")),
    ("D", Cons("ड")),
    ("N", Cons("ण")),
    ("L", Cons("ळ")),
    ("Ri", Vowel { indep: "ऋ", matra: "ृ" }),
    ("MM", Mark("ँ")),
    ("M", Mark("ं")),
    ("H", Mark("ः")),
];

/// Case-insensitive entries (checked against the lower-cased text).
const LOWER: &[(&str, Tok)] = &[
    ("chh", Cons("छ")),
    ("ksh", Cons("क्ष")),
    ("kh", Cons("ख")),
    ("gh", Cons("घ")),
    ("ch", Cons("च")),
    ("jh", Cons("झ")),
    ("th", Cons("थ")),
    ("dh", Cons("ध")),
    ("ph", Cons("फ")),
    ("bh", Cons("भ")),
    ("sh", Cons("श")),
    ("gy", Cons("ज्ञ")),
    ("jn", Cons("ज्ञ")),
    ("k", Cons("क")),
    ("g", Cons("ग")),
    ("c", Cons("च")),
    ("j", Cons("ज")),
    ("t", Cons("त")),
    ("d", Cons("द")),
    ("n", Cons("न")),
    ("p", Cons("प")),
    ("b", Cons("ब")),
    ("m", Cons("म")),
    ("y", Cons("य")),
    ("r", Cons("र")),
    ("l", Cons("ल")),
    ("v", Cons("व")),
    ("w", Cons("व")),
    ("s", Cons("स")),
    ("h", Cons("ह")),
    ("f", Cons("फ")),
    ("z", Cons("ज")),
    ("x", Cons("क्ष")),
    ("q", Halant),
    ("aa", Vowel { indep: "आ", matra: "ा" }),
    ("ai", Vowel { indep: "ऐ", matra: "ै" }),
    ("au", Vowel { indep: "औ", matra: "ौ" }),
    ("ee", Vowel { indep: "ई", matra: "ी" }),
    ("ii", Vowel { indep: "ई", matra: "ी" }),
    ("oo", Vowel { indep: "ऊ", matra: "ू" }),
    ("uu", Vowel { indep: "ऊ", matra: "ू" }),
    ("a", Vowel { indep: "अ", matra: "" }),
    ("i", Vowel { indep: "इ", matra: "ि" }),
    ("u", Vowel { indep: "उ", matra: "ु" }),
    ("e", Vowel { indep: "ए", matra: "े" }),
    ("o", Vowel { indep: "ओ", matra: "ो" }),
];

/// Longest table entry matching the start of `text`. `orig` is the text as
/// typed (same byte length); for multi-letter entries a lower-case letter
/// followed by an upper-case one ("kH") is not a digraph - that is a letter
/// plus a separate mark - while "Chh" and "SH" still are.
fn longest(table: &'static [(&'static str, Tok)], text: &str, orig: &str) -> Option<(usize, Tok)> {
    let mut best: Option<(usize, Tok)> = None;
    for (key, tok) in table {
        if !text.starts_with(key) || best.map_or(false, |(l, _)| key.len() <= l) {
            continue;
        }
        let typed = &orig[..key.len()];
        let mixed = key.len() > 1
            && typed.starts_with(|c: char| c.is_ascii_lowercase())
            && typed[1..].chars().any(|c| c.is_ascii_uppercase());
        if !mixed {
            best = Some((key.len(), *tok));
        }
    }
    best
}

pub fn transliterate(roman: &str, lang: Lang) -> String {
    let mut out = String::with_capacity(roman.len() * 3);
    // true when the last thing written is a consonant that has not yet
    // received a vowel sign or a virama.
    let mut pending = false;
    let mut rest = roman;

    while !rest.is_empty() {
        let lower = rest.to_ascii_lowercase();
        let exact = longest(EXACT, rest, rest);
        let fold = longest(LOWER, &lower, rest);
        let hit = match (exact, fold) {
            (Some(e), Some(f)) => Some(if e.0 >= f.0 { e } else { f }),
            (e, f) => e.or(f),
        };

        let Some((len, tok)) = hit else {
            // Not a letter we know (digit, punctuation, non-ASCII): pass through.
            let c = rest.chars().next().unwrap();
            out.push(c);
            pending = false;
            rest = &rest[c.len_utf8()..];
            continue;
        };
        let consumed = &rest[..len];
        rest = &rest[len..];

        match tok {
            Cons(s) => {
                if pending {
                    out.push(VIRAMA);
                }
                match (lang, s, consumed.to_ascii_lowercase().as_str()) {
                    // Hindi borrows f / z with a nukta.
                    (Lang::Hindi, _, "f") => out.push_str("फ\u{093C}"),
                    (Lang::Hindi, _, "z") => out.push_str("ज\u{093C}"),
                    _ => out.push_str(s),
                }
                pending = true;
            }
            Vowel { indep, matra } => {
                out.push_str(if pending { matra } else { indep });
                pending = false;
            }
            Mark(s) => {
                out.push_str(s);
                pending = false;
            }
            Halant => {
                if pending {
                    out.push(VIRAMA);
                    pending = false;
                }
            }
        }
    }
    out
}

/// Up to 3 distinct phonetic Devanagari candidates for `roman` (primary first,
/// followed by vowel-lengthened and retroflex/sibilant alternatives).
pub fn variants(roman: &str, lang: Lang) -> Vec<String> {
    if roman.is_empty() {
        return Vec::new();
    }
    let primary = transliterate(roman, lang);
    let mut out = vec![primary];
    let mut push_unique = |r: String| {
        if out.len() < 3 {
            let t = transliterate(&r, lang);
            if !t.is_empty() && !out.contains(&t) {
                out.push(t);
            }
        }
    };

    // 1. Word-final short vowel -> long vowel ("aama" -> "आमा", "timi" -> "तिमी", "guru" -> "गुरू")
    if roman.len() >= 2 {
        if roman.ends_with('a') && !roman.ends_with("aa") {
            push_unique(format!("{roman}a"));
        } else if roman.ends_with('i') && !roman.ends_with("ii") && !roman.ends_with("ai") {
            push_unique(format!("{roman}i"));
        } else if roman.ends_with('u') && !roman.ends_with("uu") && !roman.ends_with("au") {
            push_unique(format!("{roman}u"));
        }
    }

    // 2. Dental/sibilant -> retroflex/palatal swap for users who don't hold Shift
    let mut retro = String::with_capacity(roman.len() + 2);
    let bytes = roman.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        let rest = &roman[i..];
        if rest.starts_with("th") {
            retro.push_str("Th");
            i += 2;
        } else if rest.starts_with("dh") {
            retro.push_str("Dh");
            i += 2;
        } else if rest.starts_with("sh") {
            retro.push_str("Sh");
            i += 2;
        } else {
            let c = rest.chars().next().unwrap();
            match c {
                't' => retro.push('T'),
                'd' => retro.push('D'),
                'n' if i > 0 => retro.push('N'),
                's' => retro.push_str("sh"),
                _ => retro.push(c),
            }
            i += c.len_utf8();
        }
    }
    if retro != roman {
        push_unique(retro);
    }

    // 3. Trailing 'a' -> 'aa' on single consonant ("ta" -> "ता") if slot remains
    if !roman.ends_with('a') {
        push_unique(format!("{roman}aa"));
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ne(s: &str) -> String {
        transliterate(s, Lang::Nepali)
    }

    #[test]
    fn common_nepali_words() {
        assert_eq!(ne("namaste"), "नमस्ते");
        assert_eq!(ne("nepaal"), "नेपाल");
        assert_eq!(ne("prem"), "प्रेम");
        assert_eq!(ne("ghar"), "घर");
        assert_eq!(ne("hunchha"), "हुन्छ");
        assert_eq!(ne("gyaan"), "ज्ञान");
        assert_eq!(ne("kaaThamaaDau"), "काठमाडौ");
        assert_eq!(ne("kshatriya"), "क्षत्रिय");
    }

    #[test]
    fn independent_vowels_at_word_start() {
        assert_eq!(ne("a"), "अ");
        assert_eq!(ne("aama"), "आम");
        assert_eq!(ne("ek"), "एक");
        assert_eq!(ne("oo"), "ऊ");
    }

    #[test]
    fn retroflex_via_capitals_and_case_folding() {
        assert_eq!(ne("T"), "ट");
        assert_eq!(ne("Dh"), "ढ");
        assert_eq!(ne("Sh"), "ष");
        assert_eq!(ne("SH"), "श"); // unknown capital pair folds to plain sh
        assert_eq!(ne("Ram"), "रम");
    }

    #[test]
    fn marks_and_explicit_halant() {
        assert_eq!(ne("kM"), "कं");
        assert_eq!(ne("sq"), "स्");
        assert_eq!(ne("kH"), "कः");
        assert_eq!(ne("kMM"), "कँ");
    }

    #[test]
    fn partial_input_is_prefix_of_final_word() {
        // Suggestions rely on this: every intermediate state of typing
        // "namaste" must be a prefix of the finished word.
        let full = ne("namaste");
        for n in 3..=7 {
            let part = ne(&"namaste"[..n]);
            assert!(full.starts_with(&part), "{part} !prefix {full}");
        }
    }

    #[test]
    fn hindi_nukta_letters() {
        assert_eq!(transliterate("fan", Lang::Hindi), "फ़न");
        assert_eq!(transliterate("zindagi", Lang::Hindi), "ज़िन्दगि");
        assert_eq!(ne("fan"), "फन");
    }

    #[test]
    fn passthrough_and_empty() {
        assert_eq!(ne(""), "");
        assert_eq!(ne("5"), "5");
    }

    #[test]
    fn phonetic_variants_offer_long_vowels_and_retroflex() {
        let v = variants("aama", Lang::Nepali);
        assert_eq!(v[0], "आम");
        assert!(v.contains(&"आमा".to_string()));

        let v2 = variants("timi", Lang::Nepali);
        assert_eq!(v2[0], "तिमि");
        assert!(v2.contains(&"तिमी".to_string()));

        let v3 = variants("ta", Lang::Nepali);
        assert_eq!(v3[0], "त");
        assert!(v3.contains(&"ता".to_string()) || v3.contains(&"ट".to_string()));
    }
}
