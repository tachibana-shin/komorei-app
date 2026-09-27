//! `base64` module — encode/decode run natively in the runner (no host round
//! trip), so sources that decode base64 blobs (grab playlists, embedded
//! configs) don't carry a decoder inside their wasm.

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use wasmi::{Caller, Linker};

use crate::abi;
use crate::state::RunnerData;

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("base64", "encode", encode).unwrap();
	linker.func_wrap("base64", "decode", decode).unwrap();
}

/// Standard base64 with padding.
fn encode(mut caller: Caller<'_, RunnerData>, ptr: u32, len: u32) -> i32 {
	let Some(data) = abi::read_bytes(&caller, ptr, len) else {
		return -1;
	};
	caller
		.data_mut()
		.store
		.store_raw(STANDARD.encode(&data).into_bytes())
}

/// Decode, following the WHATWG *forgiving-base64 decode* algorithm — the one
/// a browser's `atob` implements, and the reference every site-side grab script
/// is written against.
///
/// A strict decoder is a poor stand-in for it, and the difference is exactly
/// where these blobs go wrong. The site's JavaScript calls `atob` on whatever the
/// page hands it, so anything `atob` accepts has to be accepted here or the
/// source fails on a perfectly good payload. `atob` differs from a strict
/// decoder in four ways, all of them in the permissive direction:
///
/// 1. **ASCII whitespace is stripped**, not rejected. `'\t'`, `'\n'`, `'\x0C'`,
///    `'\r'` and `' '` are removed before anything else looks at the string, so a
///    blob that was line-wrapped in transit — or arrived through a
///    pretty-printer — decodes. A strict decoder rejects all of them.
/// 2. **Missing padding is fine.** `"QQ"` is `"A"`. Padding is optional, not
///    required.
/// 3. **Surplus padding is fine.** `"QQ="` and `"QQ==="` are also `"A"`. Every
///    engine strips a whole run of trailing `'='` rather than insisting on the
///    canonical count.
/// 4. **Non-zero trailing bits are discarded, not rejected.** `"QR=="` is `"A"`;
///    the two low bits of the last character are simply not part of any output
///    byte. A strict decoder configured to reject trailing bits calls the whole
///    string invalid.
///
/// What `atob` still rejects, and so does this: a length of `4n + 1` after
/// normalisation (one character cannot carry a byte), a `'='` anywhere other than
/// the trailing run, and any character outside the alphabet.
///
/// URL-safe input (`'-'`/`'_'` in place of `'+'`/`'/'`) is **not** something
/// `atob` accepts, but sources send it and the previous behaviour allowed it, so
/// it stays a second pass rather than becoming part of the `atob` emulation. The
/// standard alphabet is tried first, so an `atob`-compatible caller sees exactly
/// `atob` semantics.
fn decode(mut caller: Caller<'_, RunnerData>, ptr: u32, len: u32) -> i32 {
	let Some(data) = abi::read_string(&caller, ptr, len) else {
		return -1;
	};
	let decoded = forgiving_decode(&data, false).or_else(|()| forgiving_decode(&data, true));
	match decoded {
		Ok(bytes) => caller.data_mut().store.store_raw(bytes),
		Err(_) => -1,
	}
}

/// Outcome of [forgiving_decode], spelled out so the two reasons for failure
/// stay distinguishable at the call site.
type ForgivingResult = Result<Vec<u8>, ()>;

/// WHATWG *forgiving-base64 decode* over the standard alphabet, or over the
/// URL-safe one when [url_safe] is set.
fn forgiving_decode(input: &str, url_safe: bool) -> ForgivingResult {
	// 1. Remove all ASCII whitespace. `u8::is_ascii_whitespace` is exactly
	//    WHATWG's ASCII whitespace — tab, LF, FF, CR, space — and deliberately
	//    excludes vertical tab, which is not ASCII whitespace to the spec.
	let cleaned: String = input
		.chars()
		.filter(|c| !matches!(*c as u32, 0x09 | 0x0A | 0x0C | 0x0D | 0x20))
		.collect();

	// 2. Padding is a trailing run and is optional in both directions: the spec
	//    words it as "one or two", but every engine strips the whole run, and
	//    `"QQ==="` is `"A"` rather than a failure.
	let body = cleaned.trim_end_matches('=');

	// 3. A `'='` that is not part of the trailing run is not padding at all.
	if body.contains('=') {
		return Err(());
	}

	// 4. Decode. `buffer` holds the pending bits and `pending` how many of them
	//    are filled, 6 at a time; a whole 24-bit group is 3 bytes. Trailing bits
	//    that do not fill a byte are dropped rather than treated as corruption,
	//    which is rule 4 above.
	let mut out: Vec<u8> = Vec::with_capacity(body.len() * 3 / 4 + 3);
	let mut buffer: u32 = 0;
	let mut pending: u32 = 0;
	let mut chars: usize = 0;

	for c in body.chars() {
		let value = match c {
			'A'..='Z' => c as u32 - 'A' as u32,
			'a'..='z' => c as u32 - 'a' as u32 + 26,
			'0'..='9' => c as u32 - '0' as u32 + 52,
			'+' if !url_safe => 62,
			'/' if !url_safe => 63,
			'-' if url_safe => 62,
			'_' if url_safe => 63,
			_ => return Err(()),
		};
		chars += 1;
		buffer = (buffer << 6) | value;
		pending += 6;
		if pending == 24 {
			out.push((buffer >> 16) as u8);
			out.push((buffer >> 8) as u8);
			out.push(buffer as u8);
			buffer = 0;
			pending = 0;
		}
	}

	// 5. `4n + 1` carries six bits that cannot make a byte, so the input cannot
	//    have come from a real encoder. This is a length check on the surviving
	//    characters, so a multi-byte code point cannot skew it.
	if chars % 4 == 1 {
		return Err(());
	}

	match pending {
		12 => out.push((buffer >> 4) as u8),
		18 => {
			out.push((buffer >> 10) as u8);
			out.push((buffer >> 2) as u8);
		}
		_ => {}
	}

	Ok(out)
}

#[cfg(test)]
mod tests {
	use super::forgiving_decode;

	fn std(input: &str) -> Option<Vec<u8>> {
		forgiving_decode(input, false).ok()
	}

	fn url(input: &str) -> Option<Vec<u8>> {
		forgiving_decode(input, true).ok()
	}

	// ── the cases atob accepts ──────────────────────────────────────────────

	#[test]
	fn decodes_the_canonical_encodings() {
		assert_eq!(std(""), Some(vec![]));
		assert_eq!(std("QQ=="), Some(b"A".to_vec()));
		assert_eq!(std("QUI="), Some(b"AB".to_vec()));
		assert_eq!(std("QUJD"), Some(b"ABC".to_vec()));
		assert_eq!(std("QUJDRA=="), Some(b"ABCD".to_vec()));
		// The RFC 4648 test vectors.
		assert_eq!(std("Zg=="), Some(b"f".to_vec()));
		assert_eq!(std("Zm8="), Some(b"fo".to_vec()));
		assert_eq!(std("Zm9v"), Some(b"foo".to_vec()));
		assert_eq!(std("Zm9vYg=="), Some(b"foob".to_vec()));
		assert_eq!(std("Zm9vYmE="), Some(b"fooba".to_vec()));
		assert_eq!(std("Zm9vYmFy"), Some(b"foobar".to_vec()));
	}

	#[test]
	fn accepts_missing_padding() {
		// Rule 2: padding is optional.
		assert_eq!(std("QQ"), Some(b"A".to_vec()));
		assert_eq!(std("QUI"), Some(b"AB".to_vec()));
		assert_eq!(std("QUJDRA"), Some(b"ABCD".to_vec()));
		assert_eq!(std("Zm9vYmE"), Some(b"fooba".to_vec()));
	}

	#[test]
	fn accepts_surplus_padding() {
		// Rule 3: a whole trailing run of '=' is stripped, not counted.
		assert_eq!(std("QQ="), Some(b"A".to_vec()));
		assert_eq!(std("QQ=="), Some(b"A".to_vec()));
		assert_eq!(std("QQ==="), Some(b"A".to_vec()));
		assert_eq!(std("QQ========"), Some(b"A".to_vec()));
		assert_eq!(std("QUI="), Some(b"AB".to_vec()));
		assert_eq!(std("QUI=="), Some(b"AB".to_vec()));
		// A no-padding body with a run appended is still the same bytes.
		assert_eq!(std("Zm9v="), Some(b"foo".to_vec()));
		assert_eq!(std("Zm9v=="), Some(b"foo".to_vec()));
	}

	#[test]
	fn strips_ascii_whitespace() {
		// Rule 1: tab, LF, FF, CR and space are removed before anything else.
		// This is the case a strict decoder rejects outright, and the reason
		// `atob` is not emulated by "try a couple of engines".
		assert_eq!(std("QU\nJD"), Some(b"ABC".to_vec()));
		assert_eq!(std("QU\r\nJD"), Some(b"ABC".to_vec()));
		assert_eq!(std("QU JD"), Some(b"ABC".to_vec()));
		assert_eq!(std("QU\tJD"), Some(b"ABC".to_vec()));
		assert_eq!(std("QU\x0CJD"), Some(b"ABC".to_vec()));
		// A blob wrapped at 76 columns, the MIME convention.
		assert_eq!(
			std("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=\r\n"),
			Some(b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".to_vec())
		);
		// Leading and trailing whitespace too.
		assert_eq!(std("\n  Zm9v  \n"), Some(b"foo".to_vec()));
		// Whitespace and surplus padding together.
		assert_eq!(std(" Q U = = \n"), Some(b"A".to_vec()));
	}

	#[test]
	fn discards_non_zero_trailing_bits() {
		// Rule 4: the unused low bits of the last character are not corruption.
		// 'R' is 17 where 'Q' is 16, setting the two bits this output byte
		// never reads.
		assert_eq!(std("QR=="), Some(b"A".to_vec()));
		assert_eq!(std("QUJDRA"), Some(b"ABCD".to_vec()));
		assert_eq!(std("Zm9vYg=="), Some(b"foob".to_vec()));
		// All four variants of the same byte decode to it.
		for last in ["QQ==", "QR==", "QS==", "QT=="] {
			assert_eq!(std(last), Some(b"A".to_vec()), "{last}");
		}
	}

	// ── what atob still rejects ────────────────────────────────────────────

	#[test]
	fn rejects_a_length_of_four_n_plus_one() {
		// Six bits cannot make a byte, so no encoder emits this.
		assert_eq!(std("Q"), None);
		assert_eq!(std("QUJDQ"), None);
		assert_eq!(std("Zm9vY"), None);
		assert_eq!(std("Q===="), None);
		// Whitespace does not count toward the length.
		assert_eq!(std("Q = = = "), None);
	}

	#[test]
	fn rejects_padding_away_from_the_end() {
		assert_eq!(std("QQ=A"), None);
		assert_eq!(std("QU=JD"), None);
		assert_eq!(std("=QQ="), None);
	}

	#[test]
	fn rejects_characters_outside_the_alphabet() {
		assert_eq!(std("QU JD!".replace(' ', "").as_str()), None);
		assert_eq!(std("QU!JD"), None);
		assert_eq!(std("QU.JD"), None);
		assert_eq!(std("QU~JD"), None);
		// Unicode is not base64, and must not be counted as one character.
		assert_eq!(std("QUĴD"), None);
		assert_eq!(std("QU\u{000B}JD"), None);
	}

	#[test]
	fn vertical_tab_is_not_ascii_whitespace_to_the_spec() {
		// U+000B is excluded from WHATWG's ASCII whitespace, so it stays in the
		// string and fails the alphabet check rather than being stripped.
		assert_eq!(std("QU\u{000B}JD"), None);
	}

	// ── the URL-safe pass, kept for sources that rely on it ────────────────

	#[test]
	fn url_safe_is_a_second_pass_not_part_of_atob() {
		// `-`/`_` in place of `+`/`/`, in the second pass only. `-` and `_` are
		// 62 and 63, so "-_8" is the same 18 bits as "+/8" and decodes to FB FF.
		assert_eq!(url("-_8="), Some(vec![0xfb, 0xff]));
		assert_eq!(url("-_8"), Some(vec![0xfb, 0xff]));
		assert_eq!(std("+/8="), Some(vec![0xfb, 0xff]));
		// The standard pass is the `atob` one and rejects them.
		assert_eq!(std("-_8="), None);
		assert_eq!(std("-_8"), None);
		// The URL-safe alphabet is a superset, so it also reads plain standard
		// input — which is why the standard pass has to run first, to give an
		// `atob` caller exactly `atob` semantics.
		assert_eq!(url("QUJD"), Some(b"ABC".to_vec()));
		// Padding, missing or surplus, behaves the same as the standard pass.
		assert_eq!(url("-_8=="), Some(vec![0xfb, 0xff]));
		assert_eq!(url("-_8==="), Some(vec![0xfb, 0xff]));
	}
}
