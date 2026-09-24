//! `std` module: rid destroy, buffer reads, dates.

use wasmi::{Caller, Linker};

use crate::abi;
use crate::state::{RunnerData, StoreItem};

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("std", "destroy", destroy).unwrap();
	linker.func_wrap("std", "buffer_len", buffer_len).unwrap();
	linker.func_wrap("std", "read_buffer", read_buffer).unwrap();
	linker
		.func_wrap("std", "current_date", current_date)
		.unwrap();
	linker.func_wrap("std", "utc_offset", utc_offset).unwrap();
	linker.func_wrap("std", "parse_date", parse_date).unwrap();
}

/// What the host must drop when a `std::destroy` lands for a handle item.
enum DestroyTarget {
	/// An HTML DOM object (Jsoup) — forward to `html_destroy`.
	Html(i64),
	/// A JS value — forward to `js_value_release`.
	JsValue(i64),
}

fn destroy(mut caller: Caller<'_, RunnerData>, rid: i32) {
	let to_destroy = match caller.data_mut().store.get(rid) {
		Some(StoreItem::Html(handle)) => Some(DestroyTarget::Html(*handle)),
		Some(StoreItem::JsValue(handle)) => Some(DestroyTarget::JsValue(*handle)),
		_ => None,
	};
	caller.data_mut().store.remove(rid);
	match to_destroy {
		// Releasing a host reference is best-effort — the descriptor is already
		// gone from the store, so a failed callback must not propagate.
		Some(DestroyTarget::Html(handle)) => {
			let _ = caller.data().host.html_destroy(handle);
		}
		Some(DestroyTarget::JsValue(handle)) => {
			let _ = caller.data().host.js_value_release(handle);
		}
		None => {}
	}
}

fn buffer_len(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	match caller.data().store.item_bytes(rid) {
		Some(data) => data.len() as i32,
		None => -1, // InvalidDescriptor
	}
}

fn read_buffer(mut caller: Caller<'_, RunnerData>, rid: i32, ptr: u32, size: u32) -> i32 {
	let data = match caller.data().store.item_bytes(rid) {
		Some(data) => data.to_vec(),
		None => return -1, // InvalidDescriptor
	};
	if size as usize <= data.len() {
		let chunk = data.into_iter().take(size as usize).collect::<Vec<_>>();
		if abi::write_bytes(&mut caller, ptr, &chunk) {
			0
		} else {
			-3 // FailedMemoryWrite
		}
	} else {
		-3 // FailedMemoryWrite (buffer too small)
	}
}

/// Current unix timestamp as seconds (f64).
fn current_date(caller: Caller<'_, RunnerData>) -> f64 {
	// The wasm ABI has a single f64 out-param and no error code here; a host
	// that cannot answer reports 0 (the unix epoch) rather than panicking.
	caller.data().host.current_date().unwrap_or(0.0)
}

/// UTC offset in seconds (west negative).
fn utc_offset(caller: Caller<'_, RunnerData>) -> i64 {
	caller.data().host.utc_offset().unwrap_or(0)
}

fn parse_date(
	caller: Caller<'_, RunnerData>,
	date_ptr: u32,
	date_len: u32,
	format_ptr: u32,
	format_len: u32,
	locale_ptr: u32,
	locale_len: u32,
	timezone_ptr: u32,
	timezone_len: u32,
) -> f64 {
	let date = abi::read_string(&caller, date_ptr, date_len);
	let format = abi::read_string(&caller, format_ptr, format_len);
	let (Some(date), Some(format)) = (date, format) else {
		return -4.0; // InvalidString
	};
	let locale = if locale_len > 0 {
		abi::read_string(&caller, locale_ptr, locale_len)
	} else {
		None
	};
	let timezone = if timezone_len > 0 {
		abi::read_string(&caller, timezone_ptr, timezone_len)
	} else {
		None
	};
	// -1.0 is the module's "parse failed" signal; reuse it when the host itself
	// cannot answer (the ABI has no dedicated code for that).
	caller
		.data()
		.host
		.parse_date(date, format, locale, timezone)
		.unwrap_or(-1.0)
}
