//! `net` module: HTTP request lifecycle. The actual request is performed by
//! the Kotlin host (OkHttp) through `KomoreiHost::net_request`.

use std::collections::HashMap;

use wasmi::{Caller, Linker};

use crate::host::HostHttpMethod;
use crate::state::{RunnerData, StoreItem};
use crate::abi;

const INVALID_DESCRIPTOR: i32 = -1;
const INVALID_METHOD: i32 = -3;
const INVALID_URL: i32 = -4;
const INVALID_HTML: i32 = -5;
const MISSING_DATA: i32 = -7;
const MISSING_RESPONSE: i32 = -8;
const REQUEST_ERROR: i32 = -10;
const FAILED_MEMORY_WRITE: i32 = -11;
const NOT_AN_IMAGE: i32 = -12;

/// A request (rid item) tracked by the runner. Mutable until `send` runs,
/// after which it also holds the response until the wasm destroys the rid.
pub struct RequestState {
	pub method: i32,
	pub url: Option<String>,
	pub headers: HashMap<String, String>,
	pub body: Option<Vec<u8>>,
	pub timeout: Option<f64>,
	pub response: Option<ResponseState>,
}

pub struct ResponseState {
	pub status: i32,
	pub url: String,
	pub headers: HashMap<String, String>,
	pub data: Vec<u8>,
}

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("net", "init", init).unwrap();
	linker.func_wrap("net", "send", send).unwrap();
	linker.func_wrap("net", "send_all", send_all).unwrap();
	linker.func_wrap("net", "set_url", set_url).unwrap();
	linker.func_wrap("net", "set_header", set_header).unwrap();
	linker.func_wrap("net", "set_body", set_body).unwrap();
	linker.func_wrap("net", "set_timeout", set_timeout).unwrap();
	linker.func_wrap("net", "data_len", data_len).unwrap();
	linker.func_wrap("net", "read_data", read_data).unwrap();
	linker.func_wrap("net", "get_image", get_image).unwrap();
	linker.func_wrap("net", "get_header", get_header).unwrap();
	linker.func_wrap("net", "get_status_code", get_status_code).unwrap();
	linker.func_wrap("net", "get_url", get_url).unwrap();
	linker.func_wrap("net", "html", html).unwrap();
	linker.func_wrap("net", "set_rate_limit", set_rate_limit).unwrap();
}

fn request_mut<'a>(caller: &'a mut Caller<'_, RunnerData>, rid: i32) -> Option<&'a mut RequestState> {
	match caller.data_mut().store.get_mut(rid) {
		Some(StoreItem::Request(r)) => Some(r),
		_ => None,
	}
}

fn init(mut caller: Caller<'_, RunnerData>, method: i32) -> i32 {
	let Some(method) = HostHttpMethod::from_index(method) else {
		return INVALID_METHOD;
	};
	let method = method as i32;
	caller
		.data_mut()
		.store
		.store(StoreItem::Request(RequestState {
			method,
			url: None,
			headers: HashMap::new(),
			body: None,
			timeout: None,
			response: None,
		}))
}

fn common_send(caller: &mut Caller<'_, RunnerData>, rid: i32) -> i32 {
	let (method, url, headers, body, timeout) = {
		let Some(request) = request_mut(caller, rid) else {
			return INVALID_DESCRIPTOR;
		};
		let Some(url) = request.url.clone() else {
			return INVALID_URL;
		};
		(method_from_index(request.method), url, request.headers.clone(), request.body.clone().unwrap_or_default(), request.timeout)
	};
	let response = caller.data().host.net_request(method, url, headers, body, timeout);
	if !response.ok {
		return REQUEST_ERROR;
	}
	let state = ResponseState {
		status: response.status,
		url: response.url,
		headers: response.headers,
		data: response.data,
	};
	match request_mut(caller, rid) {
		Some(request) => {
			request.response = Some(state);
			0
		}
		None => INVALID_DESCRIPTOR,
	}
}

fn method_from_index(index: i32) -> HostHttpMethod {
	HostHttpMethod::from_index(index).unwrap_or(HostHttpMethod::Get)
}

fn send(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	common_send(&mut caller, rid)
}

fn send_all(mut caller: Caller<'_, RunnerData>, rid_ptr: u32, len: u32) -> i32 {
	let Some(rids) = abi::read_values(&caller, rid_ptr, len) else {
		return INVALID_DESCRIPTOR;
	};
	let mut errors = Vec::with_capacity(rids.len());
	let mut was_error = false;
	for rid in &rids {
		let result = common_send(&mut caller, *rid);
		if result != 0 {
			was_error = true;
		}
		errors.push(result);
	}
	if !abi::write_values(&mut caller, rid_ptr, &errors) {
		FAILED_MEMORY_WRITE
	} else if was_error {
		REQUEST_ERROR
	} else {
		0
	}
}

fn set_url(mut caller: Caller<'_, RunnerData>, rid: i32, ptr: u32, len: u32) -> i32 {
	let value = abi::read_string(&caller, ptr, len).unwrap_or_default();
	match request_mut(&mut caller, rid) {
		Some(request) => {
			request.url = Some(value);
			0
		}
		None => INVALID_DESCRIPTOR,
	}
}

fn set_header(
	mut caller: Caller<'_, RunnerData>,
	rid: i32,
	key_ptr: u32,
	key_len: u32,
	val_ptr: u32,
	val_len: u32,
) -> i32 {
	let key = abi::read_string(&caller, key_ptr, key_len).unwrap_or_default();
	let value = abi::read_string(&caller, val_ptr, val_len).unwrap_or_default();
	match request_mut(&mut caller, rid) {
		Some(request) => {
			request.headers.insert(key, value);
			0
		}
		None => INVALID_DESCRIPTOR,
	}
}

fn set_body(mut caller: Caller<'_, RunnerData>, rid: i32, ptr: u32, len: u32) -> i32 {
	let body = abi::read_bytes(&caller, ptr, len).unwrap_or_default();
	match request_mut(&mut caller, rid) {
		Some(request) => {
			request.body = Some(body);
			0
		}
		None => INVALID_DESCRIPTOR,
	}
}

fn set_timeout(mut caller: Caller<'_, RunnerData>, rid: i32, value: f64) -> i32 {
	match request_mut(&mut caller, rid) {
		Some(request) => {
			request.timeout = Some(value);
			0
		}
		None => INVALID_DESCRIPTOR,
	}
}

fn data_len(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(request) = request_mut(&mut caller, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match &request.response {
		Some(response) => response.data.len() as i32,
		None => MISSING_RESPONSE,
	}
}

fn read_data(mut caller: Caller<'_, RunnerData>, rid: i32, buffer: u32, size: u32) -> i32 {
	let data = {
		let Some(request) = request_mut(&mut caller, rid) else {
			return INVALID_DESCRIPTOR;
		};
		let Some(response) = &request.response else {
			return MISSING_RESPONSE;
		};
		response.data.clone()
	};
	if size as usize <= data.len() {
		let chunk = data.into_iter().take(size as usize).collect::<Vec<_>>();
		if abi::write_bytes(&mut caller, buffer, &chunk) {
			0
		} else {
			FAILED_MEMORY_WRITE
		}
	} else {
		FAILED_MEMORY_WRITE
	}
}

/// Image decoding is deferred (canvas / cover-image milestone); always report
/// NotAnImage so sources fall back gracefully.
fn get_image(_caller: Caller<'_, RunnerData>, _rid: i32) -> i32 {
	NOT_AN_IMAGE
}

fn get_status_code(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(request) = request_mut(&mut caller, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match &request.response {
		Some(response) => response.status,
		None => MISSING_RESPONSE,
	}
}

fn get_url(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let url = {
		let Some(request) = request_mut(&mut caller, rid) else {
			return INVALID_DESCRIPTOR;
		};
		let Some(response) = &request.response else {
			return MISSING_RESPONSE;
		};
		response.url.clone()
	};
	caller.data_mut().store.store_raw(url.into_bytes())
}

fn get_header(mut caller: Caller<'_, RunnerData>, rid: i32, key_ptr: u32, key_len: u32) -> i32 {
	let key = abi::read_string(&caller, key_ptr, key_len).unwrap_or_default();
	let value = {
		let Some(request) = request_mut(&mut caller, rid) else {
			return INVALID_DESCRIPTOR;
		};
		let Some(response) = &request.response else {
			return MISSING_RESPONSE;
		};
		response.headers.get(&key).cloned()
	};
	match value {
		Some(value) if !value.is_empty() => {
			caller.data_mut().store.store_raw(value.into_bytes())
		}
		_ => MISSING_DATA,
	}
}

fn html(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let (text, base_url) = {
		let Some(request) = request_mut(&mut caller, rid) else {
			return INVALID_DESCRIPTOR;
		};
		let Some(response) = &request.response else {
			return MISSING_RESPONSE;
		};
		let text = match std::str::from_utf8(&response.data) {
			Ok(text) => text.to_string(),
			Err(_) => return INVALID_HTML,
		};
		let base_url = response.url.clone();
		(text, base_url)
	};
	let handle = caller.data().host.html_parse(text, base_url);
	if handle <= 0 {
		return INVALID_HTML;
	}
	caller.data_mut().store.store(StoreItem::Html(handle))
}

fn set_rate_limit(_caller: Caller<'_, RunnerData>, _permits: i32, _period: i32, _unit: i32) {
	// rate limiting is left for a later milestone
}