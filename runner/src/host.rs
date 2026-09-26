//! The host trait implemented by the Kotlin app.
//!
//! The Rust runner stays thin: it only loads the `.krx` wasm, instantiates it
//! with wasmi and calls its exports. *All* real IO — logs, network (OkHttp),
//! user defaults (SharedPreferences) and the HTML DOM (Jsoup) — is delegated to
//! the Kotlin side through this trait. The runner holds an `Arc<dyn KomoreiHost>`
//! and every wasm import forwards to it.
//!
//! The DOM mirrors aidoku semantics: every parsed document / node / node-list /
//! element-list is an opaque `i64` handle owned by the Kotlin side (Jsoup
//! objects). The runner only stores which handle a rid (descriptor) refers to
//! and relays operations. `html_destroy` is called when the wasm drops the
//! object (`std::destroy(rid)`), letting Jsoup release the object.

use std::collections::HashMap;

use crate::{error::RunnerError, records::HomePartialResult};

/// HTTP methods in the same numeric order the wasm side uses (`net::HttpMethod`).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum HostHttpMethod {
	Get,
	Post,
	Put,
	Head,
	Delete,
	Patch,
	Options,
	Connect,
	Trace,
}

impl HostHttpMethod {
	pub fn from_index(value: i32) -> Option<Self> {
		use HostHttpMethod::*;
		match value {
			0 => Some(Get),
			1 => Some(Post),
			2 => Some(Put),
			3 => Some(Head),
			4 => Some(Delete),
			5 => Some(Patch),
			6 => Some(Options),
			7 => Some(Connect),
			8 => Some(Trace),
			_ => None,
		}
	}
}

/// The result of a network request performed by the host.
///
/// `ok == false` means the request failed to *start* (no network, DNS failure,
/// ...) regardless of the HTTP status code — the runner then reports
/// RequestError (-10) to the source. Successful HTTP responses (even 4xx/5xx)
/// set `ok = true` and carry their status in `status`.
#[derive(Debug, Clone, uniffi::Record)]
pub struct HostNetResponse {
	pub ok: bool,
	pub status: i32,
	/// Final URL after redirects.
	pub url: String,
	/// Response headers. Values for duplicated header names should be joined
	/// with ", " (mirrors the aidoku `get_header` behaviour).
	pub headers: HashMap<String, String>,
	pub data: Vec<u8>,
}

/// A stored user-default value (mirrors aidoku `DefaultValue`).
///
/// `Data` carries bytes that are already postcard-encoded; the other variants
/// are stored by the runner as postcard-encoded values.
#[derive(Debug, Clone, uniffi::Enum)]
pub enum HostDefaultValue {
	Bool(bool),
	Int(i32),
	Float(f32),
	String(String),
	StringArray(Vec<String>),
	Null,
	Data(Vec<u8>),
}

/// Host implementations must be `Send + Sync` so the runner can hold the trait
/// object behind an `Arc` inside its engine state.
///
/// EVERY method returns `Result<T, RunnerError>` on purpose.
///
/// A `#[uniffi::export(foreign)]` method whose Rust return type is a bare `T`
/// makes uniffi answer a host that breaks the FFI contract by PANICKING inside
/// `LiftReturn::handle_callback_unexpected_error`. That panic originates under
/// an `extern "C"` callback frame, which Rust marks `nounwind`, so it cannot be
/// caught: the unwinder aborts the whole host process (an app crash on Android,
/// a SIGABRT that kills the Gradle test JVM mid-suite). Declaring the error
/// type selects uniffi's `LiftReturn for Result<R, E>`, whose
/// `handle_callback_unexpected_error` converts the failure into
/// `Err(RunnerError::HostCallback(..))` instead — the import then reports its
/// normal "no usable value" code to the wasm and the source carries on.
///
/// The Kotlin side is unaffected: uniffi maps `Result<T, E>` for a foreign
/// trait to "return T, throw E", so `KrxHostImpl`'s signatures do not change.
#[uniffi::export(foreign)]
pub trait KomoreiHost: Send + Sync {
	// ---- env ----
	/// Called by `env::print` / `println!` from the source.
	fn log_print(&self, message: String) -> Result<(), RunnerError>;
	/// Called by `env::abort` when the source panics.
	fn log_abort(&self) -> Result<(), RunnerError>;
	/// Called by `env::sleep` (blocks the host thread).
	fn sleep(&self, seconds: i32) -> Result<(), RunnerError>;

	/// Called by `env::send_partial_result` while `get_home` is still running.
	///
	/// A home that needs several requests does not have to make the user wait
	/// for the slowest one: it sends the rails it already has through this
	/// callback and the app appends them as they arrive. Sending a `Layout`
	/// first is how a source tells the app the *shape* of the page so the right
	/// section skeletons can be shown before any content exists; every later
	/// `Component` replaces the placeholder of the same position.
	///
	/// Returned as a `Result` for the same reason as every other method here: a
	/// host that cannot accept the chunk must not abort the source, and the
	/// wasm side treats a failure as "no usable value" and carries on.
	fn partial_home(&self, result: HomePartialResult) -> Result<(), RunnerError>;

	// ---- std dates ----
	/// Current unix timestamp (seconds).
	fn current_date(&self) -> Result<f64, RunnerError>;
	/// UTC offset in seconds (west negative, as chrono `utc_minus_local`).
	fn utc_offset(&self) -> Result<i64, RunnerError>;
	/// Parse a date with a strftime-like format. Returns the unix timestamp in
	/// seconds, or a negative value on failure (the runner passes it through).
	fn parse_date(
		&self,
		date: String,
		format: String,
		locale: Option<String>,
		timezone: Option<String>,
	) -> Result<f64, RunnerError>;

	// ---- defaults ----
	/// Read a stored user default. `None` reports "invalid key" to the source.
	fn defaults_get(&self, key: String) -> Result<Option<HostDefaultValue>, RunnerError>;
	/// Store a user default.
	fn defaults_set(&self, key: String, value: HostDefaultValue) -> Result<(), RunnerError>;

	// ---- net (OkHttp) ----
	/// Perform a blocking HTTP request and return its full response.
	fn net_request(
		&self,
		method: HostHttpMethod,
		url: String,
		headers: HashMap<String, String>,
		body: Vec<u8>,
		timeout: Option<f64>,
	) -> Result<HostNetResponse, RunnerError>;

	// ---- html DOM (Jsoup) ----
	/// Parse HTML into a Document, returning an opaque handle (positive). A
	/// returned handle <= 0 is treated by the runner as InvalidHtml.
	fn html_parse(&self, html: String, base_url: String) -> Result<i64, RunnerError>;
	/// Parse an HTML fragment into a body Document.
	fn html_parse_fragment(&self, html: String, base_url: String) -> Result<i64, RunnerError>;
	fn html_escape(&self, text: String) -> Result<Option<String>, RunnerError>;
	fn html_unescape(&self, text: String) -> Result<Option<String>, RunnerError>;
	/// Kind of the DOM object referenced by `handle`:
	/// 0 Unknown, 1 Node, 2 TextNode, 3 DataNode, 4 Comment, 5 Element,
	/// 6 ElementList, 7 Document.
	fn html_kind(&self, handle: i64) -> Result<i32, RunnerError>;
	fn html_attr(&self, handle: i64, key: String) -> Result<Option<String>, RunnerError>;
	fn html_has_attr(&self, handle: i64, key: String) -> Result<bool, RunnerError>;
	fn html_set_attr(&self, handle: i64, key: String, value: String) -> Result<bool, RunnerError>;
	fn html_remove_attr(&self, handle: i64, key: String) -> Result<bool, RunnerError>;
	/// CSS-select elements. Returns the handle of a (possibly empty) element
	/// list, or `None` for an invalid query / incompatible receiver.
	fn html_select(&self, handle: i64, query: String) -> Result<Option<i64>, RunnerError>;
	/// First element matching the selector, or `None` when nothing matches.
	fn html_select_first(&self, handle: i64, query: String) -> Result<Option<i64>, RunnerError>;
	fn html_text(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_own_text(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_untrimmed_text(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	/// Inner HTML of an element.
	fn html_html(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	/// Outer HTML (full tag including the element itself).
	fn html_outer_html(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_id(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_tag_name(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_class_name(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_has_class(&self, handle: i64, class: String) -> Result<bool, RunnerError>;
	fn html_add_class(&self, handle: i64, class: String) -> Result<bool, RunnerError>;
	fn html_remove_class(&self, handle: i64, class: String) -> Result<bool, RunnerError>;
	/// Size of a list handle; negative means the handle is not a list.
	fn html_size(&self, handle: i64) -> Result<i32, RunnerError>;
	fn html_first(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	fn html_last(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	fn html_get(&self, handle: i64, index: i64) -> Result<Option<i64>, RunnerError>;
	fn html_parent(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	fn html_next(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	fn html_previous(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	/// Sibling elements/nodes of an element/node.
	fn html_siblings(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	/// Direct element children of an element.
	fn html_children(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	/// All child nodes (elements, text, comments) of an element/document.
	fn html_child_nodes(&self, handle: i64) -> Result<Option<i64>, RunnerError>;
	fn html_base_uri(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	/// Data of a data/comment/text node, or `data` of an element.
	fn html_data(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn html_set_text(&self, handle: i64, text: String) -> Result<bool, RunnerError>;
	fn html_set_html(&self, handle: i64, html: String) -> Result<bool, RunnerError>;
	fn html_prepend(&self, handle: i64, html: String) -> Result<bool, RunnerError>;
	fn html_append(&self, handle: i64, html: String) -> Result<bool, RunnerError>;
	fn html_remove(&self, handle: i64) -> Result<bool, RunnerError>;
	/// The wasm dropped this DOM object — release the Jsoup reference.
	fn html_destroy(&self, handle: i64) -> Result<(), RunnerError>;

	// ---- js contexts (host WebView with JavaScript) ----
	/// Create a JS context (an invisible WebView). Returns an opaque handle
	/// (positive). A returned handle <= 0 is treated by the runner as a
	/// JS error.
	fn js_context_create(&self) -> Result<i64, RunnerError>;
	/// Evaluate `code` in the context. Returns a JsValue handle (positive);
	/// <= 0 signals an evaluation error.
	fn js_context_eval(&self, handle: i64, code: String) -> Result<i64, RunnerError>;
	/// Read `global[key]` as a JsValue handle (positive). <= 0 = error.
	fn js_context_get(&self, handle: i64, key: String) -> Result<i64, RunnerError>;

	// ---- js values ----
	/// String rendering of a value; `None` reports a non-stringifiable value.
	fn js_value_to_string(&self, handle: i64) -> Result<Option<String>, RunnerError>;
	fn js_value_to_bool(&self, handle: i64) -> Result<bool, RunnerError>;
	fn js_value_to_int(&self, handle: i64) -> Result<i32, RunnerError>;
	fn js_value_to_f64(&self, handle: i64) -> Result<f64, RunnerError>;
	/// Whether the value is not `undefined`.
	fn js_value_is_defined(&self, handle: i64) -> Result<bool, RunnerError>;
	/// Whether the value is `null`.
	fn js_value_is_null(&self, handle: i64) -> Result<bool, RunnerError>;
	/// Duplicate a value (keeps the original alive).
	fn js_value_clone(&self, handle: i64) -> Result<i64, RunnerError>;
	/// Drop the Kotlin-side reference. Handles are also released when the
	/// wasm destroys the descriptor (`std::destroy`).
	fn js_value_release(&self, handle: i64) -> Result<(), RunnerError>;

	// ---- js webviews ----
	/// Create a web view. Returns an opaque handle (positive).
	fn js_webview_create(&self) -> Result<i64, RunnerError>;
	/// Set the URL rule list that decides whether user scripts are injected
	/// (one regex per line; empty list = inject always).
	fn js_webview_set_rule_list(&self, handle: i64, rules: String) -> Result<(), RunnerError>;
	/// Load a URL with the given extra headers (starts the load; completion is
	/// awaited via `js_webview_wait_for_load`).
	fn js_webview_load_url(
		&self,
		handle: i64,
		url: String,
		headers: HashMap<String, String>,
	) -> Result<(), RunnerError>;
	/// Load an HTML string relative to a base URL.
	fn js_webview_load_html(
		&self,
		handle: i64,
		html: String,
		base_url: String,
	) -> Result<(), RunnerError>;
	/// Block until the page triggered by the last load finishes loading.
	fn js_webview_wait_for_load(&self, handle: i64) -> Result<(), RunnerError>;
	/// Evaluate JS in the loaded page; returns a JsValue handle (positive).
	fn js_webview_eval(&self, handle: i64, code: String) -> Result<i64, RunnerError>;
	/// Register a script injected on document start/end for matching pages.
	fn js_webview_add_user_script(
		&self,
		handle: i64,
		code: String,
		at_document_end: bool,
		for_main_frame_only: bool,
	) -> Result<(), RunnerError>;
	/// Current cookies of the webview as a name → value map (the "Cookie"
	/// header string, parsed).
	fn js_webview_get_cookies(&self, handle: i64) -> Result<HashMap<String, String>, RunnerError>;
	/// Delete a cookie by name/value/domain on the webview.
	fn js_webview_delete_cookie(
		&self,
		handle: i64,
		name: String,
		value: String,
		domain: String,
	) -> Result<(), RunnerError>;
}
