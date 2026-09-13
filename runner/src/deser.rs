//! Custom postcard de-serialization for SDK types that only implement
//! `Serialize` on the lib side (`Filter`, `Setting` and friends).
//!
//! Postcard serializes structs positionally, so the mirrors below must read
//! fields in exactly the order the lib's `Serialize` impls emit them. See
//! `crates/lib/src/structs/{filter,setting}.rs`.

use std::borrow::Cow;

use komorei::{LoginMethod, SortFilterDefault};
use crate::error::RunnerError;

// ---------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, PartialEq)]
pub enum FilterKindBuf {
	Text { placeholder: Option<String> },
	Sort { can_ascend: bool, options: Vec<String>, default: Option<SortFilterDefault> },
	Check { name: Option<String>, can_exclude: bool, default: Option<bool> },
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
	Range { min: Option<f32>, max: Option<f32>, decimal: bool },
}

#[derive(Debug, Clone, PartialEq)]
pub struct FilterBuf {
	pub id: Option<String>,
	pub title: Option<String>,
	pub hide_from_header: Option<bool>,
	pub kind: FilterKindBuf,
}

impl FilterBuf {
	pub fn into_komorei(self) -> komorei::Filter {
		let kind = match self.kind {
			FilterKindBuf::Text { placeholder } => {
				komorei::FilterKind::Text { placeholder: placeholder.map(Cow::Owned) }
			}
			FilterKindBuf::Sort { can_ascend, options, default } => komorei::FilterKind::Sort {
				can_ascend,
				options: options.into_iter().map(Cow::Owned).collect(),
				default,
			},
			FilterKindBuf::Check { name, can_exclude, default } => komorei::FilterKind::Check {
				name: name.map(Cow::Owned),
				can_exclude,
				default,
			},
			FilterKindBuf::Select { is_genre, uses_tag_style, options, ids, default } => {
				komorei::FilterKind::Select {
					is_genre,
					uses_tag_style,
					options: options.into_iter().map(Cow::Owned).collect(),
					ids: ids.map(|v| v.into_iter().map(Cow::Owned).collect()),
					default: default.map(Cow::Owned),
				}
			}
			FilterKindBuf::MultiSelect {
				is_genre,
				can_exclude,
				uses_tag_style,
				options,
				ids,
				default_included,
				default_excluded,
			} => komorei::FilterKind::MultiSelect {
				is_genre,
				can_exclude,
				uses_tag_style,
				options: options.into_iter().map(Cow::Owned).collect(),
				ids: ids.map(|v| v.into_iter().map(Cow::Owned).collect()),
				default_included: default_included.map(|v| v.into_iter().map(Cow::Owned).collect()),
				default_excluded: default_excluded.map(|v| v.into_iter().map(Cow::Owned).collect()),
			},
			FilterKindBuf::Note(text) => komorei::FilterKind::Note(Cow::Owned(text)),
			FilterKindBuf::Range { min, max, decimal } => komorei::FilterKind::Range { min, max, decimal },
		};
		komorei::Filter {
			id: Cow::Owned(self.id.unwrap_or_default()),
			title: self.title.map(Cow::Owned),
			hide_from_header: self.hide_from_header,
			kind,
		}
	}
}

/// Decodes a postcard `Vec<Filter>` produced by the lib's hand-rolled
/// `Serialize` impl.
///
/// Postcard cannot drive this through a serde `Deserialize`: its struct access
/// is bounded by the declared field count, while a Filter has a *variable*
/// tail of kind-specific fields. Instead the stream is walked field by field
/// with `take_from_bytes` in exactly the order the lib emits them
/// (`crates/lib/src/structs/filter.rs`): id, title, hide_from_header, type,
/// then the kind payload.
pub fn decode_filters<'a>(payload: &'a [u8]) -> Result<Vec<FilterBuf>, RunnerError> {
	let (count, mut rest) = postcard::take_from_bytes::<usize>(payload).map_err(RunnerError::from)?;
	let mut out = Vec::with_capacity(count);
	for _ in 0..count {
		let (filter, remainder) = decode_one_filter(rest)?;
		rest = remainder;
		out.push(filter);
	}
	Ok(out)
}

fn decode_one_filter<'a>(mut rest: &'a [u8]) -> Result<(FilterBuf, &'a [u8]), RunnerError> {
	macro_rules! take {
		($t:ty) => {{
			let (value, remainder) = postcard::take_from_bytes::<$t>(rest).map_err(RunnerError::from)?;
			rest = remainder;
			value
		}};
	}

	let id: Option<String> = take!(Option<String>);
	let title: Option<String> = take!(Option<String>);
	let hide_from_header: Option<bool> = take!(Option<bool>);
	let ty: String = take!(String);

	let kind = match ty.as_str() {
		"text" => {
			let placeholder: Option<String> = take!(Option<String>);
			FilterKindBuf::Text { placeholder }
		}
		"sort" => {
			let can_ascend: Option<bool> = take!(Option<bool>);
			let options: Vec<String> = take!(Vec<String>);
			let default: Option<SortFilterDefault> = take!(Option<SortFilterDefault>);
			FilterKindBuf::Sort {
				can_ascend: can_ascend.unwrap_or(true),
				options,
				default,
			}
		}
		"check" => {
			let name: Option<String> = take!(Option<String>);
			let can_exclude: Option<bool> = take!(Option<bool>);
			let default: Option<bool> = take!(Option<bool>);
			FilterKindBuf::Check {
				name,
				can_exclude: can_exclude.unwrap_or(false),
				default,
			}
		}
		"select" => {
			let is_genre: Option<bool> = take!(Option<bool>);
			let uses_tag_style: Option<bool> = take!(Option<bool>);
			let options: Vec<String> = take!(Vec<String>);
			let ids: Option<Vec<String>> = take!(Option<Vec<String>>);
			let default: Option<String> = take!(Option<String>);
			FilterKindBuf::Select {
				is_genre: is_genre.unwrap_or(false),
				uses_tag_style: uses_tag_style.unwrap_or(false),
				options,
				ids,
				default,
			}
		}
		"multi-select" => {
			let is_genre: Option<bool> = take!(Option<bool>);
			let can_exclude: Option<bool> = take!(Option<bool>);
			let uses_tag_style: Option<bool> = take!(Option<bool>);
			let options: Vec<String> = take!(Vec<String>);
			let ids: Option<Vec<String>> = take!(Option<Vec<String>>);
			let default_included: Option<Vec<String>> = take!(Option<Vec<String>>);
			let default_excluded: Option<Vec<String>> = take!(Option<Vec<String>>);
			FilterKindBuf::MultiSelect {
				is_genre: is_genre.unwrap_or(false),
				can_exclude: can_exclude.unwrap_or(false),
				uses_tag_style: uses_tag_style.unwrap_or(false),
				options,
				ids,
				default_included,
				default_excluded,
			}
		}
		"note" => {
			let text: String = take!(String);
			FilterKindBuf::Note(text)
		}
		"range" => {
			let min: Option<f32> = take!(Option<f32>);
			let max: Option<f32> = take!(Option<f32>);
			let decimal: Option<bool> = take!(Option<bool>);
			FilterKindBuf::Range {
				min,
				max,
				decimal: decimal.unwrap_or(false),
			}
		}
		other => return Err(RunnerError::serde(format!("unknown filter type: {other}"))),
	};

	Ok((FilterBuf { id, title, hide_from_header, kind }, rest))
}

// ---------------------------------------------------------------------------
// Setting
// ---------------------------------------------------------------------------

/// Mirrors `Setting`, whose lib `Serialize` emits fields in this exact order:
/// type, key, title, notification, requires, requires_false, refreshes, value.
#[derive(Debug, Clone, PartialEq)]
pub struct SettingBuf {
	pub key: String,
	pub title: String,
	pub notification: Option<String>,
	pub requires: Option<String>,
	pub requires_false: Option<String>,
	pub refreshes: Option<Vec<String>>,
	pub value: SettingValueBuf,
}

/// Mirrors `SettingValue`. The variant *order and field order* must match the
/// lib's declaration exactly because postcard encodes derive-enums by index
/// and derives structs positionally (see `decode_value`). Note the lib's
/// `SettingValue::Text` order differs from the `TextSetting` struct order —
/// the variant wins.
#[derive(Debug, Clone, PartialEq)]
pub enum SettingValueBuf {
	Group { footer: Option<String>, items: Vec<SettingBuf> },
	Select { values: Vec<String>, titles: Option<Vec<String>>, auth_to_open: Option<bool>, default: Option<String> },
	MultiSelect { values: Vec<String>, titles: Option<Vec<String>>, auth_to_open: Option<bool>, default: Option<Vec<String>> },
	Toggle { subtitle: Option<String>, auth_to_disable: Option<bool>, default: bool },
	Stepper { minimum_value: f64, maximum_value: f64, step_value: Option<f64>, default: Option<f64> },
	Segment { options: Vec<String>, default: Option<i32> },
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
	Link { url: String, external: Option<bool> },
	Login {
		method: LoginMethodBuf,
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
		items: Vec<SettingBuf>,
		inline_title: Option<bool>,
		auth_to_open: Option<bool>,
		icon: Option<PageIconBuf>,
		info: Option<String>,
	},
	EditableList { line_limit: Option<i32>, inline: bool, placeholder: Option<String>, default: Option<Vec<String>> },
	Picker { values: Vec<String>, titles: Option<Vec<String>>, default: Option<String> },
}

/// `LoginMethod` serializes as a plain string ("basic" | "oauth" | "web").
#[derive(Debug, Clone, PartialEq)]
pub struct LoginMethodBuf(pub String);

impl LoginMethodBuf {
	pub fn into_komorei(self) -> LoginMethod {
		match self.0.as_str() {
			"basic" => LoginMethod::Basic,
			"oauth" => LoginMethod::OAuth,
			_ => LoginMethod::Web,
		}
	}
}

/// `PageIcon` serializes as a flat struct with a leading "type" string
/// ("system" | "url"): `[type][name][color][inset]` or `[type][url]`. Postcard
/// can't drive a variable-arity struct through serde, so it is walked directly
/// by `decode_page_icon` below.
#[derive(Debug, Clone, PartialEq)]
pub enum PageIconBuf {
	System { name: String, color: String, inset: Option<i32> },
	Url(String),
}

/// Decodes a postcard `Vec<Setting>` produced by the lib's hand-rolled
/// `Serialize` impl. Like filters, a Setting ends in a variable-arity tail
/// (the `SettingValue`), and the `Page` variant embeds a variable-arity
/// `PageIcon` — so the stream is walked field by field with `take_from_bytes`
/// in exactly the order `crates/lib/src/structs/setting.rs` emits them.
pub fn decode_settings<'a>(payload: &'a [u8]) -> Result<Vec<SettingBuf>, RunnerError> {
	let (count, mut rest) = postcard::take_from_bytes::<usize>(payload).map_err(RunnerError::from)?;
	let mut out = Vec::with_capacity(count);
	for _ in 0..count {
		let (setting, remainder) = decode_one_setting(rest)?;
		rest = remainder;
		out.push(setting);
	}
	Ok(out)
}

fn decode_one_setting<'a>(mut rest: &'a [u8]) -> Result<(SettingBuf, &'a [u8]), RunnerError> {
	macro_rules! take {
		($t:ty) => {{
			let (value, remainder) = postcard::take_from_bytes::<$t>(rest).map_err(RunnerError::from)?;
			rest = remainder;
			value
		}};
	}

	// the leading "type" string names the variant; the fields are positional
	let _ty: String = take!(String);
	let key: String = take!(String);
	let title: String = take!(String);
	let notification: Option<String> = take!(Option<String>);
	let requires: Option<String> = take!(Option<String>);
	let requires_false: Option<String> = take!(Option<String>);
	let refreshes: Option<Vec<String>> = take!(Option<Vec<String>>);
	let (value, remainder) = decode_value(rest)?;
	rest = remainder;

	Ok((
		SettingBuf { key, title, notification, requires, requires_false, refreshes, value },
		rest,
	))
}

/// Walks one `SettingValue`. The lib derives `Serialize` for the enum, so the
/// wire is `[u32 variant index][variant fields]` in declaration order.
fn decode_value<'a>(mut rest: &'a [u8]) -> Result<(SettingValueBuf, &'a [u8]), RunnerError> {
	macro_rules! take {
		($t:ty) => {{
			let (value, remainder) = postcard::take_from_bytes::<$t>(rest).map_err(RunnerError::from)?;
			rest = remainder;
			value
		}};
	}
	// recursive helper for Vec<Setting>
	fn items<'a>(rest: &'a [u8]) -> Result<(Vec<SettingBuf>, &'a [u8]), RunnerError> {
		let (count, rest) = postcard::take_from_bytes::<usize>(rest).map_err(RunnerError::from)?;
		let mut out = Vec::with_capacity(count);
		let mut rest = rest;
		for _ in 0..count {
			let (setting, remainder) = decode_one_setting(rest)?;
			rest = remainder;
			out.push(setting);
		}
		Ok((out, rest))
	}

	let idx: u32 = take!(u32);
	let value = match idx {
		0 => {
			let footer: Option<String> = take!(Option<String>);
			let (group_items, remainder) = items(rest)?;
			rest = remainder;
			SettingValueBuf::Group { footer, items: group_items }
		}
		1 => SettingValueBuf::Select {
			values: take!(Vec<String>),
			titles: take!(Option<Vec<String>>),
			auth_to_open: take!(Option<bool>),
			default: take!(Option<String>),
		},
		2 => SettingValueBuf::MultiSelect {
			values: take!(Vec<String>),
			titles: take!(Option<Vec<String>>),
			auth_to_open: take!(Option<bool>),
			default: take!(Option<Vec<String>>),
		},
		3 => SettingValueBuf::Toggle {
			subtitle: take!(Option<String>),
			auth_to_disable: take!(Option<bool>),
			default: take!(bool),
		},
		4 => SettingValueBuf::Stepper {
			minimum_value: take!(f64),
			maximum_value: take!(f64),
			step_value: take!(Option<f64>),
			default: take!(Option<f64>),
		},
		5 => SettingValueBuf::Segment { options: take!(Vec<String>), default: take!(Option<i32>) },
		6 => SettingValueBuf::Text {
			placeholder: take!(Option<String>),
			autocapitalization_type: take!(Option<i32>),
			autocorrection_disabled: take!(Option<bool>),
			keyboard_type: take!(Option<i32>),
			return_key_type: take!(Option<i32>),
			secure: take!(Option<bool>),
			default: take!(Option<String>),
		},
		7 => SettingValueBuf::Button,
		8 => SettingValueBuf::Link { url: take!(String), external: take!(Option<bool>) },
		9 => SettingValueBuf::Login {
			method: LoginMethodBuf(take!(String)),
			url: take!(Option<String>),
			url_key: take!(Option<String>),
			logout_title: take!(Option<String>),
			pkce: take!(bool),
			token_url: take!(Option<String>),
			callback_scheme: take!(Option<String>),
			use_email: take!(bool),
			local_storage_keys: take!(Option<Vec<String>>),
			clear_cookies_on_log_out: take!(bool),
		},
		10 => {
			let (page_items, remainder) = items(rest)?;
			rest = remainder;
			SettingValueBuf::Page {
				items: page_items,
				inline_title: take!(Option<bool>),
				auth_to_open: take!(Option<bool>),
				icon: {
					// Option<PageIconBuf>: [0x00 None | 0x01 Some][icon fields]
					let tag: u8 = take!(u8);
					if tag == 0 {
						None
					} else {
						let (icon, remainder) = decode_page_icon(rest)?;
						rest = remainder;
						Some(icon)
					}
				},
				info: take!(Option<String>),
			}
		}
		11 => SettingValueBuf::EditableList {
			line_limit: take!(Option<i32>),
			inline: take!(bool),
			placeholder: take!(Option<String>),
			default: take!(Option<Vec<String>>),
		},
		12 => SettingValueBuf::Picker {
			values: take!(Vec<String>),
			titles: take!(Option<Vec<String>>),
			default: take!(Option<String>),
		},
		other => return Err(RunnerError::serde(format!("unknown setting value kind: {other}"))),
	};
	Ok((value, rest))
}

/// Walks one `PageIcon` (with the Option tag already consumed by the caller).
fn decode_page_icon<'a>(mut rest: &'a [u8]) -> Result<(PageIconBuf, &'a [u8]), RunnerError> {
	macro_rules! take {
		($t:ty) => {{
			let (value, remainder) = postcard::take_from_bytes::<$t>(rest).map_err(RunnerError::from)?;
			rest = remainder;
			value
		}};
	}
	let ty: String = take!(String);
	let icon = match ty.as_str() {
		"system" => PageIconBuf::System {
			name: take!(String),
			color: take!(String),
			inset: take!(Option<i32>),
		},
		"url" => PageIconBuf::Url(take!(String)),
		other => return Err(RunnerError::serde(format!("unknown page icon type: {other}"))),
	};
	Ok((icon, rest))
}

impl SettingBuf {
	pub fn into_komorei(self) -> komorei::Setting {
		komorei::Setting {
			key: Cow::Owned(self.key),
			title: Cow::Owned(self.title),
			notification: self.notification.map(Cow::Owned),
			requires: self.requires.map(Cow::Owned),
			requires_false: self.requires_false.map(Cow::Owned),
			refreshes: self.refreshes.map(|v| v.into_iter().map(Cow::Owned).collect()),
			value: self.value.into_komorei(),
		}
	}
}

impl SettingValueBuf {
	pub fn into_komorei(self) -> komorei::SettingValue {
		use komorei::SettingValue;
		match self {
			SettingValueBuf::Group { footer, items } => SettingValue::Group {
				footer: footer.map(Cow::Owned),
				items: items.into_iter().map(SettingBuf::into_komorei).collect(),
			},
			SettingValueBuf::Select { values, titles, auth_to_open, default } => SettingValue::Select {
				values: values.into_iter().map(Cow::Owned).collect(),
				titles: titles.map(|v| v.into_iter().map(Cow::Owned).collect()),
				auth_to_open,
				default,
			},
			SettingValueBuf::MultiSelect { values, titles, auth_to_open, default } => SettingValue::MultiSelect {
				values: values.into_iter().map(Cow::Owned).collect(),
				titles: titles.map(|v| v.into_iter().map(Cow::Owned).collect()),
				auth_to_open,
				default,
			},
			SettingValueBuf::Toggle { subtitle, auth_to_disable, default } => SettingValue::Toggle {
				subtitle: subtitle.map(Cow::Owned),
				auth_to_disable,
				default,
			},
			SettingValueBuf::Stepper { minimum_value, maximum_value, step_value, default } => {
				SettingValue::Stepper { minimum_value, maximum_value, step_value, default }
			}
			SettingValueBuf::Segment { options, default } => SettingValue::Segment {
				options: options.into_iter().map(Cow::Owned).collect(),
				default,
			},
			SettingValueBuf::Text {
				placeholder,
				autocapitalization_type,
				autocorrection_disabled,
				keyboard_type,
				return_key_type,
				secure,
				default,
			} => SettingValue::Text {
				placeholder: placeholder.map(Cow::Owned),
				autocapitalization_type,
				autocorrection_disabled,
				keyboard_type,
				return_key_type,
				secure,
				default: default.map(Cow::Owned),
			},
			SettingValueBuf::Button => SettingValue::Button,
			SettingValueBuf::Link { url, external } => SettingValue::Link {
				url: Cow::Owned(url),
				external,
			},
			SettingValueBuf::Login {
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
				method: method.into_komorei(),
				url: url.map(Cow::Owned),
				url_key: url_key.map(Cow::Owned),
				logout_title: logout_title.map(Cow::Owned),
				pkce,
				token_url: token_url.map(Cow::Owned),
				callback_scheme: callback_scheme.map(Cow::Owned),
				use_email,
				local_storage_keys,
				clear_cookies_on_log_out,
			},
			SettingValueBuf::Page { items, inline_title, auth_to_open, icon, info } => SettingValue::Page {
				items: items.into_iter().map(SettingBuf::into_komorei).collect(),
				inline_title,
				auth_to_open,
				icon: icon.map(|i| i.into_komorei()),
				info,
			},
			SettingValueBuf::EditableList { line_limit, inline, placeholder, default } => {
				SettingValue::EditableList {
					line_limit,
					inline,
					placeholder: placeholder.map(Cow::Owned),
					default: default.map(|v| v.into_iter().map(Cow::Owned).collect()),
				}
			}
			SettingValueBuf::Picker { values, titles, default } => SettingValue::Picker {
				values: values.into_iter().map(Cow::Owned).collect(),
				titles: titles.map(|v| v.into_iter().map(Cow::Owned).collect()),
				default,
			},
		}
	}
}

impl PageIconBuf {
	pub fn into_komorei(self) -> komorei::PageIcon {
		match self {
			PageIconBuf::System { name, color, inset } => komorei::PageIcon::System { name, color, inset },
			PageIconBuf::Url(url) => komorei::PageIcon::Url(url),
		}
	}
}