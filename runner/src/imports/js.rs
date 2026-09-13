//! `js` module: JavaScript contexts and web views.
//!
//! The actual JavaScript engine lives on the Kotlin side — an Android
//! `WebView` with JavaScript enabled (a hidden one per JS context). The runner
//! only relays operations to `KomoreiHost::js_*` methods keyed by opaque
//! handles and stores the resulting handles (or raw-string descriptors) in the
//! rid store, mirroring the `html` module.
//!
//! Value semantics: `*_eval` / `context_get` return a JsValue descriptor
//! (positive); the wasm reads it with `value_to_string` / `value_to_bool` /
//! `value_to_int` / `value_to_f64` / `value_is_*`. Handles live until the wasm
//! drops the descriptor (`std::destroy`) or calls `value_release`.
//!
//! Error codes: -1 InvalidDescriptor, -2 InvalidString, -3 JSError, -4
//! InvalidRequest, -5 InvalidUrl, -6 NoResult.

use std::collections::HashMap;

use wasmi::{Caller, Linker};

use crate::state::{GlobalStore, RunnerData, StoreItem};
use crate::abi;

const INVALID_DESCRIPTOR: i32 = -1;
const INVALID_STRING: i32 = -2;
const JS_ERROR: i32 = -3;
const INVALID_REQUEST: i32 = -4;
const INVALID_URL: i32 = -5;
const NO_RESULT: i32 = -6;

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("js", "context_create", context_create).unwrap();
	linker.func_wrap("js", "context_eval", context_eval).unwrap();
	linker.func_wrap("js", "context_eval_async", context_eval_async).unwrap();
	linker.func_wrap("js", "context_get", context_get).unwrap();
	// JsValue reads (needed by every source that uses `js!` results)
	linker.func_wrap("js", "value_clone", value_clone).unwrap();
	linker.func_wrap("js", "value_release", value_release).unwrap();
	linker.func_wrap("js", "value_to_string", value_to_string).unwrap();
	linker.func_wrap("js", "value_to_bool", value_to_bool).unwrap();
	linker.func_wrap("js", "value_to_int", value_to_int).unwrap();
	linker.func_wrap("js", "value_to_f64", value_to_f64).unwrap();
	linker.func_wrap("js", "value_is_defined", value_is_defined).unwrap();
	linker.func_wrap("js", "value_is_null", value_is_null).unwrap();
	linker.func_wrap("js", "webview_create", webview_create).unwrap();
	linker.func_wrap("js", "webview_set_rule_list", webview_set_rule_list).unwrap();
	linker.func_wrap("js", "webview_load", webview_load).unwrap();
	linker.func_wrap("js", "webview_load_html", webview_load_html).unwrap();
	linker.func_wrap("js", "webview_wait_for_load", webview_wait_for_load).unwrap();
	linker.func_wrap("js", "webview_eval", webview_eval).unwrap();
	linker.func_wrap("js", "webview_eval_async", webview_eval_async).unwrap();
	linker.func_wrap("js", "webview_add_user_script", webview_add_user_script).unwrap();
	linker.func_wrap("js", "webview_get_cookies", webview_get_cookies).unwrap();
	linker.func_wrap("js", "webview_delete_cookie", webview_delete_cookie).unwrap();
}

// -- helpers ---------------------------------------------------------------

fn context_handle(store: &GlobalStore, rid: i32) -> Option<i64> {
	match store.get(rid) {
		Some(StoreItem::JsContext(h)) => Some(*h),
		_ => None,
	}
}

fn value_handle(store: &GlobalStore, rid: i32) -> Option<i64> {
	match store.get(rid) {
		Some(StoreItem::JsValue(h)) => Some(*h),
		_ => None,
	}
}

fn webview_handle(store: &GlobalStore, rid: i32) -> Option<i64> {
	match store.get(rid) {
		Some(StoreItem::JsWebView(h)) => Some(*h),
		_ => None,
	}
}

/// Stores a raw-string descriptor for `read_buffer`.
fn store_string(store: &mut GlobalStore, value: String) -> i32 {
	store.store_raw(value.into_bytes())
}

/// Stores a JsValue descriptor.
fn store_value(store: &mut GlobalStore, handle: i64) -> i32 {
	store.store(StoreItem::JsValue(handle))
}

/// Reads a string arg, distinguishing descriptor errors (-1) from string
/// decode errors (-2). Returns `Err(code)` when either fails.
fn read_string_or(caller: &Caller<'_, RunnerData>, ptr: u32, len: u32) -> Result<String, i32> {
	abi::read_string(caller, ptr, len).ok_or(INVALID_STRING)
}

// -- contexts --------------------------------------------------------------

fn context_create(mut caller: Caller<'_, RunnerData>) -> i32 {
	let handle = caller.data().host.js_context_create();
	if handle <= 0 {
		return JS_ERROR;
	}
	caller.data_mut().store.store(StoreItem::JsContext(handle))
}

fn context_eval(mut caller: Caller<'_, RunnerData>, context: i32, code_ptr: u32, code_len: u32) -> i32 {
	let Some(handle) = context_handle(&caller.data().store, context) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(code) = read_string_or(&caller, code_ptr, code_len) else {
		return INVALID_STRING;
	};
	let value = caller.data().host.js_context_eval(handle, code);
	if value <= 0 {
		return JS_ERROR;
	}
	store_value(&mut caller.data_mut().store, value)
}

/// Fire-and-forget evaluation: the code runs on a worker thread, the result
/// is discarded (the wasm caller cannot receive an async value handle).
fn context_eval_async(caller: Caller<'_, RunnerData>, context: i32, code_ptr: u32, code_len: u32) -> i32 {
	let Some(handle) = context_handle(&caller.data().store, context) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(code) = read_string_or(&caller, code_ptr, code_len) else {
		return INVALID_STRING;
	};
	let host = caller.data().host.clone();
	std::thread::spawn(move || {
		let value = host.js_context_eval(handle, code);
		if value > 0 {
			host.js_value_release(value);
		}
	});
	0
}

fn context_get(mut caller: Caller<'_, RunnerData>, context: i32, key_ptr: u32, key_len: u32) -> i32 {
	let Some(handle) = context_handle(&caller.data().store, context) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(key) = read_string_or(&caller, key_ptr, key_len) else {
		return INVALID_STRING;
	};
	let value = caller.data().host.js_context_get(handle, key);
	if value <= 0 {
		return JS_ERROR;
	}
	store_value(&mut caller.data_mut().store, value)
}

// -- values -----------------------------------------------------------------

fn value_to_string(mut caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return INVALID_DESCRIPTOR;
	};
	match caller.data().host.js_value_to_string(handle) {
		Some(string) => store_string(&mut caller.data_mut().store, string),
		None => NO_RESULT,
	}
}

fn value_to_bool(caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return 0;
	};
	i32::from(caller.data().host.js_value_to_bool(handle))
}

fn value_to_int(caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return 0;
	};
	caller.data().host.js_value_to_int(handle)
}

fn value_to_f64(caller: Caller<'_, RunnerData>, value: i32) -> f64 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return 0.0;
	};
	caller.data().host.js_value_to_f64(handle)
}

fn value_is_defined(caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return 0;
	};
	i32::from(caller.data().host.js_value_is_defined(handle))
}

fn value_is_null(caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return 0;
	};
	i32::from(caller.data().host.js_value_is_null(handle))
}

fn value_clone(mut caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return INVALID_DESCRIPTOR;
	};
	let clone = caller.data().host.js_value_clone(handle);
	if clone <= 0 {
		return JS_ERROR;
	}
	store_value(&mut caller.data_mut().store, clone)
}

fn value_release(mut caller: Caller<'_, RunnerData>, value: i32) -> i32 {
	let Some(handle) = value_handle(&caller.data().store, value) else {
		return INVALID_DESCRIPTOR;
	};
	caller.data_mut().store.remove(value);
	caller.data().host.js_value_release(handle);
	0
}

// -- webviews --------------------------------------------------------------

fn webview_create(mut caller: Caller<'_, RunnerData>) -> i32 {
	let handle = caller.data().host.js_webview_create();
	if handle <= 0 {
		return JS_ERROR;
	}
	caller.data_mut().store.store(StoreItem::JsWebView(handle))
}

fn webview_set_rule_list(caller: Caller<'_, RunnerData>, webview: i32, rules_ptr: u32, rules_len: u32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(rules) = read_string_or(&caller, rules_ptr, rules_len) else {
		return INVALID_STRING;
	};
	caller.data().host.js_webview_set_rule_list(handle, rules);
	0
}

fn webview_load(caller: Caller<'_, RunnerData>, webview: i32, request: i32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let (url, headers) = match caller.data().store.get(request) {
		Some(StoreItem::Request(r)) => (r.url.clone(), r.headers.clone()),
		_ => return INVALID_REQUEST,
	};
	let Some(url) = url else {
		return INVALID_URL;
	};
	caller.data().host.js_webview_load_url(handle, url, headers);
	0
}

fn webview_load_html(caller: Caller<'_, RunnerData>, webview: i32, html_ptr: u32, html_len: u32, url_ptr: u32, url_len: u32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let (Ok(html), Ok(base_url)) = (
		read_string_or(&caller, html_ptr, html_len),
		read_string_or(&caller, url_ptr, url_len),
	) else {
		return INVALID_STRING;
	};
	caller.data().host.js_webview_load_html(handle, html, base_url);
	0
}

fn webview_wait_for_load(caller: Caller<'_, RunnerData>, webview: i32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	caller.data().host.js_webview_wait_for_load(handle);
	0
}

fn webview_eval(mut caller: Caller<'_, RunnerData>, webview: i32, code_ptr: u32, code_len: u32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(code) = read_string_or(&caller, code_ptr, code_len) else {
		return INVALID_STRING;
	};
	let value = caller.data().host.js_webview_eval(handle, code);
	if value <= 0 {
		return JS_ERROR;
	}
	store_value(&mut caller.data_mut().store, value)
}

/// Fire-and-forget evaluation on a worker thread; result discarded.
fn webview_eval_async(caller: Caller<'_, RunnerData>, webview: i32, code_ptr: u32, code_len: u32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(code) = read_string_or(&caller, code_ptr, code_len) else {
		return INVALID_STRING;
	};
	let host = caller.data().host.clone();
	std::thread::spawn(move || {
		let value = host.js_webview_eval(handle, code);
		if value > 0 {
			host.js_value_release(value);
		}
	});
	0
}

fn webview_add_user_script(
	caller: Caller<'_, RunnerData>,
	webview: i32,
	code_ptr: u32,
	code_len: u32,
	at_document_end: i32,
	for_main_frame_only: i32,
) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let Ok(code) = read_string_or(&caller, code_ptr, code_len) else {
		return INVALID_STRING;
	};
	caller.data().host.js_webview_add_user_script(handle, code, at_document_end != 0, for_main_frame_only != 0);
	0
}

fn webview_get_cookies(mut caller: Caller<'_, RunnerData>, webview: i32) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let cookies: HashMap<String, String> = caller.data().host.js_webview_get_cookies(handle);
	let header = cookies
		.iter()
		.map(|(name, value)| format!("{name}={value}"))
		.collect::<Vec<_>>()
		.join("; ");
	store_string(&mut caller.data_mut().store, header)
}

#[allow(clippy::too_many_arguments)]
fn webview_delete_cookie(
	caller: Caller<'_, RunnerData>,
	webview: i32,
	name_ptr: u32,
	name_len: u32,
	value_ptr: u32,
	value_len: u32,
	domain_ptr: u32,
	domain_len: u32,
) -> i32 {
	let Some(handle) = webview_handle(&caller.data().store, webview) else {
		return INVALID_DESCRIPTOR;
	};
	let (Ok(name), Ok(value), Ok(domain)) = (
		read_string_or(&caller, name_ptr, name_len),
		read_string_or(&caller, value_ptr, value_len),
		read_string_or(&caller, domain_ptr, domain_len),
	) else {
		return INVALID_STRING;
	};
	caller.data().host.js_webview_delete_cookie(handle, name, value, domain);
	0
}