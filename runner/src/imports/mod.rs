//! Registration of all wasm imports (the "linker table") for the 9 host
//! modules: env, std, net, html, defaults, canvas, js, base64, crypto.
//!
//! The wasm module only imports what it actually uses; extra entries are
//! harmless but registering the full table means *any* `.krx` instantiates.
//!
//! All modules are implemented for real except `canvas` (bitmap drawing),
//! which stays a stub returning -1. `base64` and `crypto` are pure-CPU
//! helpers computed natively in the runner (no Kotlin host round trip).

use wasmi::Linker;

use crate::state::RunnerData;

pub mod base64;
pub mod canvas;
pub mod crypto;
pub mod defaults;
pub mod env;
pub mod html;
pub mod js;
pub mod net;
pub mod std;

pub fn register(linker: &mut Linker<RunnerData>) {
	env::register(linker);
	std::register(linker);
	net::register(linker);
	html::register(linker);
	defaults::register(linker);
	canvas::register(linker);
	js::register(linker);
	base64::register(linker);
	crypto::register(linker);
}

/// Common error code returned by the remaining unimplemented module stub
/// (`canvas` / `canvas::*`).
pub(crate) fn stub_code() -> i32 {
	-1
}