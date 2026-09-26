//! End-to-end tests against the reference source (`komorei-sdk/examples/example-source`,
//! whose compiled `Payload/main.wasm` lives inside `package.krx`).
//!
//! A canned `CannedHost` plays the part of the Kotlin app: net → example.com,
//! DOM → a minimal hand-rolled tree (only what `fetch_example_title` uses),
//! defaults → an in-memory map. Every test creates a fresh runner, loads the
//! wasm, calls `start()`, then exercises the source exports through the public
//! `komorei_runner` API.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use komorei_runner::{
	Anime, AnimePageResult, AnimeStatus, DeepLinkResult, Episode, Filter, FilterKind, FilterValue,
	HomeComponentValue, HomePartialResult, HostDefaultValue, HostHttpMethod, HostNetResponse,
	KomoreiHost, KomoreiRunner, Listing, ListingKind, RunnerError, SettingValue, StreamData,
	StreamInfo, StreamType, SubtitleInfo,
};

// ---------------------------------------------------------------------------
// Canned host
// ---------------------------------------------------------------------------

const EXAMPLE_HTML: &str = "\
<!doctype html>
<html>
<head><title>Example Domain</title></head>
<body>
	<div>
		<h1>Example Domain</h1>
		<p>This domain is for use in illustrative examples. You may use this domain in literature without prior coordination or asking for permission.</p>
		<p><a href=\"https://www.iana.org/domains/example\">More information...</a></p>
	</div>
</body>
</html>
";

/// A minimal fake for the Kotlin host. Handles the example flow exactly:
/// `https://example.com` returns the classic page, parsing it yields a
/// document whose `h1` reads "Example Domain".
struct CannedHost {
	defaults: Mutex<HashMap<String, HostDefaultValue>>,
	net_calls: Mutex<Vec<(HostHttpMethod, String, String)>>,
	/// DOM handle → (kind, text). Handle 1 = document, handle 2 = its h1.
	dom: Mutex<HashMap<i64, (i32, Option<String>)>>,
	/// Recorded `js_*` calls (for assertions); canned handles/values below.
	js_calls: Mutex<Vec<String>>,
	/// Chunks a source streamed out of `get_home` via `send_partial_result`,
	/// in arrival order.
	partials: Mutex<Vec<HomePartialResult>>,
}

impl CannedHost {
	fn new() -> Self {
		let mut dom = HashMap::new();
		dom.insert(1, (7, None)); // Document
		dom.insert(2, (5, Some("Example Domain".to_string()))); // Element h1
		Self {
			defaults: Mutex::new(HashMap::new()),
			net_calls: Mutex::new(Vec::new()),
			dom: Mutex::new(dom),
			js_calls: Mutex::new(Vec::new()),
			partials: Mutex::new(Vec::new()),
		}
	}

	/// The chunks the source streamed, in arrival order.
	fn partials(&self) -> Vec<HomePartialResult> {
		self.partials.lock().expect("partials lock").clone()
	}

	fn net_calls(&self) -> Vec<(HostHttpMethod, String, String)> {
		self.net_calls.lock().unwrap().clone()
	}

	fn js_calls(&self) -> Vec<String> {
		self.js_calls.lock().unwrap().clone()
	}

	fn record_js(&self, call: impl Into<String>) {
		self.js_calls.lock().unwrap().push(call.into());
	}
}

impl KomoreiHost for CannedHost {
	fn log_print(&self, message: String) -> Result<(), RunnerError> {
		eprintln!("[source println] {message}");
		Ok(())
	}

	fn log_abort(&self) -> Result<(), RunnerError> {
		eprintln!("[source abort]");
		Ok(())
	}

	fn sleep(&self, _seconds: i32) -> Result<(), RunnerError> {
		Ok(())
	}

	fn partial_home(&self, result: HomePartialResult) -> Result<(), RunnerError> {
		self.partials.lock().expect("partials lock").push(result);
		Ok(())
	}

	fn current_date(&self) -> Result<f64, RunnerError> {
		Ok(0.0)
	}

	fn utc_offset(&self) -> Result<i64, RunnerError> {
		Ok(0)
	}

	fn parse_date(
		&self,
		_date: String,
		_format: String,
		_locale: Option<String>,
		_timezone: Option<String>,
	) -> Result<f64, RunnerError> {
		Ok(-1.0)
	}

	fn defaults_get(&self, key: String) -> Result<Option<HostDefaultValue>, RunnerError> {
		Ok(self.defaults.lock().unwrap().get(&key).cloned())
	}

	fn defaults_set(&self, key: String, value: HostDefaultValue) -> Result<(), RunnerError> {
		self.defaults.lock().unwrap().insert(key, value);
		Ok(())
	}

	fn net_request(
		&self,
		method: HostHttpMethod,
		url: String,
		_headers: HashMap<String, String>,
		body: Vec<u8>,
		_timeout: Option<f64>,
	) -> Result<HostNetResponse, RunnerError> {
		self.net_calls.lock().unwrap().push((
			method,
			url.clone(),
			String::from_utf8_lossy(&body).into_owned(),
		));
		Ok(if url == "https://example.com" {
			HostNetResponse {
				ok: true,
				status: 200,
				url,
				headers: HashMap::new(),
				data: EXAMPLE_HTML.as_bytes().to_vec(),
			}
		} else {
			HostNetResponse {
				ok: false,
				status: 0,
				url,
				headers: HashMap::new(),
				data: Vec::new(),
			}
		})
	}

	fn html_parse(&self, _html: String, _base_url: String) -> Result<i64, RunnerError> {
		Ok(1)
	}

	fn html_parse_fragment(&self, _html: String, _base_url: String) -> Result<i64, RunnerError> {
		Ok(1)
	}

	fn html_escape(&self, text: String) -> Result<Option<String>, RunnerError> {
		Ok(Some(text))
	}

	fn html_unescape(&self, text: String) -> Result<Option<String>, RunnerError> {
		Ok(Some(text))
	}

	fn html_kind(&self, handle: i64) -> Result<i32, RunnerError> {
		Ok(self
			.dom
			.lock()
			.unwrap()
			.get(&handle)
			.map(|(k, _)| *k)
			.unwrap_or(0))
	}

	fn html_attr(&self, _handle: i64, _key: String) -> Result<Option<String>, RunnerError> {
		Ok(None)
	}

	fn html_has_attr(&self, _handle: i64, _key: String) -> Result<bool, RunnerError> {
		Ok(false)
	}

	fn html_set_attr(
		&self,
		_handle: i64,
		_key: String,
		_value: String,
	) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_remove_attr(&self, _handle: i64, _key: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_select(&self, _handle: i64, _query: String) -> Result<Option<i64>, RunnerError> {
		Ok(if _query == "h1" { Some(2) } else { None })
	}

	fn html_select_first(&self, _handle: i64, query: String) -> Result<Option<i64>, RunnerError> {
		Ok(if query == "h1" { Some(2) } else { None })
	}

	fn html_text(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		Ok(self
			.dom
			.lock()
			.unwrap()
			.get(&handle)
			.and_then(|(_, t)| t.clone()))
	}

	fn html_own_text(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		self.html_text(handle)
	}

	fn html_untrimmed_text(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		self.html_text(handle)
	}

	fn html_html(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		self.html_text(handle)
	}

	fn html_outer_html(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		self.html_text(handle)
	}

	fn html_id(&self, _handle: i64) -> Result<Option<String>, RunnerError> {
		Ok(None)
	}

	fn html_tag_name(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		Ok(if self.html_kind(handle).unwrap_or(0) == 5 {
			Some("h1".into())
		} else {
			None
		})
	}

	fn html_class_name(&self, _handle: i64) -> Result<Option<String>, RunnerError> {
		Ok(None)
	}

	fn html_has_class(&self, _handle: i64, _class: String) -> Result<bool, RunnerError> {
		Ok(false)
	}

	fn html_add_class(&self, _handle: i64, _class: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_remove_class(&self, _handle: i64, _class: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_size(&self, handle: i64) -> Result<i32, RunnerError> {
		// handle 3 is the canned element-list for `select` queries
		Ok(if handle == 3 { 1 } else { -1 })
	}

	fn html_first(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(Some(2))
	}

	fn html_last(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(Some(2))
	}

	fn html_get(&self, _handle: i64, index: i64) -> Result<Option<i64>, RunnerError> {
		Ok(if index == 0 { Some(2) } else { None })
	}

	fn html_parent(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(None)
	}

	fn html_next(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(None)
	}

	fn html_previous(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(None)
	}

	fn html_siblings(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(None)
	}

	fn html_children(&self, _handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(None)
	}

	fn html_child_nodes(&self, handle: i64) -> Result<Option<i64>, RunnerError> {
		Ok(if handle == 1 { Some(3) } else { None })
	}

	fn html_base_uri(&self, _handle: i64) -> Result<Option<String>, RunnerError> {
		Ok(None)
	}

	fn html_data(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		self.html_text(handle)
	}

	fn html_set_text(&self, _handle: i64, _text: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_set_html(&self, _handle: i64, _html: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_prepend(&self, _handle: i64, _html: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_append(&self, _handle: i64, _html: String) -> Result<bool, RunnerError> {
		Ok(true)
	}

	fn html_remove(&self, handle: i64) -> Result<bool, RunnerError> {
		Ok(self.dom.lock().unwrap().remove(&handle).is_some())
	}

	fn html_destroy(&self, handle: i64) -> Result<(), RunnerError> {
		self.dom.lock().unwrap().remove(&handle);
		Ok(())
	}

	// -- js (canned: contexts = 101, context values = 201, webviews = 301,
	//    webview values = 401; value reads return 42 / "42" / true) --------

	fn js_context_create(&self) -> Result<i64, RunnerError> {
		self.record_js("context_create");
		Ok(101)
	}

	fn js_context_eval(&self, handle: i64, code: String) -> Result<i64, RunnerError> {
		self.record_js(format!("context_eval {handle} {code}"));
		Ok(201)
	}

	fn js_context_get(&self, handle: i64, key: String) -> Result<i64, RunnerError> {
		self.record_js(format!("context_get {handle} {key}"));
		Ok(202)
	}

	fn js_value_to_string(&self, handle: i64) -> Result<Option<String>, RunnerError> {
		self.record_js(format!("value_to_string {handle}"));
		Ok(Some("42".to_string()))
	}

	fn js_value_to_bool(&self, handle: i64) -> Result<bool, RunnerError> {
		self.record_js(format!("value_to_bool {handle}"));
		Ok(true)
	}

	fn js_value_to_int(&self, handle: i64) -> Result<i32, RunnerError> {
		self.record_js(format!("value_to_int {handle}"));
		Ok(42)
	}

	fn js_value_to_f64(&self, handle: i64) -> Result<f64, RunnerError> {
		self.record_js(format!("value_to_f64 {handle}"));
		Ok(42.5)
	}

	fn js_value_is_defined(&self, handle: i64) -> Result<bool, RunnerError> {
		self.record_js(format!("value_is_defined {handle}"));
		Ok(true)
	}

	fn js_value_is_null(&self, handle: i64) -> Result<bool, RunnerError> {
		self.record_js(format!("value_is_null {handle}"));
		Ok(false)
	}

	fn js_value_clone(&self, handle: i64) -> Result<i64, RunnerError> {
		self.record_js(format!("value_clone {handle}"));
		Ok(handle + 1000)
	}

	fn js_value_release(&self, handle: i64) -> Result<(), RunnerError> {
		self.record_js(format!("value_release {handle}"));
		Ok(())
	}

	fn js_webview_create(&self) -> Result<i64, RunnerError> {
		self.record_js("webview_create");
		Ok(301)
	}

	fn js_webview_set_rule_list(&self, handle: i64, rules: String) -> Result<(), RunnerError> {
		self.record_js(format!("webview_set_rule_list {handle} {rules}"));
		Ok(())
	}

	fn js_webview_load_url(
		&self,
		handle: i64,
		url: String,
		headers: HashMap<String, String>,
	) -> Result<(), RunnerError> {
		self.record_js(format!("webview_load_url {handle} {url} {:?}", headers));
		Ok(())
	}

	fn js_webview_load_html(
		&self,
		handle: i64,
		_html: String,
		base_url: String,
	) -> Result<(), RunnerError> {
		self.record_js(format!("webview_load_html {handle} {base_url}"));
		Ok(())
	}

	fn js_webview_wait_for_load(&self, handle: i64) -> Result<(), RunnerError> {
		self.record_js(format!("webview_wait_for_load {handle}"));
		Ok(())
	}

	fn js_webview_eval(&self, handle: i64, code: String) -> Result<i64, RunnerError> {
		self.record_js(format!("webview_eval {handle} {code}"));
		Ok(401)
	}

	fn js_webview_add_user_script(
		&self,
		handle: i64,
		code: String,
		at_document_end: bool,
		for_main_frame_only: bool,
	) -> Result<(), RunnerError> {
		self.record_js(format!(
			"webview_add_user_script {handle} {at_document_end} {for_main_frame_only} {code}"
		));
		Ok(())
	}

	fn js_webview_get_cookies(&self, handle: i64) -> Result<HashMap<String, String>, RunnerError> {
		self.record_js(format!("webview_get_cookies {handle}"));
		let mut cookies = HashMap::new();
		cookies.insert("session".to_string(), "abc".to_string());
		Ok(cookies)
	}

	fn js_webview_delete_cookie(
		&self,
		handle: i64,
		name: String,
		value: String,
		domain: String,
	) -> Result<(), RunnerError> {
		self.record_js(format!(
			"webview_delete_cookie {handle} {name} {value} {domain}"
		));
		Ok(())
	}
}

// ---------------------------------------------------------------------------
// Harness
// ---------------------------------------------------------------------------

fn extract_main_wasm() -> Vec<u8> {
	// The reference source lives in the separate komorei-sdk repository (the
	// same relative-path relationship as the `komorei` dependency above).
	let path = concat!(
		env!("CARGO_MANIFEST_DIR"),
		"/../komorei-sdk/examples/example-source/package.krx"
	);
	let file = std::fs::File::open(path).expect("open package.krx");
	let mut zip = zip::ZipArchive::new(file).expect("package.krx is a zip");
	let mut entry = zip
		.by_name("Payload/main.wasm")
		.expect("Payload/main.wasm entry");
	let mut wasm = Vec::new();
	std::io::Read::read_to_end(&mut entry, &mut wasm).expect("read main.wasm");
	wasm
}

/// A fresh runner with the example wasm loaded and started.
fn loaded_runner(host: &Arc<CannedHost>) -> Arc<KomoreiRunner> {
	let runner = KomoreiRunner::new(host.clone());
	runner.load(extract_main_wasm()).expect("load wasm");
	runner.start().expect("start source");
	runner
}

fn sample_anime() -> Anime {
	Anime {
		key: "1".into(),
		source_id: "en.example-source".into(),
		title: "Anime 1".into(),
		original_title: String::new(),
		cover: "https://example.com/cover.png".into(),
		..Default::default()
	}
}

fn sample_episode() -> Episode {
	Episode {
		key: "1".into(),
		episode_number: "1".into(),
		..Default::default()
	}
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

#[test]
fn search_page_one_returns_twenty_entries_without_io() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let result = runner.search(None, 1, Vec::new()).expect("search ok");
	assert_eq!(result.entries.len(), 20);
	assert!(result.has_next_page);

	let first = &result.entries[0];
	assert_eq!(first.key, "1");
	assert_eq!(first.source_id, "en.example-source");
	assert_eq!(first.title, "Anime 1");
	assert_eq!(first.cover, "https://example.com/cover.png");
	assert_eq!(first.status, AnimeStatus::Ongoing);
	assert_eq!(first.current_episode.as_deref(), Some("Tập 12/12"));
	assert_eq!(first.episode_count, 12);
	assert_eq!(first.genres.len(), 1);
	assert_eq!(first.genres[0].name, "Action");

	// the search itself performs no IO
	assert!(
		host.net_calls().is_empty(),
		"search must not hit the network"
	);
}

#[test]
fn search_third_page_has_no_next_page() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let result = runner.search(None, 3, Vec::new()).expect("search ok");
	assert_eq!(result.entries.len(), 20);
	assert_eq!(result.entries[0].key, "41");
	assert!(!result.has_next_page);
}

#[test]
fn search_with_query_filters_entries() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let result = runner
		.search(Some("Anime 13".to_string()), 1, Vec::new())
		.expect("search ok");
	assert_eq!(result.entries.len(), 1);
	assert_eq!(result.entries[0].key, "13");
}

#[test]
fn search_accepts_filter_values() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	// the example source ignores filters, but they must round-trip through the
	// postcard filter descriptor without erroring on the wasm side
	let filters = vec![FilterValue::Text {
		id: "text".into(),
		value: "13".into(),
	}];
	let result = runner
		.search(Some("Anime 13".to_string()), 1, filters)
		.expect("search ok");
	assert_eq!(result.entries.len(), 1);
	assert_eq!(result.entries[0].key, "13");
}

// ---------------------------------------------------------------------------
// Anime update
// ---------------------------------------------------------------------------

#[test]
fn anime_update_details_fetches_and_parses_title() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let full = runner
		.anime_update(sample_anime(), true, true)
		.expect("update ok");

	// needs_details=true drove net -> html pipeline (Jsoup stand-in)
	assert_eq!(full.description.as_deref(), Some("Example Domain"));
	let calls = host.net_calls();
	assert_eq!(calls.len(), 1);
	assert_eq!(calls[0].0, HostHttpMethod::Get);
	assert_eq!(calls[0].1, "https://example.com");

	assert_eq!(
		full.banner.as_deref(),
		Some("https://example.com/cover.png")
	);
	assert_eq!(full.status, AnimeStatus::Ongoing);
	assert_eq!(
		full.release_year.as_ref().map(|c| c.name.as_str()),
		Some("2024")
	);
	assert_eq!(full.authors[0].name, "Author");
	assert_eq!(
		full.studio.as_ref().map(|c| c.name.as_str()),
		Some("Studio")
	);
	assert_eq!(full.rating, Some(8.5));
	assert_eq!(full.rating_count, Some(1234));
	assert_eq!(full.views, 99999);
	assert_eq!(
		full.next_episode_air_info.as_deref(),
		Some("Tập 13 phát sóng 20:00 thứ 7")
	);
	assert_eq!(full.quality_tag.as_deref(), Some("FHD"));
	assert_eq!(full.url.as_deref(), Some("https://example.com/anime/1"));
	assert_eq!(full.seasons.len(), 2);
	assert_eq!(full.seasons[0].id, "1");
	assert_eq!(full.seasons[1].title, "Season 2");

	// needs_chapters=true returned the full 8-episode list
	let episodes = full.episodes.expect("episodes present");
	assert_eq!(episodes.len(), 8);
	assert_eq!(episodes[0].key, "8");
	assert_eq!(episodes[1].key, "7");
	assert_eq!(episodes[1].title.as_deref(), Some("Title"));
	assert_eq!(
		episodes[1].thumbnail.as_deref(),
		Some("https://example.com/cover.png")
	);
	assert_eq!(episodes[1].quality.as_deref(), Some("1080p FHD"));
	assert_eq!(episodes[2].date_uploaded, Some(1692318525));
	assert_eq!(episodes[7].key, "1");
}

#[test]
fn anime_update_without_flags_makes_no_io() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let same = runner
		.anime_update(sample_anime(), false, false)
		.expect("update ok");
	assert_eq!(same.description, None);
	assert_eq!(same.episodes, None);
	assert!(host.net_calls().is_empty());
}

// ---------------------------------------------------------------------------
// Streams
// ---------------------------------------------------------------------------

#[test]
fn stream_list_returns_two_servers() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let streams = runner
		.stream_list(sample_anime(), sample_episode())
		.expect("stream list ok");
	assert_eq!(streams.len(), 2);
	assert_eq!(
		streams,
		vec![
			StreamInfo {
				key: "mux".into(),
				name: "Server 1".into(),
				quality: "1080p".into()
			},
			StreamInfo {
				key: "mp4".into(),
				name: "Server 2".into(),
				quality: "720p".into()
			},
		]
	);
}

#[test]
fn stream_resolves_hls_with_subs_and_ranges() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let streams = runner
		.stream_list(sample_anime(), sample_episode())
		.expect("stream list ok");
	let data = runner
		.stream(sample_anime(), sample_episode(), streams[0].clone())
		.expect("stream ok");

	assert_eq!(
		data.url,
		"https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
	);
	assert_eq!(data.stream_type, StreamType::HLS);
	assert!(data.is_content);
	assert_eq!(
		data.headers.get("User-Agent").map(String::as_str),
		Some("Komorei/1.0")
	);

	assert_eq!(data.subtitles.len(), 1);
	assert_eq!(
		data.subtitles[0],
		SubtitleInfo {
			url: "https://example.com/subs/vi.vtt".into(),
			language: "vi".into(),
			label: Some("Tiếng Việt".into()),
			headers: HashMap::new(),
		}
	);

	let intro = data.intro.expect("intro range");
	assert_eq!((intro.start_ms, intro.end_ms), (0, 90_000));
	let outro = data.outro.expect("outro range");
	assert_eq!((outro.start_ms, outro.end_ms), (1_500_000, 1_590_000));
}

#[test]
fn stream_resolves_mp4_without_subs() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let streams = runner
		.stream_list(sample_anime(), sample_episode())
		.expect("stream list ok");
	let data = runner
		.stream(sample_anime(), sample_episode(), streams[1].clone())
		.expect("stream ok");

	assert_eq!(
		data.url,
		"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
	);
	assert_eq!(data.stream_type, StreamType::MP4);
	assert!(data.is_content);
	assert_eq!(
		data.headers.get("User-Agent").map(String::as_str),
		Some("Komorei/1.0")
	);
	assert!(data.subtitles.is_empty());
	assert!(data.intro.is_none() && data.outro.is_none());
}

// ---------------------------------------------------------------------------
// Filters / settings / listings
// ---------------------------------------------------------------------------

#[test]
fn dynamic_filters_decode_to_seven_filters() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let filters = runner.filters().expect("filters ok");
	assert_eq!(filters.len(), 7);

	let by_id: HashMap<&str, &Filter> = filters.iter().map(|f| (f.id.as_str(), f)).collect();

	assert!(matches!(
		&by_id["text"].kind,
		FilterKind::Text { placeholder: Some(p) } if p == "Search"
	));
	assert!(matches!(
		&by_id["sort"].kind,
		FilterKind::Sort { can_ascend: true, options, default: None } if options == &["Popular", "Recent"]
	));
	assert!(matches!(
		&by_id["check"].kind,
		FilterKind::Check {
			can_exclude: true,
			..
		}
	));
	assert!(matches!(
		&by_id["select"].kind,
		FilterKind::Select { uses_tag_style: true, options, .. } if options == &["One", "Two"]
	));
	assert!(matches!(
		&by_id["mselect"].kind,
		FilterKind::MultiSelect { can_exclude: true, uses_tag_style: false, options, .. }
			if options == &["One", "Two"]
	));
	assert!(matches!(&by_id["note"].kind, FilterKind::Note(text) if text == "Testing note"));
	assert!(matches!(
		&by_id["range"].kind,
		FilterKind::Range { min: Some(m), max: Some(x), decimal: true } if *m == 0.0 && *x == 100.0
	));
}

#[test]
fn settings_reflect_defaults() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	// default value missing -> 1 setting
	let settings = runner.settings().expect("settings ok");
	assert_eq!(settings.len(), 1);
	assert_eq!(settings[0].key, "setting");
	assert_eq!(settings[0].title, "Toggle");
	assert_eq!(settings[0].notification.as_deref(), Some("test"));
	assert_eq!(
		settings[0].refreshes.as_deref(),
		Some(&["settings".to_string()][..])
	);
	assert!(matches!(
		settings[0].value,
		SettingValue::Toggle { default: false, .. }
	));

	// after the host stores Bool(true) the DynamicSettings adds setting2
	host.defaults_set("setting".into(), HostDefaultValue::Bool(true))
		.expect("defaults_set ok");
	let settings = runner.settings().expect("settings ok");
	assert_eq!(settings.len(), 2);
	assert_eq!(settings[1].key, "setting2");
	assert_eq!(settings[1].title, "Toggle 2");
}

#[test]
fn dynamic_listings_single_tab() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let listings = runner.listings().expect("listings ok");
	assert_eq!(
		listings,
		vec![Listing {
			id: "listing".into(),
			name: "Listing".into(),
			kind: ListingKind::List
		}]
	);
}

#[test]
fn anime_list_ok_for_listing_and_message_error_for_test() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let ok = runner
		.anime_list(
			Listing {
				id: "listing".into(),
				name: "Listing".into(),
				kind: ListingKind::List,
			},
			1,
		)
		.expect("listing ok");
	assert_eq!(ok.entries.len(), 1);
	assert_eq!(ok.entries[0].key, "1");
	assert_eq!(ok.entries[0].title, "Anime 1");
	assert!(!ok.has_next_page);

	let err = runner
		.anime_list(
			Listing {
				id: "test".into(),
				name: "Test".into(),
				kind: ListingKind::Default,
			},
			1,
		)
		.expect_err("must fail for id 'test'");
	match err {
		RunnerError::Source { code, message } => {
			assert_eq!(code, -1);
			assert!(message.contains("Not supported"), "message was: {message}");
		}
		other => panic!("expected Source error, got {other:?}"),
	}
}

// ---------------------------------------------------------------------------
// Misc exports
// ---------------------------------------------------------------------------

#[test]
fn base_url_missing_export_errors() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let err = runner
		.base_url()
		.expect_err("example source does not implement BaseUrlProvider");
	match err {
		RunnerError::ExportMissing { name } => assert_eq!(name, "get_base_url"),
		other => panic!("expected ExportMissing, got {other:?}"),
	}
}

#[test]
fn notify_returns_unit() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	// handle_notification returns literal 0 (unit), so Ok(())
	runner.notify("test".into()).expect("notify ok");
}

#[test]
fn deep_link_returns_anime() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let result = runner
		.deep_link("https://example.com/anime/1".into())
		.expect("deep link ok");
	assert_eq!(
		result,
		Some(DeepLinkResult::Anime {
			key: "anime_key".into()
		})
	);
}

#[test]
fn segment_interceptors_are_identity() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let url = "https://example.com/seg/1.ts".to_string();

	// without stream data
	let same = runner
		.intercept_segment_url(None, url.clone())
		.expect("url interceptor");
	assert_eq!(same, url);

	let data = runner
		.intercept_segment_data(None, url.clone(), vec![1, 2, 3])
		.expect("data interceptor");
	assert_eq!(data, vec![1, 2, 3]);

	// with stream data
	let stream = StreamData {
		url: "https://bad.example.com/master.m3u8".into(),
		stream_type: StreamType::HLS,
		is_content: true,
		..Default::default()
	};
	let same = runner
		.intercept_segment_url(Some(stream.clone()), url.clone())
		.expect("url interceptor");
	assert_eq!(same, url);

	let data = runner
		.intercept_segment_data(Some(stream), url, vec![9, 8, 7])
		.expect("data interceptor");
	assert_eq!(data, vec![9, 8, 7]);
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

#[test]
fn home_has_seven_components() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	let home = runner.home().expect("home ok");
	let titles: Vec<&str> = home
		.components
		.iter()
		.map(|c| c.title.as_deref().unwrap_or(""))
		.collect();
	assert_eq!(
		titles,
		vec![
			"Big Scroller",
			"Anime Episode List",
			"Anime List",
			"Anime List (Paged, Ranking)",
			"Scroller",
			"Filters",
			"Links",
		]
	);

	// BigScroller carries all 20 entries of the first search page
	match &home.components[0].value {
		HomeComponentValue::BigScroller {
			entries,
			auto_scroll_interval,
		} => {
			assert_eq!(entries.len(), 20);
			assert_eq!(*auto_scroll_interval, Some(10.0));
		}
		other => panic!("expected BigScroller, got {other:?}"),
	}

	// AnimeEpisodeList has 3 AnimeWithEpisode entries
	match &home.components[1].value {
		HomeComponentValue::AnimeEpisodeList { entries, .. } => assert_eq!(entries.len(), 3),
		other => panic!("expected AnimeEpisodeList, got {other:?}"),
	}

	// the Filters component lists the genre names
	match &home.components[5].value {
		HomeComponentValue::Filters(items) => {
			let names: Vec<&str> = items.iter().map(|i| i.title.as_str()).collect();
			assert_eq!(
				names,
				vec![
					"Action",
					"Adventure",
					"Fantasy",
					"Horror",
					"Slice of Life",
					"Magic",
					"Adaptation"
				]
			);
		}
		other => panic!("expected Filters, got {other:?}"),
	}
}

/// A source streaming its home out of `get_home` reaches the host as it builds
/// it, so the app can paint each row as it lands instead of waiting for the
/// slowest request.
#[test]
fn home_streams_partial_results_before_it_returns() {
	let host = Arc::new(CannedHost::new());
	let runner = loaded_runner(&host);

	// Nothing yet: the chunks only arrive while `home()` runs.
	assert!(host.partials().is_empty(), "partials before get_home");

	let home = runner.home().expect("home ok");
	let partials = host.partials();
	assert!(!partials.is_empty(), "source sent no partial results");

	// The first chunk is the placeholder layout: every row the page will have,
	// with no entries, so the app can show the right skeletons immediately.
	let HomePartialResult::Layout(placeholder) = &partials[0] else {
		panic!("expected a layout placeholder first, got {:?}", partials[0]);
	};
	let titles = |layout: &komorei_runner::HomeLayout| -> Vec<String> {
		layout
			.components
			.iter()
			.map(|c| c.title.clone().unwrap_or_default())
			.collect()
	};
	assert_eq!(
		titles(placeholder),
		titles(&home),
		"the placeholder must name every row the real layout has"
	);
	// A placeholder carries no content, or the app would render it as a real row.
	for component in &placeholder.components {
		let empty = match &component.value {
			HomeComponentValue::Links(links) => links.is_empty(),
			HomeComponentValue::Scroller { entries, .. } => entries.is_empty(),
			HomeComponentValue::BigScroller { entries, .. } => entries.is_empty(),
			HomeComponentValue::AnimeList { entries, .. } => entries.is_empty(),
			HomeComponentValue::AnimeEpisodeList { entries, .. } => entries.is_empty(),
			HomeComponentValue::ImageScroller { links, .. } => links.is_empty(),
			HomeComponentValue::Filters(items) => items.is_empty(),
		};
		assert!(
			empty,
			"placeholder row {:?} carries content",
			component.title
		);
	}

	// Every row then follows as its own chunk, in the same order the real
	// layout uses — a host that appends on arrival ends up with the same page.
	let streamed: Vec<HomePartialResult> = partials[1..].to_vec();
	assert_eq!(
		streamed.len(),
		home.components.len(),
		"expected one chunk per row"
	);
	for (chunk, component) in streamed.iter().zip(&home.components) {
		let HomePartialResult::Component(streamed) = chunk else {
			panic!("expected a component chunk, got {chunk:?}");
		};
		assert_eq!(
			streamed, component,
			"a streamed row differs from the final one"
		);
	}
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

#[test]
fn calling_exports_before_load_is_not_loaded() {
	let host = Arc::new(CannedHost::new());
	let runner = KomoreiRunner::new(host.clone());

	let err = runner
		.search(None, 1, Vec::new())
		.expect_err("not loaded yet");
	assert!(matches!(err, RunnerError::NotLoaded));
}

#[test]
fn page_result_type_is_usable() {
	// guards the Kotlin-facing record shapes against accidental removal
	let _ = AnimePageResult::default();
}

// ---------------------------------------------------------------------------
// js host contract
// ---------------------------------------------------------------------------

#[test]
fn js_host_contract_calls_through() {
	// Guards the js_* trait surface (the same methods the Kotlin bindings
	// expose). Canned values are asserted so signature drift fails loudly.
	// Every method is fallible now (see the `KomoreiHost` note in host.rs), so
	// the assertions unwrap the `Result` and check the value.
	let host = CannedHost::new();
	assert_eq!(host.js_context_create().unwrap(), 101);
	assert_eq!(host.js_context_eval(101, "1+1".into()).unwrap(), 201);
	assert_eq!(host.js_context_get(101, "window".into()).unwrap(), 202);
	assert_eq!(
		host.js_value_to_string(201).unwrap(),
		Some("42".to_string())
	);
	assert!(host.js_value_to_bool(201).unwrap());
	assert_eq!(host.js_value_to_int(201).unwrap(), 42);
	assert_eq!(host.js_value_to_f64(201).unwrap(), 42.5);
	assert!(host.js_value_is_defined(201).unwrap());
	assert!(!host.js_value_is_null(201).unwrap());
	assert_eq!(host.js_value_clone(201).unwrap(), 1201);
	host.js_value_release(201).unwrap();
	assert_eq!(host.js_webview_create().unwrap(), 301);
	host.js_webview_set_rule_list(301, ".*\\.example\\.com".into())
		.unwrap();
	host.js_webview_load_url(
		301,
		"https://example.com".into(),
		HashMap::from([("X-Test".into(), "1".into())]),
	)
	.unwrap();
	host.js_webview_load_html(301, "<html/>".into(), "https://example.com".into())
		.unwrap();
	host.js_webview_wait_for_load(301).unwrap();
	assert_eq!(
		host.js_webview_eval(301, "document.title".into()).unwrap(),
		401
	);
	host.js_webview_add_user_script(301, "window.x = 1".into(), true, true)
		.unwrap();
	assert_eq!(
		host.js_webview_get_cookies(301).unwrap(),
		HashMap::from([("session".into(), "abc".into())])
	);
	host.js_webview_delete_cookie(301, "session".into(), "abc".into(), ".example.com".into())
		.unwrap();

	let calls = host.js_calls();
	assert!(calls.contains(&"context_create".to_string()));
	assert!(calls.contains(&"context_eval 101 1+1".to_string()));
	assert!(
		calls.contains(&"webview_load_url 301 https://example.com {\"X-Test\": \"1\"}".to_string())
	);
	assert!(calls.contains(&"webview_get_cookies 301".to_string()));
}
