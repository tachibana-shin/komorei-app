//! Komorei SDK runner: loads a `.krx` (wasm) module with wasmi and calls its
//! exports. All host IO is delegated to the Kotlin app via the `KomoreiHost`
//! foreign trait (network → OkHttp, HTML DOM → Jsoup, defaults →
//! SharedPreferences, dates/logs → app services).
//!
//! The runner is the ABI owner for the rid store: arguments are postcard- /
//! raw-encoded into descriptors, results are decoded from the lib's result
//! buffer layout and freed via the `free_result` wasm export.

uniffi::setup_scaffolding!();

mod abi;
mod deser;
mod engine;
mod error;
mod host;
mod imports;
mod records;
mod state;

pub use error::RunnerError;
pub use host::{HostDefaultValue, HostHttpMethod, HostNetResponse, KomoreiHost};
pub use records::{
	Anime, AnimePageResult, AnimeSeason, AnimeStatus, AnimeWithEpisode, CategoryLink, DeepLinkResult,
	Episode, Filter, FilterItem, FilterKind, FilterValue, HomeComponent, HomeComponentValue,
	HomeLayout, Link, LinkValue, Listing, ListingKind, LoginMethod, PageIcon, RangeLong, Setting,
	SettingValue, SortFilterDefault, StreamData, StreamInfo, StreamType, SubtitleInfo,
};

use std::sync::{Arc, Mutex};

/// A loaded instance of a `.krx` source. Create with `new(host)`, then
/// `load(wasm)` and `start()` before calling the source exports.
#[derive(uniffi::Object)]
pub struct KomoreiRunner {
	host: Arc<dyn KomoreiHost>,
	state: Mutex<Option<engine::EngineState>>,
}

#[uniffi::export]
impl KomoreiRunner {
	/// Creates an empty runner bound to the given Kotlin host.
	#[uniffi::constructor]
	pub fn new(host: Arc<dyn KomoreiHost>) -> Arc<Self> {
		Arc::new(Self { host, state: Mutex::new(None) })
	}

	/// Instantiates the wasm payload (the `Payload/main.wasm` bytes extracted
	/// from a `.krx`). Does not run the source `start()` export.
	pub fn load(&self, wasm: Vec<u8>) -> Result<(), RunnerError> {
		let mut guard = self.state.lock().map_err(|_| RunnerError::Wasm("runner mutex poisoned".into()))?;
		let engine = engine::EngineState::load(&wasm, self.host.clone())?;
		*guard = Some(engine);
		Ok(())
	}

	/// Runs the source `start()` export (initializes the source instance).
	pub fn start(&self) -> Result<(), RunnerError> {
		self.with_engine(|engine| engine.call_start())
	}

	/// `get_search_anime_list(query, page, filters)`.
	pub fn search(&self, query: Option<String>, page: i32, filters: Vec<FilterValue>) -> Result<AnimePageResult, RunnerError> {
		self.with_engine(|engine| {
			let query_desc = match query {
				Some(q) => engine.encode_raw_string(&q),
				None => 0,
			};
			let lib_filters: Vec<komorei::FilterValue> =
				filters.into_iter().map(Into::into).collect();
			let filters_desc = engine.encode(&lib_filters)?;
			let ptr = engine.call3("get_search_anime_list", query_desc, page, filters_desc)?;
			let decoded = engine.decode::<records::AnimePageResultBuf>(ptr)?;
			Ok(decoded.into())
		})
	}

	/// `get_anime_update(anime, needs_details, needs_chapters)` — upgrades a
	/// Lite anime into a full one (details + episodes).
	pub fn anime_update(&self, anime: Anime, needs_details: bool, needs_chapters: bool) -> Result<Anime, RunnerError> {
		self.with_engine(|engine| {
			let lib_anime: komorei::Anime = anime.into();
			let desc = engine.encode(&lib_anime)?;
			let ptr = engine.call3(
				"get_anime_update",
				desc,
				i32::from(needs_details),
				i32::from(needs_chapters),
			)?;
			let decoded = engine.decode::<komorei::Anime>(ptr)?;
			Ok(decoded.into())
		})
	}

	/// `get_stream_list(anime, episode)` — the stream servers for an episode.
	pub fn stream_list(&self, anime: Anime, episode: Episode) -> Result<Vec<StreamInfo>, RunnerError> {
		self.with_engine(|engine| {
			let anime_desc = engine.encode(&Into::<komorei::Anime>::into(anime))?;
			let episode_desc = engine.encode(&Into::<komorei::Episode>::into(episode))?;
			let ptr = engine.call2("get_stream_list", anime_desc, episode_desc)?;
			let decoded = engine.decode::<Vec<komorei::StreamInfo>>(ptr)?;
			Ok(decoded.into_iter().map(Into::into).collect())
		})
	}

	/// `get_stream(anime, episode, stream)` — resolved stream data for playback.
	pub fn stream(&self, anime: Anime, episode: Episode, stream: StreamInfo) -> Result<StreamData, RunnerError> {
		self.with_engine(|engine| {
			let anime_desc = engine.encode(&Into::<komorei::Anime>::into(anime))?;
			let episode_desc = engine.encode(&Into::<komorei::Episode>::into(episode))?;
			let stream_desc = engine.encode(&Into::<komorei::StreamInfo>::into(stream))?;
			let ptr = engine.call3("get_stream", anime_desc, episode_desc, stream_desc)?;
			let decoded = engine.decode::<komorei::StreamData>(ptr)?;
			Ok(decoded.into())
		})
	}

	/// `get_anime_list(listing, page)`.
	pub fn anime_list(&self, listing: Listing, page: i32) -> Result<AnimePageResult, RunnerError> {
		self.with_engine(|engine| {
			let listing_desc = engine.encode(&Into::<komorei::Listing>::into(listing))?;
			let ptr = engine.call2("get_anime_list", listing_desc, page)?;
			let decoded = engine.decode::<records::AnimePageResultBuf>(ptr)?;
			Ok(decoded.into())
		})
	}

	/// `get_home()` — the source's home layout.
	pub fn home(&self) -> Result<HomeLayout, RunnerError> {
		self.with_engine(|engine| {
			let ptr = engine.call0("get_home")?;
			let decoded = engine.decode::<komorei::HomeLayout>(ptr)?;
			Ok(decoded.into())
		})
	}

	/// `get_listings()` — the tabs/filters presented by the source.
	pub fn listings(&self) -> Result<Vec<Listing>, RunnerError> {
		self.with_engine(|engine| {
			let ptr = engine.call0("get_listings")?;
			let decoded = engine.decode::<Vec<komorei::Listing>>(ptr)?;
			Ok(decoded.into_iter().map(Into::into).collect())
		})
	}

	/// `get_filters()`.
	pub fn filters(&self) -> Result<Vec<Filter>, RunnerError> {
		self.with_engine(|engine| {
			let ptr = engine.call0("get_filters")?;
			// Filters have a variable-length tail (the kind payload), so the
			// postcard stream is walked field-by-field instead of serde-parsed.
			let payload = engine.decode_raw(ptr)?;
			let decoded = deser::decode_filters(&payload)?;
			Ok(decoded.into_iter().map(Into::into).collect())
		})
	}

	/// `get_settings()`.
	pub fn settings(&self) -> Result<Vec<Setting>, RunnerError> {
		self.with_engine(|engine| {
			let ptr = engine.call0("get_settings")?;
			// Settings have a variable-arity tail (SettingValue) with an
			// embedded var-arity PageIcon — decoded by stream walking.
			let payload = engine.decode_raw(ptr)?;
			let decoded = deser::decode_settings(&payload)?;
			Ok(decoded.into_iter().map(Into::into).collect())
		})
	}

	/// `get_base_url()`.
	pub fn base_url(&self) -> Result<String, RunnerError> {
		self.with_engine(|engine| {
			let ptr = engine.call0("get_base_url")?;
			engine.decode::<String>(ptr)
		})
	}

	/// `handle_notification(notification)`.
	pub fn notify(&self, notification: String) -> Result<(), RunnerError> {
		self.with_engine(|engine| {
			let desc = engine.encode(&notification)?;
			let result = engine.call1("handle_notification", desc)?;
			if result == 0 {
				Ok(())
			} else {
				Err(RunnerError::Source { code: result, message: String::new() })
			}
		})
	}

	/// `handle_deep_link(url)` — `None` when the URL is not recognized.
	pub fn deep_link(&self, url: String) -> Result<Option<DeepLinkResult>, RunnerError> {
		self.with_engine(|engine| {
			let desc = engine.encode(&url)?;
			let ptr = engine.call1("handle_deep_link", desc)?;
			let decoded = engine.decode::<Option<records::DeepLinkResultBuf>>(ptr)?;
			Ok(decoded.map(Into::into))
		})
	}

	/// `intercept_segment_url(stream_data, url)` — header injection happens in
	/// the media requests, so this resolves the *final* segment URL.
	pub fn intercept_segment_url(&self, stream_data: Option<StreamData>, url: String) -> Result<String, RunnerError> {
		self.with_engine(|engine| {
			let stream_desc = match stream_data {
				Some(data) => engine.encode(&Into::<komorei::StreamData>::into(data))?,
				None => 0,
			};
			let url_desc = engine.encode(&url)?;
			let ptr = engine.call2("intercept_segment_url", stream_desc, url_desc)?;
			engine.decode::<String>(ptr)
		})
	}

	/// `intercept_segment_data(stream_data, url, data)`.
	pub fn intercept_segment_data(&self, stream_data: Option<StreamData>, url: String, data: Vec<u8>) -> Result<Vec<u8>, RunnerError> {
		self.with_engine(|engine| {
			let stream_desc = match stream_data {
				Some(data) => engine.encode(&Into::<komorei::StreamData>::into(data))?,
				None => 0,
			};
			let url_desc = engine.encode(&url)?;
			let data_desc = engine.encode(&data)?;
			let ptr = engine.call3("intercept_segment_data", stream_desc, url_desc, data_desc)?;
			engine.decode::<Vec<u8>>(ptr)
		})
	}
}

impl KomoreiRunner {
	fn with_engine<T>(
		&self,
		f: impl FnOnce(&mut engine::EngineState) -> Result<T, RunnerError>,
	) -> Result<T, RunnerError> {
		let mut guard = self.state.lock().map_err(|_| RunnerError::Wasm("runner mutex poisoned".into()))?;
		match guard.as_mut() {
			Some(engine) => f(engine),
			None => Err(RunnerError::NotLoaded),
		}
	}
}