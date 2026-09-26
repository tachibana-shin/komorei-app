//! uniffi records / enums that mirror the SDK structs, plus conversions to and
//! from the `komorei` lib types.
//!
//! These are the Kotlin-facing value types. The lib types stay the canonical
//! form for postcard encoding against the wasm.

use std::collections::HashMap;

// ---------------------------------------------------------------------------
// Setting
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Setting {
	pub key: String,
	pub title: String,
	pub notification: Option<String>,
	pub requires: Option<String>,
	pub requires_false: Option<String>,
	pub refreshes: Option<Vec<String>>,
	pub value: SettingValue,
}

impl From<crate::deser::SettingBuf> for Setting {
	fn from(buf: crate::deser::SettingBuf) -> Self {
		Self {
			key: buf.key,
			title: buf.title,
			notification: buf.notification,
			requires: buf.requires,
			requires_false: buf.requires_false,
			refreshes: buf.refreshes,
			value: buf.value.into(),
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum SettingValue {
	Group {
		footer: Option<String>,
		items: Vec<Setting>,
	},
	Select {
		values: Vec<String>,
		titles: Option<Vec<String>>,
		auth_to_open: Option<bool>,
		default: Option<String>,
	},
	MultiSelect {
		values: Vec<String>,
		titles: Option<Vec<String>>,
		auth_to_open: Option<bool>,
		default: Option<Vec<String>>,
	},
	Toggle {
		subtitle: Option<String>,
		auth_to_disable: Option<bool>,
		default: bool,
	},
	Stepper {
		minimum_value: f64,
		maximum_value: f64,
		step_value: Option<f64>,
		default: Option<f64>,
	},
	Segment {
		options: Vec<String>,
		default: Option<i32>,
	},
	Text {
		placeholder: Option<String>,
		autocapitalization_type: Option<i32>,
		autocorrection_disabled: Option<bool>,
		keyboard_type: Option<i32>,
		return_key_type: Option<i32>,
		secure: Option<bool>,
		default: Option<String>,
	},
	Button,
	Link {
		url: String,
		external: Option<bool>,
	},
	Login {
		method: LoginMethod,
		url: Option<String>,
		url_key: Option<String>,
		logout_title: Option<String>,
		pkce: bool,
		token_url: Option<String>,
		callback_scheme: Option<String>,
		use_email: bool,
		local_storage_keys: Option<Vec<String>>,
		clear_cookies_on_log_out: bool,
	},
	Page {
		items: Vec<Setting>,
		inline_title: Option<bool>,
		auth_to_open: Option<bool>,
		icon: Option<PageIcon>,
		info: Option<String>,
	},
	EditableList {
		line_limit: Option<i32>,
		inline: bool,
		placeholder: Option<String>,
		default: Option<Vec<String>>,
	},
	Picker {
		values: Vec<String>,
		titles: Option<Vec<String>>,
		default: Option<String>,
	},
}

impl From<crate::deser::SettingValueBuf> for SettingValue {
	fn from(v: crate::deser::SettingValueBuf) -> Self {
		use crate::deser::SettingValueBuf as B;
		match v {
			B::Group { footer, items } => SettingValue::Group {
				footer,
				items: items.into_iter().map(Into::into).collect(),
			},
			B::Select {
				values,
				titles,
				auth_to_open,
				default,
			} => SettingValue::Select {
				values,
				titles,
				auth_to_open,
				default,
			},
			B::MultiSelect {
				values,
				titles,
				auth_to_open,
				default,
			} => SettingValue::MultiSelect {
				values,
				titles,
				auth_to_open,
				default,
			},
			B::Toggle {
				subtitle,
				auth_to_disable,
				default,
			} => SettingValue::Toggle {
				subtitle,
				auth_to_disable,
				default,
			},
			B::Stepper {
				minimum_value,
				maximum_value,
				step_value,
				default,
			} => SettingValue::Stepper {
				minimum_value,
				maximum_value,
				step_value,
				default,
			},
			B::Segment { options, default } => SettingValue::Segment { options, default },
			B::Text {
				placeholder,
				autocapitalization_type,
				autocorrection_disabled,
				keyboard_type,
				return_key_type,
				secure,
				default,
			} => SettingValue::Text {
				placeholder,
				autocapitalization_type,
				autocorrection_disabled,
				keyboard_type,
				return_key_type,
				secure,
				default,
			},
			B::Button => SettingValue::Button,
			B::Link { url, external } => SettingValue::Link { url, external },
			B::Login {
				method,
				url,
				url_key,
				logout_title,
				pkce,
				token_url,
				callback_scheme,
				use_email,
				local_storage_keys,
				clear_cookies_on_log_out,
			} => SettingValue::Login {
				method: match method.0.as_str() {
					"basic" => LoginMethod::Basic,
					"oauth" => LoginMethod::OAuth,
					_ => LoginMethod::Web,
				},
				url,
				url_key,
				logout_title,
				pkce,
				token_url,
				callback_scheme,
				use_email,
				local_storage_keys,
				clear_cookies_on_log_out,
			},
			B::Page {
				items,
				inline_title,
				auth_to_open,
				icon,
				info,
			} => SettingValue::Page {
				items: items.into_iter().map(Into::into).collect(),
				inline_title,
				auth_to_open,
				icon: icon.map(|i| match i {
					crate::deser::PageIconBuf::System { name, color, inset } => {
						PageIcon::System { name, color, inset }
					}
					crate::deser::PageIconBuf::Url(url) => PageIcon::Url(url),
				}),
				info,
			},
			B::EditableList {
				line_limit,
				inline,
				placeholder,
				default,
			} => SettingValue::EditableList {
				line_limit,
				inline,
				placeholder,
				default,
			},
			B::Picker {
				values,
				titles,
				default,
			} => SettingValue::Picker {
				values,
				titles,
				default,
			},
		}
	}
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Link {
	pub title: String,
	pub subtitle: Option<String>,
	pub image_url: Option<String>,
	pub value: Option<LinkValue>,
}

impl From<komorei::Link> for Link {
	fn from(v: komorei::Link) -> Self {
		Self {
			title: v.title,
			subtitle: v.subtitle,
			image_url: v.image_url,
			value: v.value.map(Into::into),
		}
	}
}

// `Anime` is an order of magnitude larger than the sibling payloads, so this
// enum is ~560 bytes wide and gets moved around on every decode. Boxing `Anime`
// would trade one heap allocation per decoded link for a smaller value; that is
// only worth paying once someone measures how often links are decoded (they are
// built once per listing and then held for the whole screen), so the lint is
// parked here rather than silenced globally. Note the shape is also pinned by
// the uniffi bindings and the source-side JSON model.
#[allow(clippy::large_enum_variant)]
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum LinkValue {
	Url(String),
	Listing(Listing),
	Anime(Anime),
}

impl From<komorei::LinkValue> for LinkValue {
	fn from(v: komorei::LinkValue) -> Self {
		match v {
			komorei::LinkValue::Url(url) => LinkValue::Url(url),
			komorei::LinkValue::Listing(l) => LinkValue::Listing(l.into()),
			komorei::LinkValue::Anime(a) => LinkValue::Anime(a.into()),
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct FilterItem {
	pub title: String,
	pub values: Option<Vec<FilterValue>>,
}

impl From<komorei::FilterItem> for FilterItem {
	fn from(v: komorei::FilterItem) -> Self {
		Self {
			title: v.title,
			values: v
				.values
				.map(|vals| vals.into_iter().map(Into::into).collect()),
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AnimeWithEpisode {
	pub anime: Anime,
	pub episode: Episode,
}

impl From<komorei::AnimeWithEpisode> for AnimeWithEpisode {
	fn from(v: komorei::AnimeWithEpisode) -> Self {
		Self {
			anime: v.anime.into(),
			episode: v.episode.into(),
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct HomeComponent {
	pub title: Option<String>,
	pub subtitle: Option<String>,
	pub value: HomeComponentValue,
}

impl From<komorei::HomeComponent> for HomeComponent {
	fn from(v: komorei::HomeComponent) -> Self {
		Self {
			title: v.title,
			subtitle: v.subtitle,
			value: v.value.into(),
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum HomeComponentValue {
	ImageScroller {
		links: Vec<Link>,
		auto_scroll_interval: Option<f32>,
		width: Option<i32>,
		height: Option<i32>,
	},
	BigScroller {
		entries: Vec<Anime>,
		auto_scroll_interval: Option<f32>,
	},
	Scroller {
		entries: Vec<Link>,
		listing: Option<Listing>,
	},
	AnimeList {
		ranking: bool,
		page_size: Option<i32>,
		entries: Vec<Link>,
		listing: Option<Listing>,
	},
	AnimeEpisodeList {
		page_size: Option<i32>,
		entries: Vec<AnimeWithEpisode>,
		listing: Option<Listing>,
	},
	Filters(Vec<FilterItem>),
	Links(Vec<Link>),
}

impl From<komorei::HomeComponentValue> for HomeComponentValue {
	fn from(v: komorei::HomeComponentValue) -> Self {
		use komorei::HomeComponentValue as L;
		match v {
			L::ImageScroller {
				links,
				auto_scroll_interval,
				width,
				height,
			} => HomeComponentValue::ImageScroller {
				links: links.into_iter().map(Into::into).collect(),
				auto_scroll_interval,
				width,
				height,
			},
			L::BigScroller {
				entries,
				auto_scroll_interval,
			} => HomeComponentValue::BigScroller {
				entries: entries.into_iter().map(Into::into).collect(),
				auto_scroll_interval,
			},
			L::Scroller { entries, listing } => HomeComponentValue::Scroller {
				entries: entries.into_iter().map(Into::into).collect(),
				listing: listing.map(Into::into),
			},
			L::AnimeList {
				ranking,
				page_size,
				entries,
				listing,
			} => HomeComponentValue::AnimeList {
				ranking,
				page_size,
				entries: entries.into_iter().map(Into::into).collect(),
				listing: listing.map(Into::into),
			},
			L::AnimeEpisodeList {
				page_size,
				entries,
				listing,
			} => HomeComponentValue::AnimeEpisodeList {
				page_size,
				entries: entries.into_iter().map(Into::into).collect(),
				listing: listing.map(Into::into),
			},
			L::Filters(items) => {
				HomeComponentValue::Filters(items.into_iter().map(Into::into).collect())
			}
			L::Links(links) => {
				HomeComponentValue::Links(links.into_iter().map(Into::into).collect())
			}
		}
	}
}

#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct HomeLayout {
	pub components: Vec<HomeComponent>,
}

impl From<komorei::HomeLayout> for HomeLayout {
	fn from(v: komorei::HomeLayout) -> Self {
		Self {
			components: v.components.into_iter().map(Into::into).collect(),
		}
	}
}

/// One chunk of a home layout a source streamed out of `get_home` via
/// `send_partial_result`.
///
/// A source whose home needs several requests can send the first rails as soon
/// as they land instead of making the user wait for the slowest one; see
/// [`KomoreiHost::partial_home`](crate::host::KomoreiHost::partial_home).
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum HomePartialResult {
	/// A whole layout — used for the opening placeholder, so the app can show
	/// the right section skeletons before any real content exists.
	Layout(HomeLayout),
	/// One more section, appended to whatever has arrived so far.
	Component(HomeComponent),
}

impl From<komorei::HomePartialResult> for HomePartialResult {
	fn from(v: komorei::HomePartialResult) -> Self {
		match v {
			komorei::HomePartialResult::Layout(layout) => Self::Layout(layout.into()),
			komorei::HomePartialResult::Component(component) => Self::Component(component.into()),
		}
	}
}

// ---------------------------------------------------------------------------
// Deep links
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum DeepLinkResult {
	Anime { key: String },
	Episode { anime_key: String, key: String },
	Listing(Listing),
}

impl From<DeepLinkResultBuf> for DeepLinkResult {
	fn from(v: DeepLinkResultBuf) -> Self {
		match (v.anime_key, v.episode_key, v.listing) {
			(Some(key), None, None) => DeepLinkResult::Anime { key },
			(Some(anime_key), Some(key), None) => DeepLinkResult::Episode { anime_key, key },
			(None, None, Some(listing)) => DeepLinkResult::Listing(listing.into()),
			_ => DeepLinkResult::Anime { key: String::new() },
		}
	}
}

/// The lib `DeepLinkResult` is hand-serialized as a flat struct with three
/// optional fields; this mirror decodes it positionally.
#[derive(Debug, Clone, Default, PartialEq, serde::Deserialize)]
pub struct DeepLinkResultBuf {
	pub anime_key: Option<String>,
	pub episode_key: Option<String>,
	pub listing: Option<komorei::Listing>,
}

// ---------------------------------------------------------------------------
// Status / season / links
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, uniffi::Enum)]
pub enum AnimeStatus {
	#[default]
	Unknown,
	Ongoing,
	Completed,
	Cancelled,
	Hiatus,
}

impl From<komorei::AnimeStatus> for AnimeStatus {
	fn from(v: komorei::AnimeStatus) -> Self {
		use komorei::AnimeStatus as L;
		match v {
			L::Unknown => AnimeStatus::Unknown,
			L::Ongoing => AnimeStatus::Ongoing,
			L::Completed => AnimeStatus::Completed,
			L::Cancelled => AnimeStatus::Cancelled,
			L::Hiatus => AnimeStatus::Hiatus,
		}
	}
}

impl From<AnimeStatus> for komorei::AnimeStatus {
	fn from(v: AnimeStatus) -> Self {
		use komorei::AnimeStatus as L;
		match v {
			AnimeStatus::Unknown => L::Unknown,
			AnimeStatus::Ongoing => L::Ongoing,
			AnimeStatus::Completed => L::Completed,
			AnimeStatus::Cancelled => L::Cancelled,
			AnimeStatus::Hiatus => L::Hiatus,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct AnimeSeason {
	pub anime_id: String,
	pub title: String,
	pub id: String,
}

impl From<komorei::AnimeSeason> for AnimeSeason {
	fn from(v: komorei::AnimeSeason) -> Self {
		Self {
			anime_id: v.anime_id,
			title: v.title,
			id: v.id,
		}
	}
}

impl From<AnimeSeason> for komorei::AnimeSeason {
	fn from(v: AnimeSeason) -> Self {
		Self {
			anime_id: v.anime_id,
			title: v.title,
			id: v.id,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct CategoryLink {
	pub name: String,
	pub filters: Vec<FilterValue>,
}

impl From<komorei::CategoryLink> for CategoryLink {
	fn from(v: komorei::CategoryLink) -> Self {
		Self {
			name: v.name,
			filters: v.filters.into_iter().map(Into::into).collect(),
		}
	}
}

impl From<CategoryLink> for komorei::CategoryLink {
	fn from(v: CategoryLink) -> Self {
		Self {
			name: v.name,
			filters: v.filters.into_iter().map(Into::into).collect(),
		}
	}
}

// ---------------------------------------------------------------------------
// Filters
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum FilterKind {
	Text {
		placeholder: Option<String>,
	},
	Sort {
		can_ascend: bool,
		options: Vec<String>,
		default: Option<SortFilterDefault>,
	},
	Check {
		name: Option<String>,
		can_exclude: bool,
		default: Option<bool>,
	},
	Select {
		is_genre: bool,
		uses_tag_style: bool,
		options: Vec<String>,
		ids: Option<Vec<String>>,
		default: Option<String>,
	},
	MultiSelect {
		is_genre: bool,
		can_exclude: bool,
		uses_tag_style: bool,
		options: Vec<String>,
		ids: Option<Vec<String>>,
		default_included: Option<Vec<String>>,
		default_excluded: Option<Vec<String>>,
	},
	Note(String),
	Range {
		min: Option<f32>,
		max: Option<f32>,
		decimal: bool,
	},
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Filter {
	pub id: String,
	pub title: Option<String>,
	pub hide_from_header: Option<bool>,
	pub kind: FilterKind,
}

impl From<crate::deser::FilterBuf> for Filter {
	fn from(buf: crate::deser::FilterBuf) -> Self {
		let kind = match buf.kind {
			crate::deser::FilterKindBuf::Text { placeholder } => FilterKind::Text { placeholder },
			crate::deser::FilterKindBuf::Sort {
				can_ascend,
				options,
				default,
			} => FilterKind::Sort {
				can_ascend,
				options,
				default: default.map(Into::into),
			},
			crate::deser::FilterKindBuf::Check {
				name,
				can_exclude,
				default,
			} => FilterKind::Check {
				name,
				can_exclude,
				default,
			},
			crate::deser::FilterKindBuf::Select {
				is_genre,
				uses_tag_style,
				options,
				ids,
				default,
			} => FilterKind::Select {
				is_genre,
				uses_tag_style,
				options,
				ids,
				default,
			},
			crate::deser::FilterKindBuf::MultiSelect {
				is_genre,
				can_exclude,
				uses_tag_style,
				options,
				ids,
				default_included,
				default_excluded,
			} => FilterKind::MultiSelect {
				is_genre,
				can_exclude,
				uses_tag_style,
				options,
				ids,
				default_included,
				default_excluded,
			},
			crate::deser::FilterKindBuf::Note(text) => FilterKind::Note(text),
			crate::deser::FilterKindBuf::Range { min, max, decimal } => {
				FilterKind::Range { min, max, decimal }
			}
		};
		Filter {
			id: buf.id.unwrap_or_default(),
			title: buf.title,
			hide_from_header: buf.hide_from_header,
			kind,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct SortFilterDefault {
	pub index: i32,
	pub ascending: bool,
}

impl From<komorei::SortFilterDefault> for SortFilterDefault {
	fn from(v: komorei::SortFilterDefault) -> Self {
		Self {
			index: v.index,
			ascending: v.ascending,
		}
	}
}

/// A configured search filter value.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum FilterValue {
	Text {
		id: String,
		value: String,
	},
	Sort {
		id: String,
		index: i32,
		ascending: bool,
	},
	Check {
		id: String,
		value: i32,
	},
	Select {
		id: String,
		value: String,
	},
	MultiSelect {
		id: String,
		included: Vec<String>,
		excluded: Vec<String>,
	},
	Range {
		id: String,
		from: Option<f32>,
		to: Option<f32>,
	},
}

impl From<komorei::FilterValue> for FilterValue {
	fn from(v: komorei::FilterValue) -> Self {
		use komorei::FilterValue as L;
		match v {
			L::Text { id, value } => FilterValue::Text { id, value },
			L::Sort {
				id,
				index,
				ascending,
			} => FilterValue::Sort {
				id,
				index,
				ascending,
			},
			L::Check { id, value } => FilterValue::Check { id, value },
			L::Select { id, value } => FilterValue::Select { id, value },
			L::MultiSelect {
				id,
				included,
				excluded,
			} => FilterValue::MultiSelect {
				id,
				included,
				excluded,
			},
			L::Range { id, from, to } => FilterValue::Range { id, from, to },
		}
	}
}

impl From<FilterValue> for komorei::FilterValue {
	fn from(v: FilterValue) -> Self {
		use komorei::FilterValue as L;
		match v {
			FilterValue::Text { id, value } => L::Text { id, value },
			FilterValue::Sort {
				id,
				index,
				ascending,
			} => L::Sort {
				id,
				index,
				ascending,
			},
			FilterValue::Check { id, value } => L::Check { id, value },
			FilterValue::Select { id, value } => L::Select { id, value },
			FilterValue::MultiSelect {
				id,
				included,
				excluded,
			} => L::MultiSelect {
				id,
				included,
				excluded,
			},
			FilterValue::Range { id, from, to } => L::Range { id, from, to },
		}
	}
}

// ---------------------------------------------------------------------------
// Anime
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct Anime {
	pub key: String,
	pub source_id: String,
	pub title: String,
	pub original_title: String,
	pub cover: String,
	pub banner: Option<String>,
	pub description: Option<String>,
	pub episode_count: i32,
	pub current_episode: Option<String>,
	pub rating: Option<f32>,
	pub rating_count: Option<i32>,
	pub status: AnimeStatus,
	pub release_year: Option<CategoryLink>,
	pub genres: Vec<CategoryLink>,
	pub authors: Vec<CategoryLink>,
	pub studio: Option<CategoryLink>,
	pub season_of: Option<CategoryLink>,
	pub countries: Vec<CategoryLink>,
	pub is_featured: bool,
	pub views: i32,
	pub next_episode_air_info: Option<String>,
	pub quality_tag: Option<String>,
	pub seasons: Vec<AnimeSeason>,
	pub episodes: Option<Vec<Episode>>,
	pub url: Option<String>,
	/// Source-defined extras (see [`komorei::Anime::extra`]) — a flat map so no
	/// source can widen the ABI by choosing a key.
	pub extra: HashMap<String, String>,
}

impl From<komorei::Anime> for Anime {
	fn from(v: komorei::Anime) -> Self {
		Self {
			key: v.key,
			source_id: v.source_id,
			title: v.title,
			original_title: v.original_title,
			cover: v.cover,
			banner: v.banner,
			description: v.description,
			episode_count: v.episode_count,
			current_episode: v.current_episode,
			rating: v.rating,
			rating_count: v.rating_count,
			status: v.status.into(),
			release_year: v.release_year.map(Into::into),
			genres: v.genres.into_iter().map(Into::into).collect(),
			authors: v.authors.into_iter().map(Into::into).collect(),
			studio: v.studio.map(Into::into),
			season_of: v.season_of.map(Into::into),
			countries: v.countries.into_iter().map(Into::into).collect(),
			is_featured: v.is_featured,
			views: v.views,
			next_episode_air_info: v.next_episode_air_info,
			quality_tag: v.quality_tag,
			seasons: v.seasons.into_iter().map(Into::into).collect(),
			episodes: v
				.episodes
				.map(|eps| eps.into_iter().map(Into::into).collect()),
			url: v.url,
			// `komorei::HashMap` is hashbrown's, not std's.
			extra: v.extra.into_iter().collect(),
		}
	}
}

impl From<Anime> for komorei::Anime {
	fn from(v: Anime) -> Self {
		Self {
			key: v.key,
			source_id: v.source_id,
			title: v.title,
			original_title: v.original_title,
			cover: v.cover,
			banner: v.banner,
			description: v.description,
			episode_count: v.episode_count,
			current_episode: v.current_episode,
			rating: v.rating,
			rating_count: v.rating_count,
			status: v.status.into(),
			release_year: v.release_year.map(Into::into),
			genres: v.genres.into_iter().map(Into::into).collect(),
			authors: v.authors.into_iter().map(Into::into).collect(),
			studio: v.studio.map(Into::into),
			season_of: v.season_of.map(Into::into),
			countries: v.countries.into_iter().map(Into::into).collect(),
			is_featured: v.is_featured,
			views: v.views,
			next_episode_air_info: v.next_episode_air_info,
			quality_tag: v.quality_tag,
			seasons: v.seasons.into_iter().map(Into::into).collect(),
			episodes: v
				.episodes
				.map(|eps| eps.into_iter().map(Into::into).collect()),
			url: v.url,
			// `komorei::HashMap` is hashbrown's, not std's.
			extra: v.extra.into_iter().collect(),
		}
	}
}

/// The lib `AnimePageResult` is `Serialize`-only; this mirror decodes the
/// positional field order (entries, then has_next_page).
#[derive(Debug, Clone, Default, PartialEq, serde::Deserialize)]
pub struct AnimePageResultBuf {
	pub entries: Vec<komorei::Anime>,
	pub has_next_page: bool,
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct AnimePageResult {
	pub entries: Vec<Anime>,
	pub has_next_page: bool,
}

impl From<AnimePageResultBuf> for AnimePageResult {
	fn from(v: AnimePageResultBuf) -> Self {
		Self {
			entries: v.entries.into_iter().map(Into::into).collect(),
			has_next_page: v.has_next_page,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct Episode {
	pub key: String,
	pub episode_number: String,
	pub title: Option<String>,
	pub date_uploaded: Option<i64>,
	pub thumbnail: Option<String>,
	pub quality: Option<String>,
	pub duration_seconds: Option<i64>,
	pub url: Option<String>,
	pub language: Option<String>,
	pub locked: bool,
}

impl From<komorei::Episode> for Episode {
	fn from(v: komorei::Episode) -> Self {
		Self {
			key: v.key,
			episode_number: v.episode_number,
			title: v.title,
			date_uploaded: v.date_uploaded,
			thumbnail: v.thumbnail,
			quality: v.quality,
			duration_seconds: v.duration_seconds,
			url: v.url,
			language: v.language,
			locked: v.locked,
		}
	}
}

impl From<Episode> for komorei::Episode {
	fn from(v: Episode) -> Self {
		Self {
			key: v.key,
			episode_number: v.episode_number,
			title: v.title,
			date_uploaded: v.date_uploaded,
			thumbnail: v.thumbnail,
			quality: v.quality,
			duration_seconds: v.duration_seconds,
			url: v.url,
			language: v.language,
			locked: v.locked,
		}
	}
}

// ---------------------------------------------------------------------------
// Listings
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, uniffi::Enum)]
pub enum ListingKind {
	#[default]
	Default,
	List,
}

impl From<komorei::ListingKind> for ListingKind {
	fn from(v: komorei::ListingKind) -> Self {
		match v {
			komorei::ListingKind::Default => ListingKind::Default,
			komorei::ListingKind::List => ListingKind::List,
		}
	}
}

impl From<ListingKind> for komorei::ListingKind {
	fn from(v: ListingKind) -> Self {
		match v {
			ListingKind::Default => komorei::ListingKind::Default,
			ListingKind::List => komorei::ListingKind::List,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct Listing {
	pub id: String,
	pub name: String,
	pub kind: ListingKind,
}

impl From<komorei::Listing> for Listing {
	fn from(v: komorei::Listing) -> Self {
		Self {
			id: v.id,
			name: v.name,
			kind: v.kind.into(),
		}
	}
}

impl From<Listing> for komorei::Listing {
	fn from(v: Listing) -> Self {
		Self {
			id: v.id,
			name: v.name,
			kind: v.kind.into(),
		}
	}
}

// ---------------------------------------------------------------------------
// Streams
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, uniffi::Enum)]
pub enum StreamType {
	#[default]
	HLS,
	MP4,
	DASH,
	OTHER,
}

impl From<komorei::StreamType> for StreamType {
	fn from(v: komorei::StreamType) -> Self {
		use komorei::StreamType as L;
		match v {
			L::HLS => StreamType::HLS,
			L::MP4 => StreamType::MP4,
			L::DASH => StreamType::DASH,
			L::OTHER => StreamType::OTHER,
		}
	}
}

impl From<StreamType> for komorei::StreamType {
	fn from(v: StreamType) -> Self {
		use komorei::StreamType as L;
		match v {
			StreamType::HLS => L::HLS,
			StreamType::MP4 => L::MP4,
			StreamType::DASH => L::DASH,
			StreamType::OTHER => L::OTHER,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct RangeLong {
	pub start_ms: i64,
	pub end_ms: i64,
}

impl From<komorei::RangeLong> for RangeLong {
	fn from(v: komorei::RangeLong) -> Self {
		Self {
			start_ms: v.start_ms,
			end_ms: v.end_ms,
		}
	}
}

impl From<RangeLong> for komorei::RangeLong {
	fn from(v: RangeLong) -> Self {
		Self {
			start_ms: v.start_ms,
			end_ms: v.end_ms,
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SubtitleInfo {
	pub url: String,
	pub language: String,
	pub label: Option<String>,
	pub headers: HashMap<String, String>,
}

impl From<komorei::SubtitleInfo> for SubtitleInfo {
	fn from(v: komorei::SubtitleInfo) -> Self {
		Self {
			url: v.url,
			language: v.language,
			label: v.label,
			headers: v.headers.into_iter().collect(),
		}
	}
}

impl From<SubtitleInfo> for komorei::SubtitleInfo {
	fn from(v: SubtitleInfo) -> Self {
		Self {
			url: v.url,
			language: v.language,
			label: v.label,
			headers: v.headers.into_iter().collect(),
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct StreamInfo {
	pub key: String,
	pub name: String,
	pub quality: String,
}

impl From<komorei::StreamInfo> for StreamInfo {
	fn from(v: komorei::StreamInfo) -> Self {
		Self {
			key: v.key,
			name: v.name,
			quality: v.quality,
		}
	}
}

impl From<StreamInfo> for komorei::StreamInfo {
	fn from(v: StreamInfo) -> Self {
		Self {
			key: v.key,
			name: v.name,
			quality: v.quality,
		}
	}
}

#[derive(Debug, Clone, PartialEq, Default, uniffi::Record)]
pub struct StreamData {
	pub url: String,
	pub stream_type: StreamType,
	pub is_content: bool,
	pub headers: HashMap<String, String>,
	pub subtitles: Vec<SubtitleInfo>,
	pub intro: Option<RangeLong>,
	pub outro: Option<RangeLong>,
}

impl From<komorei::StreamData> for StreamData {
	fn from(v: komorei::StreamData) -> Self {
		Self {
			url: v.url,
			stream_type: v.stream_type.into(),
			is_content: v.is_content,
			headers: v.headers.into_iter().collect(),
			subtitles: v.subtitles.into_iter().map(Into::into).collect(),
			intro: v.intro.map(Into::into),
			outro: v.outro.map(Into::into),
		}
	}
}

impl From<StreamData> for komorei::StreamData {
	fn from(v: StreamData) -> Self {
		Self {
			url: v.url,
			stream_type: v.stream_type.into(),
			is_content: v.is_content,
			headers: v.headers.into_iter().collect(),
			subtitles: v.subtitles.into_iter().map(Into::into).collect(),
			intro: v.intro.map(Into::into),
			outro: v.outro.map(Into::into),
		}
	}
}

// ---------------------------------------------------------------------------
// Settings support types
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum LoginMethod {
	Basic,
	OAuth,
	Web,
}

impl From<crate::deser::LoginMethodBuf> for LoginMethod {
	fn from(v: crate::deser::LoginMethodBuf) -> Self {
		match v.0.as_str() {
			"basic" => LoginMethod::Basic,
			"oauth" => LoginMethod::OAuth,
			_ => LoginMethod::Web,
		}
	}
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum PageIcon {
	System {
		name: String,
		color: String,
		inset: Option<i32>,
	},
	Url(String),
}
