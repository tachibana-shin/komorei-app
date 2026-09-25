//! Helpers for reading from / writing to the wasm linear memory through a
//! `Caller` inside host function implementations.

use wasmi::{Caller, Memory};

use crate::state::RunnerData;

/// The wasm `memory` export of the running instance.
pub fn memory(caller: &Caller<'_, RunnerData>) -> Option<Memory> {
	caller.get_export("memory").and_then(|e| e.into_memory())
}

pub fn read_bytes(caller: &Caller<'_, RunnerData>, ptr: u32, len: u32) -> Option<Vec<u8>> {
	let mem = memory(caller)?;
	let mut buf = vec![0u8; len as usize];
	mem.read(caller, ptr as usize, &mut buf).ok()?;
	Some(buf)
}

pub fn read_string(caller: &Caller<'_, RunnerData>, ptr: u32, len: u32) -> Option<String> {
	let bytes = read_bytes(caller, ptr, len)?;
	String::from_utf8(bytes).ok()
}

pub fn read_u32(caller: &Caller<'_, RunnerData>, ptr: u32) -> Option<u32> {
	let bytes = read_bytes(caller, ptr, 4)?;
	Some(u32::from_le_bytes(bytes.try_into().ok()?))
}

pub fn read_values(caller: &Caller<'_, RunnerData>, ptr: u32, count: u32) -> Option<Vec<i32>> {
	let bytes = read_bytes(caller, ptr, count.checked_mul(4)?)?;
	Some(
		bytes
			.as_chunks::<4>()
			.0
			.iter()
			.map(|c| i32::from_le_bytes([c[0], c[1], c[2], c[3]]))
			.collect(),
	)
}

/// Reads an item descriptor: 4-byte length, then `len - 8` bytes of payload.
/// This is the layout the wasm side uses for `encode(...)` buffers.
pub fn read_item_bytes(caller: &Caller<'_, RunnerData>, ptr: u32) -> Option<Vec<u8>> {
	let len = read_u32(caller, ptr)? as usize;
	if len <= 8 {
		return None;
	}
	read_bytes(caller, ptr + 8, (len - 8) as u32)
}

pub fn write_bytes(caller: &mut Caller<'_, RunnerData>, ptr: u32, data: &[u8]) -> bool {
	let Some(mem) = memory(caller) else {
		return false;
	};
	mem.write(caller, ptr as usize, data).is_ok()
}

pub fn write_values(caller: &mut Caller<'_, RunnerData>, ptr: u32, values: &[i32]) -> bool {
	let bytes: Vec<u8> = values.iter().flat_map(|v| v.to_le_bytes()).collect();
	write_bytes(caller, ptr, &bytes)
}
