/// Errors surfaced by the Komorei runner.
///
/// These are mapped to Kotlin exceptions by the uniffi bindings.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum RunnerError {
	/// The source `start()` export was called before the module finished loading.
	#[error("source module is not loaded")]
	NotLoaded,
	/// A required export is missing from the wasm module.
	#[error("missing wasm export: {name}")]
	ExportMissing { name: String },
	/// The wasm module failed to instantiate or a call trapped.
	#[error("wasm error: {0}")]
	Wasm(String),
	/// Postcard failed to decode a result or encode an argument.
	#[error("serialization error: {0}")]
	Serde(String),
	/// A result buffer layout was invalid.
	#[error("invalid result buffer")]
	InvalidResult,
	/// The source returned an error. `code` is the raw ABI error code
	/// (negative; -1 with a message payload carries the source message).
	#[error("source error ({code}): {message}")]
	Source { code: i32, message: String },
}

impl RunnerError {
	pub fn wasm(err: impl core::fmt::Display) -> Self {
		Self::Wasm(err.to_string())
	}

	pub fn serde(err: impl core::fmt::Display) -> Self {
		Self::Serde(err.to_string())
	}
}

impl From<postcard::Error> for RunnerError {
	fn from(e: postcard::Error) -> Self {
		Self::Serde(e.to_string())
	}
}

impl From<wasmi::Error> for RunnerError {
	fn from(e: wasmi::Error) -> Self {
		Self::Wasm(e.to_string())
	}
}

impl From<wasmi::errors::LinkerError> for RunnerError {
	fn from(e: wasmi::errors::LinkerError) -> Self {
		Self::Wasm(e.to_string())
	}
}