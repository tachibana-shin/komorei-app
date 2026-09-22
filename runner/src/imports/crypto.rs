//! `crypto` module — hashes / HMACs computed natively by the runner (md-5,
//! sha1, sha2, hmac crates), so a source can sign request parameters without
//! a wasm→host round trip. All digests come back lowercase hex (the format
//! the VN streaming sites expect). Mirrors aidoku's `imports::crypto` API.

use digest::Digest;
use hmac::{Hmac, Mac};
use md5::Md5;
use sha1::Sha1;
use sha2::Sha256;
use wasmi::{Caller, Linker};

use crate::abi;
use crate::state::RunnerData;

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("crypto", "md5", md5).unwrap();
	linker.func_wrap("crypto", "sha1", sha1).unwrap();
	linker.func_wrap("crypto", "sha256", sha256).unwrap();
	linker.func_wrap("crypto", "hmac_sha1", hmac_sha1).unwrap();
	linker.func_wrap("crypto", "hmac_sha256", hmac_sha256).unwrap();
}

fn md5(mut caller: Caller<'_, RunnerData>, ptr: u32, len: u32) -> i32 {
	let Some(data) = abi::read_bytes(&caller, ptr, len) else {
		return -1;
	};
	store_hex(&mut caller, Md5::digest(&data).as_slice())
}

fn sha1(mut caller: Caller<'_, RunnerData>, ptr: u32, len: u32) -> i32 {
	let Some(data) = abi::read_bytes(&caller, ptr, len) else {
		return -1;
	};
	store_hex(&mut caller, Sha1::digest(&data).as_slice())
}

fn sha256(mut caller: Caller<'_, RunnerData>, ptr: u32, len: u32) -> i32 {
	let Some(data) = abi::read_bytes(&caller, ptr, len) else {
		return -1;
	};
	store_hex(&mut caller, Sha256::digest(&data).as_slice())
}

fn hmac_sha1(mut caller: Caller<'_, RunnerData>, p: u32, l: u32, kp: u32, kl: u32) -> i32 {
	let (Some(data), Some(key)) = (
		abi::read_bytes(&caller, p, l),
		abi::read_bytes(&caller, kp, kl),
	) else {
		return -1;
	};
	let mut mac = match Hmac::<Sha1>::new_from_slice(&key) {
		Ok(mac) => mac,
		Err(_) => return -1,
	};
	mac.update(&data);
	store_hex(&mut caller, mac.finalize().into_bytes().as_slice())
}

fn hmac_sha256(mut caller: Caller<'_, RunnerData>, p: u32, l: u32, kp: u32, kl: u32) -> i32 {
	let (Some(data), Some(key)) = (
		abi::read_bytes(&caller, p, l),
		abi::read_bytes(&caller, kp, kl),
	) else {
		return -1;
	};
	let mut mac = match Hmac::<Sha256>::new_from_slice(&key) {
		Ok(mac) => mac,
		Err(_) => return -1,
	};
	mac.update(&data);
	store_hex(&mut caller, mac.finalize().into_bytes().as_slice())
}

fn store_hex(caller: &mut Caller<'_, RunnerData>, bytes: &[u8]) -> i32 {
	caller.data_mut().store.store_raw(to_hex(bytes).into_bytes())
}

fn to_hex(bytes: &[u8]) -> String {
	use core::fmt::Write;
	let mut out = String::with_capacity(bytes.len() * 2);
	for byte in bytes {
		let _ = write!(out, "{byte:02x}");
	}
	out
}