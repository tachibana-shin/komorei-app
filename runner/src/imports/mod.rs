//! Registration of all wasm imports (the "linker table") for the 7 host
//! modules: env, std, net, html, defaults, canvas, js.
//!
//! The wasm module only imports what it actually uses; extra entries are
//! harmless but registering the full table means *any* `.krx` instantiates.
//!
//! All modules are implemented for real except `canvas` (bitmap drawing),
//! which stays a stub returning -1.

use wasmi::Linker;

use crate::state::RunnerData;

pub mod canvas;
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
}

/// Common error code returned by the remaining unimplemented module stub
/// (`canvas` / `canvas::*`).
pub(crate) fn stub_code() -> i32 {
	-1
}