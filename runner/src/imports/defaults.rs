//! `defaults` module: user defaults, stored on the Kotlin side
//! (SharedPreferences) through `KomoreiHost::defaults_get/set`.

use wasmi::{Caller, Linker};

use crate::abi;
use crate::host::HostDefaultValue;
use crate::state::RunnerData;

const INVALID_KEY: i32 = -1;
const INVALID_VALUE: i32 = -2;
const FAILED_DECODING: i32 = -3;

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("defaults", "get", get).unwrap();
	linker.func_wrap("defaults", "set", set).unwrap();
}

/// Kind byte used by the wasm side (see `DefaultValue::as_byte`).
const KIND_DATA: i32 = 0;
const KIND_BOOL: i32 = 1;
const KIND_INT: i32 = 2;
const KIND_FLOAT: i32 = 3;
const KIND_STRING: i32 = 4;
const KIND_STRING_ARRAY: i32 = 5;
const KIND_NULL: i32 = 6;

fn get(mut caller: Caller<'_, RunnerData>, key_ptr: u32, key_len: u32) -> i32 {
	let Some(key) = abi::read_string(&caller, key_ptr, key_len) else {
		return INVALID_KEY;
	};
	// A host callback that broke the FFI contract yields no value at all. That
	// is NOT the same as a missing key, so it gets its own code: the source
	// sees "could not decode" rather than being told the key is invalid.
	// (This branch used to PANIC inside uniffi, which aborted the whole host
	// process — see the `KomoreiHost` note in host.rs.)
	let value = match caller.data().host.defaults_get(key) {
		Ok(Some(value)) => value,
		Ok(None) => return INVALID_KEY,
		Err(_) => return FAILED_DECODING,
	};
	if matches!(value, HostDefaultValue::Null) {
		return INVALID_VALUE;
	}
	let encoded: Vec<u8> = match value {
		HostDefaultValue::Bool(b) => postcard::to_allocvec(&b).unwrap_or_default(),
		HostDefaultValue::Int(i) => postcard::to_allocvec(&i).unwrap_or_default(),
		HostDefaultValue::Float(f) => postcard::to_allocvec(&f).unwrap_or_default(),
		HostDefaultValue::String(s) => postcard::to_allocvec(&s).unwrap_or_default(),
		HostDefaultValue::StringArray(v) => postcard::to_allocvec(&v).unwrap_or_default(),
		HostDefaultValue::Data(bytes) => bytes,
		HostDefaultValue::Null => return INVALID_VALUE,
	};
	caller.data_mut().store.store_raw(encoded)
}

fn set(
	caller: Caller<'_, RunnerData>,
	key_ptr: u32,
	key_len: u32,
	kind: i32,
	value_ptr: i32,
) -> i32 {
	let Some(key) = abi::read_string(&caller, key_ptr, key_len) else {
		return INVALID_KEY;
	};
	if !(0..=KIND_NULL).contains(&kind) {
		return INVALID_VALUE;
	}
	let value = if kind == KIND_NULL {
		HostDefaultValue::Null
	} else {
		let Some(data) = abi::read_item_bytes(&caller, value_ptr as u32) else {
			return FAILED_DECODING;
		};
		match kind {
			KIND_DATA => HostDefaultValue::Data(data),
			KIND_BOOL => match postcard::from_bytes::<bool>(&data) {
				Ok(v) => HostDefaultValue::Bool(v),
				Err(_) => return FAILED_DECODING,
			},
			KIND_INT => match postcard::from_bytes::<i32>(&data) {
				Ok(v) => HostDefaultValue::Int(v),
				Err(_) => return FAILED_DECODING,
			},
			KIND_FLOAT => match postcard::from_bytes::<f32>(&data) {
				Ok(v) => HostDefaultValue::Float(v),
				Err(_) => return FAILED_DECODING,
			},
			KIND_STRING => match postcard::from_bytes::<String>(&data) {
				Ok(v) => HostDefaultValue::String(v),
				Err(_) => return FAILED_DECODING,
			},
			KIND_STRING_ARRAY => match postcard::from_bytes::<Vec<String>>(&data) {
				Ok(v) => HostDefaultValue::StringArray(v),
				Err(_) => return FAILED_DECODING,
			},
			_ => return INVALID_VALUE,
		}
	};
	// Storing is best-effort from the caller's perspective: a failed host
	// callback leaves the store unchanged, and the source treats this import as
	// fire-and-forget anyway.
	let _ = caller.data().host.defaults_set(key, value);
	0
}
