//! `env` module: logs, sleep, partial results.

use wasmi::{Caller, Linker};

use crate::{abi, state::RunnerData};

pub fn register(linker: &mut Linker<RunnerData>) {
	linker.func_wrap("env", "abort", abort).unwrap();
	linker.func_wrap("env", "print", print).unwrap();
	linker.func_wrap("env", "sleep", sleep).unwrap();
	linker
		.func_wrap("env", "send_partial_result", send_partial_result)
		.unwrap();
}

/// Called when the source panics / aborts.
fn abort(caller: Caller<'_, RunnerData>) {
	// Logging is best-effort: a host that cannot take the message must not
	// turn an abort into a second failure.
	let _ = caller.data().host.log_abort();
}

/// `println!` from the source.
fn print(caller: Caller<'_, RunnerData>, ptr: u32, len: u32) {
	let message = abi::read_string(&caller, ptr, len).unwrap_or_default();
	let _ = caller.data().host.log_print(message);
}

fn sleep(_caller: Caller<'_, RunnerData>, seconds: i32) {
	std::thread::sleep(std::time::Duration::from_secs(seconds.max(0) as u64));
}

/// `send_partial_result` — a source streaming its home layout out of
/// `get_home`.
///
/// The wasm hands over a pointer to the same `[len][cap][postcard]` buffer the
/// return-value path produces, so the payload is read out of linear memory
/// directly. It is deliberately **not** freed here: the SDK's
/// `imports::std::send_partial_result` calls `free_result` itself once this
/// returns.
///
/// A payload that does not decode is dropped with a log rather than raised: the
/// source is mid-`get_home` and a malformed chunk must not take the whole home
/// down with it.
fn send_partial_result(caller: Caller<'_, RunnerData>, ptr: i32) {
	if ptr <= 0 {
		return;
	}
	let base = ptr as u32;
	let Some(header) = abi::read_bytes(&caller, base, 8) else {
		return;
	};
	let len = i32::from_le_bytes(header[0..4].try_into().unwrap_or_default());
	// A negative length is the message-error form the return path uses; a
	// partial result is never an error, so there is nothing to report.
	if len < 8 {
		return;
	}
	let Some(payload) = abi::read_bytes(&caller, base + 8, (len - 8) as u32) else {
		return;
	};
	match postcard::from_bytes::<komorei::HomePartialResult>(&payload) {
		Ok(value) => {
			let _ = caller.data().host.partial_home(value.into());
		}
		Err(err) => {
			let _ = caller
				.data()
				.host
				.log_print(format!("[runner] partial home result rejected: {err}"));
		}
	}
}
