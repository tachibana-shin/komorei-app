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
	/// A `KomoreiHost` callback broke the FFI contract — uniffi cannot lift the
	/// value the foreign side handed back (observed as a zero-length
	/// out-buffer). Because the trait declares `Result` everywhere, uniffi
	/// converts this into an error instead of panicking under the
	/// `extern "C"` callback frame, so the import reports its normal failure
	/// code and the host process survives.
	#[error("host callback failed: {0}")]
	HostCallback(String),
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

/// uniffi calls this instead of panicking when a foreign (Kotlin) host method
/// hands back a value it cannot lift — the only thing standing between a
/// misbehaving callback and an aborted host process. See the note on
/// `KomoreiHost` for why the trait declares `Result` everywhere.
impl From<uniffi::UnexpectedUniFFICallbackError> for RunnerError {
	fn from(e: uniffi::UnexpectedUniFFICallbackError) -> Self {
		Self::HostCallback(e.reason)
	}
}
