//! `base64` module — encode/decode run natively in the runner (no host round
//! trip), so sources that decode base64 blobs (grab playlists, embedded
//! configs) don't carry a decoder inside their wasm.

use base64::engine::general_purpose::{STANDARD, STANDARD_NO_PAD, URL_SAFE, URL_SAFE_NO_PAD};
use base64::Engine;
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

/// Tolerant decode: standard padded/unpadded and URL-safe padded/unpadded
/// (grab pages ship all four variants in the wild).
fn decode(mut caller: Caller<'_, RunnerData>, ptr: u32, len: u32) -> i32 {
	let Some(data) = abi::read_string(&caller, ptr, len) else {
		return -1;
	};
	let decoded = STANDARD
		.decode(&data)
		.or_else(|_| STANDARD_NO_PAD.decode(&data))
		.or_else(|_| URL_SAFE.decode(&data))
		.or_else(|_| URL_SAFE_NO_PAD.decode(&data));
	match decoded {
		Ok(bytes) => caller.data_mut().store.store_raw(bytes),
		Err(_) => -1,
	}
}