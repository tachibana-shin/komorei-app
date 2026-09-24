//! `html` module: the entire DOM lives on the Kotlin side (Jsoup). The runner
//! only relays operations to `KomoreiHost::html_*` methods keyed by opaque
//! handles and stores the resulting handles (or raw-string descriptors) in the
//! rid store.
//!
//! Error codes mirror the lib's `HtmlError`: -1 InvalidDescriptor, -2
//! InvalidString, -3 InvalidHtml, -4 InvalidQuery, -5 NoResult.

use wasmi::{Caller, Linker};

use crate::error::RunnerError;
use crate::state::{GlobalStore, RunnerData, StoreItem};
use crate::{abi, host::KomoreiHost};

const INVALID_DESCRIPTOR: i32 = -1;
const INVALID_STRING: i32 = -2;
const INVALID_HTML: i32 = -3;
const INVALID_QUERY: i32 = -4;
const NO_RESULT: i32 = -5;

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("html", "parse", parse).unwrap();
	linker
		.func_wrap("html", "parse_fragment", parse_fragment)
		.unwrap();
	linker.func_wrap("html", "escape", escape).unwrap();
	linker.func_wrap("html", "unescape", unescape).unwrap();
	linker.func_wrap("html", "kind", kind).unwrap();
	linker
		.func_wrap("html", "child_nodes", child_nodes)
		.unwrap();
	linker.func_wrap("html", "has_attr", has_attr).unwrap();
	linker.func_wrap("html", "set_attr", set_attr).unwrap();
	linker
		.func_wrap("html", "remove_attr", remove_attr)
		.unwrap();
	linker.func_wrap("html", "set_text", set_text).unwrap();
	linker.func_wrap("html", "set_html", set_html).unwrap();
	linker.func_wrap("html", "prepend", prepend).unwrap();
	linker.func_wrap("html", "append", append).unwrap();
	linker.func_wrap("html", "children", children).unwrap();
	linker.func_wrap("html", "base_uri", base_uri).unwrap();
	linker.func_wrap("html", "own_text", own_text).unwrap();
	linker.func_wrap("html", "data", data).unwrap();
	linker.func_wrap("html", "id", id).unwrap();
	linker.func_wrap("html", "tag_name", tag_name).unwrap();
	linker.func_wrap("html", "class_name", class_name).unwrap();
	linker.func_wrap("html", "has_class", has_class).unwrap();
	linker.func_wrap("html", "add_class", add_class).unwrap();
	linker
		.func_wrap("html", "remove_class", remove_class)
		.unwrap();
	linker.func_wrap("html", "first", first).unwrap();
	linker.func_wrap("html", "last", last).unwrap();
	linker.func_wrap("html", "get", html_get).unwrap();
	linker.func_wrap("html", "size", size).unwrap();
	linker.func_wrap("html", "parent", parent).unwrap();
	linker.func_wrap("html", "siblings", siblings).unwrap();
	linker.func_wrap("html", "next", next).unwrap();
	linker.func_wrap("html", "previous", previous).unwrap();
	linker.func_wrap("html", "attr", attr).unwrap();
	linker.func_wrap("html", "outer_html", outer_html).unwrap();
	linker.func_wrap("html", "remove", remove).unwrap();
	linker.func_wrap("html", "select", select).unwrap();
	linker
		.func_wrap("html", "select_first", select_first)
		.unwrap();
	linker.func_wrap("html", "text", text).unwrap();
	linker
		.func_wrap("html", "untrimmed_text", untrimmed_text)
		.unwrap();
	linker.func_wrap("html", "html", html).unwrap();
}

// -- helpers ---------------------------------------------------------------

/// Folds a failed host call into the same "nothing to report" branch the
/// `None` arm already handles.
///
/// A `KomoreiHost` that breaks the FFI contract surfaces here as
/// `Err(RunnerError::HostCallback)` instead of panicking inside uniffi and
/// aborting the process (see the note on `KomoreiHost` in host.rs). The wasm
/// ABI has no code for "the host itself is broken", so the call degrades to
/// whatever the source already handles as "no value".
fn host_opt<T>(result: Result<Option<T>, RunnerError>) -> Option<T> {
	result.unwrap_or(None)
}

fn handle(store: &GlobalStore, rid: i32) -> Option<i64> {
	match store.get(rid) {
		Some(StoreItem::Html(h)) => Some(*h),
		_ => None,
	}
}

/// Stores a raw-string descriptor for `read_string_and_destroy`.
fn store_string(store: &mut GlobalStore, value: String) -> i32 {
	store.store_raw(value.into_bytes())
}

/// Stores a DOM handle descriptor.
fn store_handle(store: &mut GlobalStore, value: i64) -> i32 {
	store.store(StoreItem::Html(value))
}

// -- parse / text transforms -----------------------------------------------

fn parse(
	mut caller: Caller<'_, RunnerData>,
	html_ptr: u32,
	html_len: u32,
	base_ptr: u32,
	base_len: u32,
) -> i32 {
	let html = abi::read_string(&caller, html_ptr, html_len);
	let base_url = if base_len > 0 {
		abi::read_string(&caller, base_ptr, base_len)
	} else {
		Some(String::new())
	};
	let (Some(html), Some(base_url)) = (html, base_url) else {
		return INVALID_STRING;
	};
	let Ok(handle) = caller.data().host.html_parse(html, base_url) else {
		return INVALID_HTML;
	};
	if handle <= 0 {
		return INVALID_HTML;
	}
	caller.data_mut().store.store(StoreItem::Html(handle))
}

fn parse_fragment(
	mut caller: Caller<'_, RunnerData>,
	html_ptr: u32,
	html_len: u32,
	base_ptr: u32,
	base_len: u32,
) -> i32 {
	let html = abi::read_string(&caller, html_ptr, html_len);
	let base_url = if base_len > 0 {
		abi::read_string(&caller, base_ptr, base_len)
	} else {
		Some(String::new())
	};
	let (Some(html), Some(base_url)) = (html, base_url) else {
		return INVALID_STRING;
	};
	let Ok(handle) = caller.data().host.html_parse_fragment(html, base_url) else {
		return INVALID_HTML;
	};
	if handle <= 0 {
		return INVALID_HTML;
	}
	caller.data_mut().store.store(StoreItem::Html(handle))
}

fn escape(mut caller: Caller<'_, RunnerData>, text_ptr: u32, text_len: u32) -> i32 {
	let Some(text) = abi::read_string(&caller, text_ptr, text_len) else {
		return INVALID_STRING;
	};
	match host_opt(caller.data().host.html_escape(text)) {
		Some(escaped) => store_string(&mut caller.data_mut().store, escaped),
		None => INVALID_STRING,
	}
}

fn unescape(mut caller: Caller<'_, RunnerData>, text_ptr: u32, text_len: u32) -> i32 {
	let Some(text) = abi::read_string(&caller, text_ptr, text_len) else {
		return INVALID_STRING;
	};
	match host_opt(caller.data().host.html_unescape(text)) {
		Some(unescaped) => store_string(&mut caller.data_mut().store, unescaped),
		None => INVALID_STRING,
	}
}

// -- single node / kind ----------------------------------------------------

fn kind(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	caller
		.data()
		.host
		.html_kind(handle)
		.unwrap_or(INVALID_DESCRIPTOR)
}

fn child_nodes(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_child_nodes(handle)) {
		Some(child) => store_handle(&mut caller.data_mut().store, child),
		None => NO_RESULT,
	}
}

// -- attributes ------------------------------------------------------------

fn has_attr(caller: Caller<'_, RunnerData>, rid: i32, attr_ptr: u32, attr_len: u32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return 0;
	};
	let Some(key) = abi::read_string(&caller, attr_ptr, attr_len) else {
		return 0;
	};
	i32::from(
		caller
			.data()
			.host
			.html_has_attr(handle, key)
			.unwrap_or(false),
	)
}

fn set_attr(
	caller: Caller<'_, RunnerData>,
	rid: i32,
	key_ptr: u32,
	key_len: u32,
	value_ptr: u32,
	value_len: u32,
) -> i32 {
	let (Some(handle), Some(key), Some(value)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, key_ptr, key_len),
		abi::read_string(&caller, value_ptr, value_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_set_attr(handle, key, value)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn remove_attr(caller: Caller<'_, RunnerData>, rid: i32, attr_ptr: u32, attr_len: u32) -> i32 {
	let (Some(handle), Some(key)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, attr_ptr, attr_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_remove_attr(handle, key)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn add_class(caller: Caller<'_, RunnerData>, rid: i32, class_ptr: u32, class_len: u32) -> i32 {
	let (Some(handle), Some(class)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, class_ptr, class_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_add_class(handle, class)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn remove_class(caller: Caller<'_, RunnerData>, rid: i32, class_ptr: u32, class_len: u32) -> i32 {
	let (Some(handle), Some(class)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, class_ptr, class_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_remove_class(handle, class)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn has_class(caller: Caller<'_, RunnerData>, rid: i32, class_ptr: u32, class_len: u32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return 0;
	};
	let Some(class) = abi::read_string(&caller, class_ptr, class_len) else {
		return 0;
	};
	i32::from(
		caller
			.data()
			.host
			.html_has_class(handle, class)
			.unwrap_or(false),
	)
}

// -- text / html -----------------------------------------------------------

fn text(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_text(h))
}

fn own_text(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_own_text(h))
}

fn untrimmed_text(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_untrimmed_text(h))
}

fn html(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_html(h))
}

fn outer_html(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_outer_html(h))
}

fn id(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_id(h))
}

fn tag_name(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_tag_name(h))
}

fn class_name(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_class_name(h))
}

fn base_uri(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_base_uri(h))
}

fn data(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	string_rid(caller, rid, |host, h| host.html_data(h))
}

fn string_rid(
	mut caller: Caller<'_, RunnerData>,
	rid: i32,
	f: impl Fn(&dyn KomoreiHost, i64) -> Result<Option<String>, RunnerError>,
) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(f(caller.data().host.as_ref(), handle)) {
		Some(value) => store_string(&mut caller.data_mut().store, value),
		None => NO_RESULT,
	}
}

fn set_text(caller: Caller<'_, RunnerData>, rid: i32, text_ptr: u32, text_len: u32) -> i32 {
	let (Some(handle), Some(text)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, text_ptr, text_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_set_text(handle, text)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn set_html(caller: Caller<'_, RunnerData>, rid: i32, html_ptr: u32, html_len: u32) -> i32 {
	let (Some(handle), Some(html_value)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, html_ptr, html_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_set_html(handle, html_value)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn prepend(caller: Caller<'_, RunnerData>, rid: i32, html_ptr: u32, html_len: u32) -> i32 {
	let (Some(handle), Some(html_value)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, html_ptr, html_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_prepend(handle, html_value)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn append(caller: Caller<'_, RunnerData>, rid: i32, html_ptr: u32, html_len: u32) -> i32 {
	let (Some(handle), Some(html_value)) = (
		handle(&caller.data().store, rid),
		abi::read_string(&caller, html_ptr, html_len),
	) else {
		return INVALID_DESCRIPTOR;
	};
	if caller
		.data()
		.host
		.html_append(handle, html_value)
		.unwrap_or(false)
	{
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

fn remove(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	if caller.data().host.html_remove(handle).unwrap_or(false) {
		0
	} else {
		INVALID_DESCRIPTOR
	}
}

// -- selection -------------------------------------------------------------

fn select(mut caller: Caller<'_, RunnerData>, rid: i32, query_ptr: u32, query_len: u32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	let Some(query) = abi::read_string(&caller, query_ptr, query_len) else {
		return INVALID_STRING;
	};
	match host_opt(caller.data().host.html_select(handle, query)) {
		Some(list) => store_handle(&mut caller.data_mut().store, list),
		None => INVALID_QUERY,
	}
}

fn select_first(
	mut caller: Caller<'_, RunnerData>,
	rid: i32,
	query_ptr: u32,
	query_len: u32,
) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	let Some(query) = abi::read_string(&caller, query_ptr, query_len) else {
		return INVALID_STRING;
	};
	match host_opt(caller.data().host.html_select_first(handle, query)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => NO_RESULT,
	}
}

fn first(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_first(handle)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => INVALID_DESCRIPTOR,
	}
}

fn last(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_last(handle)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => INVALID_DESCRIPTOR,
	}
}

fn html_get(mut caller: Caller<'_, RunnerData>, rid: i32, index: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_get(handle, index as i64)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => INVALID_DESCRIPTOR,
	}
}

fn size(caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	caller
		.data()
		.host
		.html_size(handle)
		.unwrap_or(INVALID_DESCRIPTOR)
}

fn parent(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_parent(handle)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => NO_RESULT,
	}
}

fn siblings(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_siblings(handle)) {
		Some(list) => store_handle(&mut caller.data_mut().store, list),
		None => NO_RESULT,
	}
}

fn next(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_next(handle)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => NO_RESULT,
	}
}

fn previous(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_previous(handle)) {
		Some(element) => store_handle(&mut caller.data_mut().store, element),
		None => NO_RESULT,
	}
}

fn children(mut caller: Caller<'_, RunnerData>, rid: i32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	match host_opt(caller.data().host.html_children(handle)) {
		Some(list) => store_handle(&mut caller.data_mut().store, list),
		None => NO_RESULT,
	}
}

fn attr(mut caller: Caller<'_, RunnerData>, rid: i32, key_ptr: u32, key_len: u32) -> i32 {
	let Some(handle) = handle(&caller.data().store, rid) else {
		return INVALID_DESCRIPTOR;
	};
	let Some(key) = abi::read_string(&caller, key_ptr, key_len) else {
		return INVALID_STRING;
	};
	match host_opt(caller.data().host.html_attr(handle, key)) {
		Some(value) => store_string(&mut caller.data_mut().store, value),
		None => NO_RESULT,
	}
}
